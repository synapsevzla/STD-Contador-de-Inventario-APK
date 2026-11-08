package com.std.inventario.data.model

data class DatabaseConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val alias: String = "",
    val server: String = "",
    val port: String = "1433",
    val instance: String = "",
    val database: String = "",
    val user: String = "",
    val pass: String = "",
    val license: LicenseInfo? = null
)

data class Usuario(
    val codUsua: String,
    val descrip: String,
    val email: String,
    val clave: String,
    val nivel: String,
    val codVend: String
)

data class OperacionConteo(
    val numeroD: String,
    val codUbic: String,
    val deposito: String,
    val sucursal: String,
    val totalItems: Int,
    val titulo: String
)

data class ItemConteo(
    val codUbic: String,
    val deposito: String,
    val sucursal: String,
    val titulo: String,
    val numeroD: String,
    val codItem: String,
    val nroLinea: Int,
    val descripcion: String,
    val unidad: String,
    val exDecimal: Boolean,
    val existenciaContada: Double,
    val existenciaActual: Double,
    val diferencia: Double,
    val codUsuaConteo: String?
)

data class ConteoStats(
    val totalItems: Int = 0,
    val conDiferencia: Int = 0,
    val cuadrados: Int = 0
)

data class LicenseInfo(
    val valid: Boolean = false,
    val expired: Boolean = false,
    val error: String? = null,
    val serial: String = "",
    val razonSocial: String = "",
    val cliente: String = "",
    val canalDistribucion: String = "",
    val fechaVencimiento: String = "",
    val diasRestantes: Int = 0,
    val advertenciaProxima: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
