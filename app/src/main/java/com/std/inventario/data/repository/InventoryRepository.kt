package com.std.inventario.data.repository

import com.std.inventario.data.db.DatabaseManager
import com.std.inventario.data.model.ConteoStats
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.ItemConteo
import com.std.inventario.data.model.OperacionConteo
import com.std.inventario.data.model.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InventoryRepository(private val config: DatabaseConfig) {

    // 1. Autenticación de Usuario contra ssusrs
    suspend fun login(codUsua: String, email: String): Usuario? = withContext(Dispatchers.IO) {
        val query = """
            SELECT codusUa, descrip, email,
               RTRIM(LTRIM(SUBSTRING(SDATA3,175,1)+
               SUBSTRING(SDATA1,33,1)+
               SUBSTRING(SDATA2,90,1)+
               SUBSTRING(SDATA3,14,1)+
               SUBSTRING(SDATA1,207,1)+
               SUBSTRING(SDATA3,111,1)+
               SUBSTRING(SDATA3,145,1)+
               SUBSTRING(SDATA2,180,1)+
               SUBSTRING(SDATA2,9,1)+
               SUBSTRING(SDATA3,53,1))) AS Clave,
               RTRIM(LTRIM(SUBSTRING(SDATA2,193,1)+
               SUBSTRING(SDATA1,77,1))) AS Nivel,
               ISNULL(CodVend,'') AS CodVend 
            FROM ssusrs
            WHERE CodUsua = ? AND Email = ?
        """.trimIndent()

        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, codUsua)
                stmt.setString(2, email)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        Usuario(
                            codUsua = rs.getString("codusUa").trim(),
                            descrip = rs.getString("descrip")?.trim() ?: "",
                            email = rs.getString("email")?.trim() ?: "",
                            clave = rs.getString("Clave")?.trim() ?: "",
                            nivel = rs.getString("Nivel")?.trim() ?: "",
                            codVend = rs.getString("CodVend")?.trim() ?: ""
                        )
                    } else null
                }
            }
        }
    }

    // 2. Listado de Operaciones de Conteo Abiertas
    suspend fun getOperaciones(): List<OperacionConteo> = withContext(Dispatchers.IO) {
        val query = """
            SELECT 
                t1.NumeroD,
                t1.CodUbic,
                t4.Descrip AS Deposito,
                LTRIM(RTRIM(CONCAT(t4.Direc1, ' ', t4.Direc2))) AS Sucursal,
                COUNT(t2.CodItem) AS TotalItems,
                'Conteo en Espera' AS Titulo
            FROM SAOPEI t1
            INNER JOIN SADEPO t4 ON t4.CodUbic = t1.CodUbic
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            WHERE t1.TipoOpI = 'r'
            GROUP BY t1.NumeroD, t1.CodUbic, t4.Descrip, t4.Direc1, t4.Direc2
        """.trimIndent()

        val list = mutableListOf<OperacionConteo>()
        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            OperacionConteo(
                                numeroD = rs.getString("NumeroD").trim(),
                                codUbic = rs.getString("CodUbic").trim(),
                                deposito = rs.getString("Deposito")?.trim() ?: "",
                                sucursal = rs.getString("Sucursal")?.trim() ?: "",
                                totalItems = rs.getInt("TotalItems"),
                                titulo = rs.getString("Titulo") ?: "Conteo en Espera"
                            )
                        )
                    }
                }
            }
        }
        list
    }

    // 3. Detalle de Items para Conteo por Operación
    suspend fun getItemsOperacion(numeroD: String): List<ItemConteo> = withContext(Dispatchers.IO) {
        val query = """
            SELECT 
                t1.CodUbic,
                ISNULL(t4.Descrip, '') AS Deposito,
                LTRIM(RTRIM(CONCAT(ISNULL(t4.Direc1,''), ' ', ISNULL(t4.Direc2,'')))) AS Sucursal,
                'Conteo en Espera' AS Titulo,
                t1.NumeroD,
                t2.CodItem,
                t2.NroLinea,
                ISNULL(NULLIF(LTRIM(RTRIM(CONCAT(ISNULL(t2.Descrip1,''), ' ', ISNULL(t2.Descrip2,''), ' ', ISNULL(t2.Descrip3,'')))), ''), ISNULL(t5.Descrip, t2.CodItem)) AS Descripcion,
                ISNULL(IIF(t2.EsUnid = 0, t5.Unidad, t5.UndEmpaq), 'UND') AS Unidad,
                ISNULL(t5.ExDecimal, 0) AS ExDecimal,
                ISNULL(t2.Cantidad, 0) AS Existencia_Contada,
                ISNULL(t3.Existen, 0) AS Existencia_Actual_En_Deposito,
                (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) AS Diferencia,
                t2.CodUsua_Conteo
            FROM SAOPEI t1
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            LEFT JOIN SAEXIS t3 ON RTRIM(LTRIM(t3.CodProd)) = RTRIM(LTRIM(t2.CodItem)) AND RTRIM(LTRIM(t3.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SADEPO t4 ON RTRIM(LTRIM(t4.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SAPROD t5 ON RTRIM(LTRIM(t5.CodProd)) = RTRIM(LTRIM(t2.CodItem))
            WHERE t1.TipoOpI = 'r' AND t1.NumeroD = ?
            ORDER BY t2.NroLinea
        """.trimIndent()

        val list = mutableListOf<ItemConteo>()
        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, numeroD)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(mapItemConteo(rs))
                    }
                }
            }
        }
        list
    }

    // 3.1 Estadísticas resumidas de la operación (conteo total, con diferencia y cuadrados)
    suspend fun getConteoStats(numeroD: String): ConteoStats = withContext(Dispatchers.IO) {
        val query = """
            SELECT 
                COUNT(t2.CodItem) AS TotalItems,
                ISNULL(SUM(CASE WHEN (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) != 0 THEN 1 ELSE 0 END), 0) AS ConDiferencia,
                ISNULL(SUM(CASE WHEN (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) = 0 THEN 1 ELSE 0 END), 0) AS Cuadrados
            FROM SAOPEI t1
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            LEFT JOIN SAEXIS t3 ON RTRIM(LTRIM(t3.CodProd)) = RTRIM(LTRIM(t2.CodItem)) AND RTRIM(LTRIM(t3.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            WHERE t1.TipoOpI = 'r' AND t1.NumeroD = ?
        """.trimIndent()

        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, numeroD)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        ConteoStats(
                            totalItems = rs.getInt("TotalItems"),
                            conDiferencia = rs.getInt("ConDiferencia"),
                            cuadrados = rs.getInt("Cuadrados")
                        )
                    } else ConteoStats()
                }
            }
        }
    }

    // 3.2 Listado Paginado con Lazy Loading (Soporta 80k+ items eficientemente)
    suspend fun getItemsOperacionPaginado(
        numeroD: String,
        offset: Int,
        limit: Int = 50,
        searchQuery: String = "",
        filterMode: String = "TODOS"
    ): List<ItemConteo> = withContext(Dispatchers.IO) {
        val queryBuilder = StringBuilder("""
            SELECT 
                t1.CodUbic,
                ISNULL(t4.Descrip, '') AS Deposito,
                LTRIM(RTRIM(CONCAT(ISNULL(t4.Direc1,''), ' ', ISNULL(t4.Direc2,'')))) AS Sucursal,
                'Conteo en Espera' AS Titulo,
                t1.NumeroD,
                t2.CodItem,
                t2.NroLinea,
                ISNULL(NULLIF(LTRIM(RTRIM(CONCAT(ISNULL(t2.Descrip1,''), ' ', ISNULL(t2.Descrip2,''), ' ', ISNULL(t2.Descrip3,'')))), ''), ISNULL(t5.Descrip, t2.CodItem)) AS Descripcion,
                ISNULL(IIF(t2.EsUnid = 0, t5.Unidad, t5.UndEmpaq), 'UND') AS Unidad,
                ISNULL(t5.ExDecimal, 0) AS ExDecimal,
                ISNULL(t2.Cantidad, 0) AS Existencia_Contada,
                ISNULL(t3.Existen, 0) AS Existencia_Actual_En_Deposito,
                (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) AS Diferencia,
                t2.CodUsua_Conteo
            FROM SAOPEI t1
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            LEFT JOIN SAEXIS t3 ON RTRIM(LTRIM(t3.CodProd)) = RTRIM(LTRIM(t2.CodItem)) AND RTRIM(LTRIM(t3.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SADEPO t4 ON RTRIM(LTRIM(t4.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SAPROD t5 ON RTRIM(LTRIM(t5.CodProd)) = RTRIM(LTRIM(t2.CodItem))
            WHERE t1.TipoOpI = 'r' AND t1.NumeroD = ?
        """.trimIndent())

        val trimmedSearch = searchQuery.trim()
        val hasSearch = trimmedSearch.isNotBlank()
        if (hasSearch) {
            queryBuilder.append(" AND (t2.CodItem LIKE ? OR t2.Descrip1 LIKE ? OR t2.Descrip2 LIKE ? OR t2.Descrip3 LIKE ?)")
        }

        when (filterMode) {
            "CON_DIFERENCIA" -> queryBuilder.append(" AND (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) != 0")
            "CUADRADOS" -> queryBuilder.append(" AND (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) = 0")
        }

        queryBuilder.append(" ORDER BY t2.NroLinea OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")

        val list = mutableListOf<ItemConteo>()
        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(queryBuilder.toString()).use { stmt ->
                var paramIdx = 1
                stmt.setString(paramIdx++, numeroD)
                if (hasSearch) {
                    val pattern = "%$trimmedSearch%"
                    stmt.setString(paramIdx++, pattern)
                    stmt.setString(paramIdx++, pattern)
                    stmt.setString(paramIdx++, pattern)
                    stmt.setString(paramIdx++, pattern)
                }
                stmt.setInt(paramIdx++, offset)
                stmt.setInt(paramIdx, limit)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(mapItemConteo(rs))
                    }
                }
            }
        }
        list
    }

    // 3.3 Búsqueda directa por código de producto o código de barra alternativo (SACODBAR)
    suspend fun findItemByCodeOrBarcode(numeroD: String, code: String): ItemConteo? = withContext(Dispatchers.IO) {
        val query = """
            SELECT TOP 1
                t1.CodUbic,
                ISNULL(t4.Descrip, '') AS Deposito,
                LTRIM(RTRIM(CONCAT(ISNULL(t4.Direc1,''), ' ', ISNULL(t4.Direc2,'')))) AS Sucursal,
                'Conteo en Espera' AS Titulo,
                t1.NumeroD,
                t2.CodItem,
                t2.NroLinea,
                ISNULL(NULLIF(LTRIM(RTRIM(CONCAT(ISNULL(t2.Descrip1,''), ' ', ISNULL(t2.Descrip2,''), ' ', ISNULL(t2.Descrip3,'')))), ''), ISNULL(t5.Descrip, t2.CodItem)) AS Descripcion,
                ISNULL(IIF(t2.EsUnid = 0, t5.Unidad, t5.UndEmpaq), 'UND') AS Unidad,
                ISNULL(t5.ExDecimal, 0) AS ExDecimal,
                ISNULL(t2.Cantidad, 0) AS Existencia_Contada,
                ISNULL(t3.Existen, 0) AS Existencia_Actual_En_Deposito,
                (ISNULL(t2.Cantidad, 0) - ISNULL(t3.Existen, 0)) AS Diferencia,
                t2.CodUsua_Conteo
            FROM SAOPEI t1
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            LEFT JOIN SAEXIS t3 ON RTRIM(LTRIM(t3.CodProd)) = RTRIM(LTRIM(t2.CodItem)) AND RTRIM(LTRIM(t3.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SADEPO t4 ON RTRIM(LTRIM(t4.CodUbic)) = RTRIM(LTRIM(t1.CodUbic))
            LEFT JOIN SAPROD t5 ON RTRIM(LTRIM(t5.CodProd)) = RTRIM(LTRIM(t2.CodItem))
            LEFT JOIN SACODBAR cb ON RTRIM(LTRIM(cb.CodProd)) = RTRIM(LTRIM(t2.CodItem))
            WHERE t1.TipoOpI = 'r' 
              AND t1.NumeroD = ?
              AND (RTRIM(LTRIM(t2.CodItem)) = ? OR RTRIM(LTRIM(cb.CodAlte)) = ?)
        """.trimIndent()

        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                val cleanCode = code.trim()
                stmt.setString(1, numeroD)
                stmt.setString(2, cleanCode)
                stmt.setString(3, cleanCode)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapItemConteo(rs)
                    } else null
                }
            }
        }
    }

    private fun mapItemConteo(rs: java.sql.ResultSet): ItemConteo {
        return ItemConteo(
            codUbic = rs.getString("CodUbic")?.trim() ?: "",
            deposito = rs.getString("Deposito")?.trim() ?: "",
            sucursal = rs.getString("Sucursal")?.trim() ?: "",
            titulo = rs.getString("Titulo") ?: "",
            numeroD = rs.getString("NumeroD").trim(),
            codItem = rs.getString("CodItem").trim(),
            nroLinea = rs.getInt("NroLinea"),
            descripcion = rs.getString("Descripcion")?.trim() ?: "",
            unidad = rs.getString("Unidad")?.trim() ?: "N/A",
            exDecimal = rs.getInt("ExDecimal") == 1,
            existenciaContada = rs.getDouble("Existencia_Contada"),
            existenciaActual = rs.getDouble("Existencia_Actual_En_Deposito"),
            diferencia = rs.getDouble("Diferencia"),
            codUsuaConteo = rs.getString("CodUsua_Conteo")?.trim()
        )
    }

    // 4. Actualización del Conteo
    suspend fun updateCantidadConteo(
        numeroD: String,
        codItem: String,
        cantidad: Double,
        codUsua: String
    ): Boolean = withContext(Dispatchers.IO) {
        val query = """
            UPDATE SAITEMOPI
            SET Cantidad = ?,
                CodUsua_Conteo = ?
            WHERE TipoOpI = 'r' AND NumeroD = ? AND CodItem = ?
        """.trimIndent()

        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setDouble(1, cantidad)
                stmt.setString(2, codUsua)
                stmt.setString(3, numeroD)
                stmt.setString(4, codItem)
                stmt.executeUpdate() > 0
            }
        }
    }

    // 5. Eliminar artículos no contados (Cantidad = 0 o NULL) de la operación
    suspend fun cleanUncountedItems(numeroD: String): Int = withContext(Dispatchers.IO) {
        val query = """
            DELETE FROM SAITEMOPI
            WHERE TipoOpI = 'r'
              AND NumeroD = ?
              AND (Cantidad = 0 OR Cantidad IS NULL)
        """.trimIndent()

        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, numeroD)
                stmt.executeUpdate()
            }
        }
    }

    // 6. Eliminar por completo una operación de conteo y todos sus items asociados
    suspend fun deleteOperacionCompleta(numeroD: String): Boolean = withContext(Dispatchers.IO) {
        val deleteItemsQuery = "DELETE FROM SAITEMOPI WHERE TipoOpI = 'r' AND NumeroD = ?"
        val deleteHeaderQuery = "DELETE FROM SAOPEI WHERE TipoOpI = 'r' AND NumeroD = ?"

        DatabaseManager.getConnection(config).use { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.prepareStatement(deleteItemsQuery).use { stmtItems ->
                    stmtItems.setString(1, numeroD)
                    stmtItems.executeUpdate()
                }

                val headerDeleted = conn.prepareStatement(deleteHeaderQuery).use { stmtHeader ->
                    stmtHeader.setString(1, numeroD)
                    stmtHeader.executeUpdate() > 0
                }

                conn.commit()
                headerDeleted
            } catch (t: Throwable) {
                try {
                    conn.rollback()
                } catch (_: Throwable) {}
                throw t
            } finally {
                try {
                    conn.autoCommit = prevAutoCommit
                } catch (_: Throwable) {}
            }
        }
    }
}
