package com.std.inventario.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
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
    val initialConfig = remember { prefs.getDatabaseConfig() }
    val initialLicense = remember { prefs.getLicenseInfo() }

    var server by remember { mutableStateOf(initialConfig.server) }
    var port by remember { mutableStateOf(initialConfig.port) }
    var instance by remember { mutableStateOf(initialConfig.instance) }
    var database by remember { mutableStateOf(initialConfig.database) }
    var user by remember { mutableStateOf(initialConfig.user) }
    var pass by remember { mutableStateOf(initialConfig.pass) }

    var isTesting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var licenseInfo by remember { mutableStateOf<LicenseInfo?>(initialLicense) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración y Licencia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Parámetros de Microsoft SQL Server",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val config = DatabaseConfig(server.trim(), port.trim(), instance.trim(), database.trim(), user.trim(), pass)
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
                            val result = LicenseService.validate(config, prefs, forceOnline = true)
                            licenseInfo = result
                            statusMessage = if (result.valid) {
                                "¡Conexión y Licencia validadas con éxito!"
                            } else {
                                result.error ?: "Error al validar la licencia."
                            }
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            statusMessage = "Error: $msg"
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
                    color = if (licenseInfo?.valid == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Tarjeta de Información de la Licencia
            licenseInfo?.let { lic ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            lic.expired || !lic.valid -> MaterialTheme.colorScheme.errorContainer
                            lic.advertenciaProxima -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    lic.expired || !lic.valid -> Icons.Default.Error
                                    lic.advertenciaProxima -> Icons.Default.Warning
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = when {
                                    lic.expired || !lic.valid -> MaterialTheme.colorScheme.error
                                    lic.advertenciaProxima -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    lic.expired -> "Licencia Vencida"
                                    !lic.valid -> "Licencia No Válida"
                                    lic.advertenciaProxima -> "Licencia Activa (Próxima a Vencer)"
                                    else -> "Licencia Válida y Activa"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (!lic.valid && lic.error != null) {
                            Text(
                                text = lic.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        if (lic.razonSocial.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Empresa: ${lic.razonSocial}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (lic.serial.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Serial ERP: ${lic.serial}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (lic.fechaVencimiento.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Vence: ${lic.fechaVencimiento} (${lic.diasRestantes} días restantes)",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (lic.canalDistribucion.isNotBlank()) {
                            Text(
                                text = "Canal: ${lic.canalDistribucion}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val canSave = licenseInfo?.valid == true && !licenseInfo!!.expired
            Button(
                onClick = {
                    val config = DatabaseConfig(server.trim(), port.trim(), instance.trim(), database.trim(), user.trim(), pass)
                    prefs.saveDatabaseConfig(config)
                    onBack()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (canSave) "Guardar Conexión y Continuar" else "Valide la Licencia para Guardar")
            }
        }
    }
}
