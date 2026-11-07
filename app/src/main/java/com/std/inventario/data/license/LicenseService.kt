package com.std.inventario.data.license

import com.std.inventario.data.db.DatabaseManager
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.LicenseInfo
import com.std.inventario.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object LicenseService {

    private const val FIREBASE_URL = "https://jhruiz-default-rtdb.asia-southeast1.firebasedatabase.app/Licencias_STD/"
    private const val CACHE_TTL_MS = 3600_000L // 1 hora de caché local
    private val TIME_ZONE = TimeZone.getTimeZone("America/Caracas")

    /**
     * Consulta el NroSerial y la Razón Social en la tabla SACONF de SQL Server.
     */
    suspend fun getSaconfData(config: DatabaseConfig): Pair<String, String> = withContext(Dispatchers.IO) {
        val queries = listOf(
            "SELECT TOP 1 NroSerial, Descrip FROM SACONF WHERE CodSucu = '00000' OR CodSucu IS NULL ORDER BY CASE WHEN CodSucu = '00000' THEN 0 ELSE 1 END",
            "SELECT TOP 1 NroSeria AS NroSerial, Descrip FROM SACONF WHERE CodSucu = '00000' OR CodSucu IS NULL ORDER BY CASE WHEN CodSucu = '00000' THEN 0 ELSE 1 END"
        )

        var serial = ""
        var razonSocial = ""

        DatabaseManager.getConnection(config).use { conn ->
            for (query in queries) {
                try {
                    conn.createStatement().use { stmt ->
                        stmt.executeQuery(query).use { rs ->
                            if (rs.next()) {
                                serial = rs.getString("NroSerial")?.trim() ?: ""
                                razonSocial = rs.getString("Descrip")?.trim() ?: ""
                                return@withContext Pair(serial, razonSocial)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Si falla la primera consulta (ej. columna NroSerial no existe), intentar con la siguiente
                }
            }
        }

        Pair(serial, razonSocial)
    }

    /**
     * Valida integralmente la licencia del sistema contra SACONF y Firebase RTDB.
     */
    suspend fun validate(
        config: DatabaseConfig,
        prefs: PreferencesManager,
        forceOnline: Boolean = false
    ): LicenseInfo = withContext(Dispatchers.IO) {
        val (serialERP, razonSocialERP) = try {
            getSaconfData(config)
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
            return@withContext LicenseInfo(
                valid = false,
                expired = false,
                error = "Error al consultar configuración SACONF: $msg"
            )
        }

        if (serialERP.isBlank()) {
            return@withContext LicenseInfo(
                valid = false,
                expired = false,
                error = "No se encontró el NroSerial en la tabla SACONF del sistema administrativo.",
                serial = serialERP,
                razonSocial = razonSocialERP
            )
        }

        // Verificar si existe caché local vigente
        if (!forceOnline) {
            val cached = prefs.getLicenseInfo()
            if (cached != null && cached.serial.equals(serialERP, ignoreCase = true)) {
                val now = System.currentTimeMillis()
                if (now - cached.timestamp < CACHE_TTL_MS && cached.valid && !cached.expired) {
                    // Recalcular días restantes con la fecha actual
                    val remaining = calculateDaysRemaining(cached.fechaVencimiento)
                    if (remaining >= 0) {
                        val refreshed = cached.copy(
                            diasRestantes = remaining,
                            advertenciaProxima = (remaining <= 15)
                        )
                        prefs.saveLicenseInfo(refreshed)
                        return@withContext refreshed
                    }
                }
            }
        }

        // Consultar Firebase Realtime Database
        val encodedSerial = URLEncoder.encode(serialERP, "UTF-8")
        val cloudUrl = "$FIREBASE_URL$encodedSerial.json"

        var responseBody: String? = null
        try {
            val url = URL(cloudUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (STD Inventory; Android)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    responseBody = reader.readText().trim()
                }
            }
            connection.disconnect()
        } catch (t: Throwable) {
            // Si la consulta en vivo falla por red pero tenemos caché previa para este serial, usarla
            val cached = prefs.getLicenseInfo()
            if (cached != null && cached.serial.equals(serialERP, ignoreCase = true)) {
                val remaining = calculateDaysRemaining(cached.fechaVencimiento)
                val expired = remaining < 0
                return@withContext cached.copy(
                    valid = !expired,
                    expired = expired,
                    diasRestantes = if (expired) 0 else remaining,
                    advertenciaProxima = (!expired && remaining <= 15)
                )
            }
            val msg = t.localizedMessage ?: t.message ?: "Error de red"
            return@withContext LicenseInfo(
                valid = false,
                expired = false,
                error = "No se pudo conectar al servidor de licencias en la nube: $msg",
                serial = serialERP,
                razonSocial = razonSocialERP
            )
        }

        if (responseBody.isNullOrBlank() || responseBody == "null") {
            return@withContext LicenseInfo(
                valid = false,
                expired = false,
                error = "La licencia no está registrada en el servidor central o el serial no coincide.",
                serial = serialERP,
                razonSocial = razonSocialERP
            )
        }

        return@withContext try {
            val json = JSONObject(responseBody)
            val fechaVencStr = json.optString("fechavencimiento").trim()
            val cliente = json.optString("cliente").trim()
            val canal = json.optString("canaldedistribucion").trim()

            if (fechaVencStr.isBlank()) {
                LicenseInfo(
                    valid = false,
                    expired = false,
                    error = "Los datos de la licencia no contienen una fecha de vencimiento válida.",
                    serial = serialERP,
                    razonSocial = razonSocialERP
                )
            } else {
                val diasRestantes = calculateDaysRemaining(fechaVencStr)
                val expired = diasRestantes < 0
                val valid = !expired

                val result = LicenseInfo(
                    valid = valid,
                    expired = expired,
                    error = if (expired) "Su licencia se encuentra vencida. Por favor contacte a su canal de integración." else null,
                    serial = serialERP,
                    razonSocial = if (razonSocialERP.isNotBlank()) razonSocialERP else cliente,
                    cliente = cliente,
                    canalDistribucion = canal,
                    fechaVencimiento = fechaVencStr,
                    diasRestantes = if (expired) 0 else diasRestantes,
                    advertenciaProxima = (valid && diasRestantes <= 15),
                    timestamp = System.currentTimeMillis()
                )

                // Guardar en caché local
                prefs.saveLicenseInfo(result)
                result
            }
        } catch (e: Exception) {
            LicenseInfo(
                valid = false,
                expired = false,
                error = "Error al procesar la respuesta del servidor de licencias: ${e.message}",
                serial = serialERP,
                razonSocial = razonSocialERP
            )
        }
    }

    /**
     * Calcula los días restantes hasta la fecha de vencimiento a las 23:59:59 (America/Caracas).
     */
    private fun calculateDaysRemaining(fechaVencStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TIME_ZONE
            }
            val vencDate = sdf.parse(fechaVencStr) ?: return -1

            val calVenc = Calendar.getInstance(TIME_ZONE).apply {
                time = vencDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val calNow = Calendar.getInstance(TIME_ZONE)

            val diffMillis = calVenc.timeInMillis - calNow.timeInMillis
            if (diffMillis < 0) {
                -1
            } else {
                TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
            }
        } catch (_: Exception) {
            -1
        }
    }
}
