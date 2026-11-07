package com.std.inventario.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.LicenseInfo
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("std_inventario_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER = "db_server"
        private const val KEY_PORT = "db_port"
        private const val KEY_INSTANCE = "db_instance"
        private const val KEY_DATABASE = "db_database"
        private const val KEY_USER = "db_user"
        private const val KEY_PASS = "db_pass"
        private const val KEY_LOGGED_USER = "logged_user"
        private const val KEY_LOGGED_USER_NAME = "logged_user_name"
        private const val KEY_LICENSE_JSON = "license_json"
    }

    fun saveDatabaseConfig(config: DatabaseConfig) {
        prefs.edit().apply {
            putString(KEY_SERVER, config.server)
            putString(KEY_PORT, config.port)
            putString(KEY_INSTANCE, config.instance)
            putString(KEY_DATABASE, config.database)
            putString(KEY_USER, config.user)
            putString(KEY_PASS, config.pass)
            apply()
        }
    }

    fun getDatabaseConfig(): DatabaseConfig {
        return DatabaseConfig(
            server = prefs.getString(KEY_SERVER, "") ?: "",
            port = prefs.getString(KEY_PORT, "1433") ?: "1433",
            instance = prefs.getString(KEY_INSTANCE, "") ?: "",
            database = prefs.getString(KEY_DATABASE, "") ?: "",
            user = prefs.getString(KEY_USER, "") ?: "",
            pass = prefs.getString(KEY_PASS, "") ?: ""
        )
    }

    fun saveLoggedUser(codUsua: String, name: String) {
        prefs.edit().apply {
            putString(KEY_LOGGED_USER, codUsua)
            putString(KEY_LOGGED_USER_NAME, name)
            apply()
        }
    }

    fun getLoggedUser(): String? = prefs.getString(KEY_LOGGED_USER, null)
    fun getLoggedUserName(): String? = prefs.getString(KEY_LOGGED_USER_NAME, null)

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_LOGGED_USER)
            remove(KEY_LOGGED_USER_NAME)
            apply()
        }
    }

    fun saveLicenseInfo(info: LicenseInfo) {
        try {
            val json = JSONObject().apply {
                put("valid", info.valid)
                put("expired", info.expired)
                put("error", info.error ?: "")
                put("serial", info.serial)
                put("razonSocial", info.razonSocial)
                put("cliente", info.cliente)
                put("canalDistribucion", info.canalDistribucion)
                put("fechaVencimiento", info.fechaVencimiento)
                put("diasRestantes", info.diasRestantes)
                put("advertenciaProxima", info.advertenciaProxima)
                put("timestamp", info.timestamp)
            }
            prefs.edit().putString(KEY_LICENSE_JSON, json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getLicenseInfo(): LicenseInfo? {
        val raw = prefs.getString(KEY_LICENSE_JSON, null) ?: return null
        return try {
            val json = JSONObject(raw)
            LicenseInfo(
                valid = json.optBoolean("valid", false),
                expired = json.optBoolean("expired", false),
                error = json.optString("error").takeIf { it.isNotBlank() },
                serial = json.optString("serial", ""),
                razonSocial = json.optString("razonSocial", ""),
                cliente = json.optString("cliente", ""),
                canalDistribucion = json.optString("canalDistribucion", ""),
                fechaVencimiento = json.optString("fechaVencimiento", ""),
                diasRestantes = json.optInt("diasRestantes", 0),
                advertenciaProxima = json.optBoolean("advertenciaProxima", false),
                timestamp = json.optLong("timestamp", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isLicenseValid(): Boolean {
        val license = getLicenseInfo() ?: return false
        return license.valid && !license.expired
    }

    fun clearLicenseInfo() {
        prefs.edit().remove(KEY_LICENSE_JSON).apply()
    }
}
