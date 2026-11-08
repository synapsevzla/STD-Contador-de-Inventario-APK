package com.std.inventario.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.LicenseInfo
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("std_inventario_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONNECTIONS_ARRAY = "connections_array_json"
        private const val KEY_ACTIVE_CONNECTION_ID = "active_connection_id"
        private const val KEY_LOGGED_USER = "logged_user"
        private const val KEY_LOGGED_USER_NAME = "logged_user_name"

        // Claves heredadas para retrocompatibilidad
        private const val KEY_SERVER = "db_server"
        private const val KEY_PORT = "db_port"
        private const val KEY_INSTANCE = "db_instance"
        private const val KEY_DATABASE = "db_database"
        private const val KEY_USER = "db_user"
        private const val KEY_PASS = "db_pass"
        private const val KEY_LICENSE_JSON = "license_json"
    }

    // ==========================================
    // GESTIÓN DE MÚLTIPLES CONEXIONES
    // ==========================================

    fun getAllConnections(): List<DatabaseConfig> {
        val raw = prefs.getString(KEY_CONNECTIONS_ARRAY, null)
        val list = mutableListOf<DatabaseConfig>()

        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(deserializeConfig(obj))
                }
            } catch (_: Exception) {}
        }

        // Migración transparente si existía una conexión única previa
        if (list.isEmpty()) {
            val legacyServer = prefs.getString(KEY_SERVER, "") ?: ""
            val legacyDb = prefs.getString(KEY_DATABASE, "") ?: ""
            if (legacyServer.isNotBlank() && legacyDb.isNotBlank()) {
                val legacyConfig = DatabaseConfig(
                    alias = "Conexión Principal",
                    server = legacyServer,
                    port = prefs.getString(KEY_PORT, "1433") ?: "1433",
                    instance = prefs.getString(KEY_INSTANCE, "") ?: "",
                    database = legacyDb,
                    user = prefs.getString(KEY_USER, "") ?: "",
                    pass = prefs.getString(KEY_PASS, "") ?: "",
                    license = getLicenseInfo()
                )
                list.add(legacyConfig)
                saveAllConnections(list)
                setActiveConnectionId(legacyConfig.id)
            }
        }

        return list
    }

    private fun saveAllConnections(connections: List<DatabaseConfig>) {
        val array = JSONArray()
        for (item in connections) {
            array.put(serializeConfig(item))
        }
        prefs.edit().putString(KEY_CONNECTIONS_ARRAY, array.toString()).apply()
    }

    fun saveConnection(config: DatabaseConfig) {
        val connections = getAllConnections().toMutableList()
        val index = connections.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            connections[index] = config
        } else {
            connections.add(config)
        }
        saveAllConnections(connections)

        // Si es la primera o la única, seleccionarla como activa
        val activeId = getActiveConnectionId()
        if (activeId.isBlank() || connections.none { it.id == activeId }) {
            setActiveConnectionId(config.id)
        } else if (activeId == config.id) {
            syncLegacyKeys(config)
        }
    }

    fun deleteConnection(id: String) {
        val connections = getAllConnections().toMutableList()
        connections.removeAll { it.id == id }
        saveAllConnections(connections)

        val activeId = getActiveConnectionId()
        if (activeId == id) {
            val next = connections.firstOrNull()
            if (next != null) {
                setActiveConnectionId(next.id)
            } else {
                prefs.edit().remove(KEY_ACTIVE_CONNECTION_ID).apply()
                clearLegacyDatabaseConfig()
                clearLicenseInfo()
                clearSession()
            }
        }
    }

    fun getActiveConnection(): DatabaseConfig {
        val connections = getAllConnections()
        if (connections.isEmpty()) return DatabaseConfig()

        val activeId = getActiveConnectionId()
        val found = connections.firstOrNull { it.id == activeId } ?: connections.first()
        syncLegacyKeys(found)
        return found
    }

    fun getActiveConnectionId(): String {
        return prefs.getString(KEY_ACTIVE_CONNECTION_ID, "") ?: ""
    }

    fun setActiveConnectionId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_CONNECTION_ID, id).apply()
        val found = getAllConnections().firstOrNull { it.id == id }
        if (found != null) {
            syncLegacyKeys(found)
            found.license?.let { saveLicenseInfo(it) }
        }
    }

    fun hasDatabaseConfig(): Boolean {
        return getAllConnections().isNotEmpty()
    }

    fun resetAllConnectionAndLicense() {
        prefs.edit().apply {
            remove(KEY_CONNECTIONS_ARRAY)
            remove(KEY_ACTIVE_CONNECTION_ID)
            remove(KEY_SERVER)
            remove(KEY_PORT)
            remove(KEY_INSTANCE)
            remove(KEY_DATABASE)
            remove(KEY_USER)
            remove(KEY_PASS)
            remove(KEY_LICENSE_JSON)
            remove(KEY_LOGGED_USER)
            remove(KEY_LOGGED_USER_NAME)
            apply()
        }
    }

    // ==========================================
    // COMPATIBILIDAD CON CÓDIGO EXISTENTE
    // ==========================================

    fun getDatabaseConfig(): DatabaseConfig {
        return getActiveConnection()
    }

    fun saveDatabaseConfig(config: DatabaseConfig) {
        saveConnection(config)
    }

    private fun clearLegacyDatabaseConfig() {
        prefs.edit().apply {
            remove(KEY_SERVER)
            remove(KEY_PORT)
            remove(KEY_INSTANCE)
            remove(KEY_DATABASE)
            remove(KEY_USER)
            remove(KEY_PASS)
            apply()
        }
    }

    private fun syncLegacyKeys(config: DatabaseConfig) {
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

    // ==========================================
    // SERIALIZACIÓN JSON DE CONEXIONES
    // ==========================================

    private fun serializeConfig(config: DatabaseConfig): JSONObject {
        val obj = JSONObject().apply {
            put("id", config.id)
            put("alias", config.alias)
            put("server", config.server)
            put("port", config.port)
            put("instance", config.instance)
            put("database", config.database)
            put("user", config.user)
            put("pass", config.pass)
        }
        config.license?.let { lic ->
            obj.put("license", JSONObject().apply {
                put("valid", lic.valid)
                put("expired", lic.expired)
                put("error", lic.error ?: "")
                put("serial", lic.serial)
                put("razonSocial", lic.razonSocial)
                put("cliente", lic.cliente)
                put("canalDistribucion", lic.canalDistribucion)
                put("fechaVencimiento", lic.fechaVencimiento)
                put("diasRestantes", lic.diasRestantes)
                put("advertenciaProxima", lic.advertenciaProxima)
                put("timestamp", lic.timestamp)
            })
        }
        return obj
    }

    private fun deserializeConfig(obj: JSONObject): DatabaseConfig {
        val licObj = obj.optJSONObject("license")
        val license = if (licObj != null) {
            LicenseInfo(
                valid = licObj.optBoolean("valid", false),
                expired = licObj.optBoolean("expired", false),
                error = licObj.optString("error").takeIf { it.isNotBlank() },
                serial = licObj.optString("serial", ""),
                razonSocial = licObj.optString("razonSocial", ""),
                cliente = licObj.optString("cliente", ""),
                canalDistribucion = licObj.optString("canalDistribucion", ""),
                fechaVencimiento = licObj.optString("fechaVencimiento", ""),
                diasRestantes = licObj.optInt("diasRestantes", 0),
                advertenciaProxima = licObj.optBoolean("advertenciaProxima", false),
                timestamp = licObj.optLong("timestamp", 0L)
            )
        } else null

        return DatabaseConfig(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            alias = obj.optString("alias", ""),
            server = obj.optString("server", ""),
            port = obj.optString("port", "1433"),
            instance = obj.optString("instance", ""),
            database = obj.optString("database", ""),
            user = obj.optString("user", ""),
            pass = obj.optString("pass", ""),
            license = license
        )
    }

    // ==========================================
    // SESIÓN Y LICENCIA GLOBAL
    // ==========================================

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
        val active = getActiveConnection()
        val lic = active.license ?: getLicenseInfo() ?: return false
        return lic.valid && !lic.expired
    }

    fun clearLicenseInfo() {
        prefs.edit().remove(KEY_LICENSE_JSON).apply()
    }
}
