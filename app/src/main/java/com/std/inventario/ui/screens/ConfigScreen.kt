package com.std.inventario.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.std.inventario.data.db.DatabaseManager
import com.std.inventario.data.license.LicenseService
import com.std.inventario.data.model.DatabaseConfig
import com.std.inventario.data.model.LicenseInfo
import com.std.inventario.data.preferences.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    prefs: PreferencesManager,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var connections by remember { mutableStateOf(prefs.getAllConnections()) }
    var activeId by remember { mutableStateOf(prefs.getActiveConnectionId()) }

    // Controla si se muestra el formulario para añadir una nueva conexión
    var isAddingNew by remember { mutableStateOf(connections.isEmpty()) }

    // Formulario de nueva conexión
    var alias by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1433") }
    var instance by remember { mutableStateOf("") }
    var database by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var testedLicense by remember { mutableStateOf<LicenseInfo?>(null) }

    var connectionToDelete by remember { mutableStateOf<DatabaseConfig?>(null) }

    fun refreshList() {
        connections = prefs.getAllConnections()
        activeId = prefs.getActiveConnectionId()
        if (connections.isEmpty()) {
            isAddingNew = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddingNew) "Nueva Conexión" else "Conexiones Guardadas") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isAddingNew && connections.isNotEmpty()) {
                            isAddingNew = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    if (!isAddingNew) {
                        IconButton(onClick = {
                            alias = "Conexión ${connections.size + 1}"
                            server = ""
                            port = "1433"
                            instance = ""
                            database = ""
                            user = ""
                            pass = ""
                            testedLicense = null
                            statusMessage = null
                            isAddingNew = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Agregar Conexión")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isAddingNew) {
                // ==========================================
                // LISTADO DE CONEXIONES GUARDADAS (CREDENCIALES OCULTAS)
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Por seguridad, los datos sensibles del servidor SQL permanecen protegidos y ocultos. Puede seleccionar la conexión activa o eliminar conexiones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Servidores y Conexiones (${connections.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                connections.forEach { conn ->
                    val isActive = conn.id == activeId
                    val lic = conn.license

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = conn.alias.ifBlank { "Servidor SQL" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }

                                if (isActive) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Activa", fontWeight = FontWeight.Bold) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                } else {
                                    TextButton(onClick = {
                                        prefs.setActiveConnectionId(conn.id)
                                        refreshList()
                                    }) {
                                        Text("Activar")
                                    }
                                }
                            }

                            // Información de la empresa y licencia vinculada
                            if (lic != null) {
                                if (lic.razonSocial.isNotBlank()) {
                                    Text(
                                        text = "Empresa: ${lic.razonSocial}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (lic.serial.isNotBlank()) {
                                    Text(
                                        text = "Serial ERP: ${lic.serial}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (lic.fechaVencimiento.isNotBlank()) {
                                    Text(
                                        text = if (lic.expired) "⚠️ Licencia Vencida (${lic.fechaVencimiento})"
                                        else "Vence: ${lic.fechaVencimiento} (${lic.diasRestantes} días)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (lic.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { connectionToDelete = conn }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Eliminar Conexión",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        alias = "Sucursal ${connections.size + 1}"
                        server = ""
                        port = "1433"
                        instance = ""
                        database = ""
                        user = ""
                        pass = ""
                        testedLicense = null
                        statusMessage = null
                        isAddingNew = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agregar Nueva Conexión")
                }

            } else {
                // ==========================================
                // FORMULARIO: REGISTRAR NUEVA CONEXIÓN
                // ==========================================
                Text(
                    text = "Registrar Nueva Conexión a Servidor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Asigne un nombre representativo a la conexión y complete los datos del servidor SQL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Nombre / Alias (Ej: Almacén Principal, Sucursal 1)") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Servidor / Host / IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = instance,
                        onValueChange = { instance = it },
                        label = { Text("Instancia (opcional)") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = database,
                    onValueChange = { database = it },
                    label = { Text("Base de Datos") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuario SQL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Contraseña SQL") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val config = DatabaseConfig(
                            alias = alias.trim().ifBlank { "Servidor SQL" },
                            server = server.trim(),
                            port = port.trim(),
                            instance = instance.trim(),
                            database = database.trim(),
                            user = user.trim(),
                            pass = pass
                        )
                        isTesting = true
                        statusMessage = "Conectando a SQL Server..."
                        coroutineScope.launch {
                            try {
                                val ok = DatabaseManager.testConnection(config)
                                if (!ok) {
                                    statusMessage = "No se pudo conectar a SQL Server."
                                    isTesting = false
                                    return@launch
                                }

                                statusMessage = "Conexión a BD exitosa. Validando licencia contra la nube..."
                                val lic = LicenseService.validate(config, prefs, forceOnline = true)
                                testedLicense = lic
                                statusMessage = if (lic.valid) "¡Conexión y Licencia validadas con éxito!" else lic.error
                            } catch (t: Throwable) {
                                statusMessage = "Error: ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}"
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probando y Validando...")
                    } else {
                        Icon(Icons.Default.Storage, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar Conexión y Validar Licencia")
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testedLicense?.valid == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                // Tarjeta de Licencia validada
                testedLicense?.let { lic ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (lic.valid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (lic.valid) "Licencia Válida" else "Licencia No Válida",
                                fontWeight = FontWeight.Bold,
                                color = if (lic.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            if (lic.razonSocial.isNotBlank()) Text("Empresa: ${lic.razonSocial}", style = MaterialTheme.typography.bodyMedium)
                            if (lic.serial.isNotBlank()) Text("Serial: ${lic.serial}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            if (lic.fechaVencimiento.isNotBlank()) Text("Vence: ${lic.fechaVencimiento} (${lic.diasRestantes} días restantes)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val canSave = testedLicense?.valid == true && !testedLicense!!.expired
                Button(
                    onClick = {
                        val finalAlias = alias.trim().ifBlank { "Servidor ${connections.size + 1}" }
                        val newConfig = DatabaseConfig(
                            alias = finalAlias,
                            server = server.trim(),
                            port = port.trim(),
                            instance = instance.trim(),
                            database = database.trim(),
                            user = user.trim(),
                            pass = pass,
                            license = testedLicense
                        )
                        prefs.saveConnection(newConfig)
                        refreshList()
                        isAddingNew = false
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (canSave) "Guardar Conexión y Proteger" else "Valide la Licencia para Guardar")
                }

                if (connections.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { isAddingNew = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar una conexión
    connectionToDelete?.let { conn ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar conexión?") },
            text = {
                Text("Se eliminará la conexión \"${conn.alias.ifBlank { "Servidor SQL" }}\" de este equipo. Los datos sensibles serán borrados por completo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.deleteConnection(conn.id)
                        connectionToDelete = null
                        refreshList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
