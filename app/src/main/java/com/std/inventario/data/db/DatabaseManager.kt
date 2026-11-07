package com.std.inventario.data.db

import com.std.inventario.data.model.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object DatabaseManager {
    init {
        try {
            DriverManager.registerDriver(net.sourceforge.jtds.jdbc.Driver())
        } catch (_: Throwable) {
            try {
                Class.forName("net.sourceforge.jtds.jdbc.Driver")
            } catch (_: Throwable) {}
        }
    }

    suspend fun getConnection(config: DatabaseConfig): Connection = withContext(Dispatchers.IO) {
        val server = config.server.trim()
        val database = config.database.trim()
        val user = config.user.trim()
        val pass = config.pass

        if (server.isBlank() || database.isBlank()) {
            throw SQLException("Servidor y Base de Datos no pueden estar vacíos")
        }

        val port = config.port.trim().ifBlank { "1433" }
        val host = "$server:$port"
        var url = "jdbc:jtds:sqlserver://$host/$database;useCursors=true;loginTimeout=10;socketTimeout=30"
        if (config.instance.isNotBlank()) {
            url += ";instance=${config.instance.trim()}"
        }

        DriverManager.getConnection(url, user, pass)
    }

    suspend fun testConnection(config: DatabaseConfig): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        try {
            conn = getConnection(config)
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT 1").use { rs ->
                    rs.next()
                }
            }
        } finally {
            try {
                conn?.close()
            } catch (_: Throwable) {}
        }
    }
}
