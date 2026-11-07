package com.std.inventario.data.repository

import com.std.inventario.data.db.DatabaseManager
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.ItemConteo
import com.std.inventario.data.model.OperacionConteo
import com.std.inventario.data.model.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

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
                t4.Descrip AS Deposito,
                LTRIM(RTRIM(CONCAT(t4.Direc1, ' ', t4.Direc2))) AS Sucursal,
                'Conteo en Espera' AS Titulo,
                t1.NumeroD,
                t2.CodItem,
                t2.NroLinea,
                LTRIM(RTRIM(CONCAT(t2.Descrip1, ' ', t2.Descrip2, ' ', t2.Descrip3))) AS Descripcion,
                ISNULL(IIF(t2.EsUnid = 0, t5.Unidad, t5.UndEmpaq), 'N/A') AS Unidad,
                t5.ExDecimal,
                t2.Cantidad AS Existencia_Contada,
                ISNULL(t3.Existen, 0) AS Existencia_Actual_En_Deposito,
                (t2.Cantidad - ISNULL(t3.Existen, 0)) AS Diferencia,
                t2.CodUsua_Conteo
            FROM SAOPEI t1
            INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
            LEFT JOIN SAEXIS t3 ON t3.CodProd = t2.CodItem AND t3.CodUbic = t1.CodUbic
            INNER JOIN SADEPO t4 ON t4.CodUbic = t1.CodUbic
            INNER JOIN SAPROD t5 ON t2.CodItem = t5.CodProd
            WHERE t1.TipoOpI = 'r' AND t1.NumeroD = ?
            ORDER BY t2.NroLinea
        """.trimIndent()

        val list = mutableListOf<ItemConteo>()
        DatabaseManager.getConnection(config).use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, numeroD)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            ItemConteo(
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
                        )
                    }
                }
            }
        }
        list
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
}
