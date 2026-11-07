package com.std.inventario.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.std.inventario.R
import com.std.inventario.data.license.LicenseService
import com.std.inventario.data.model.LicenseInfo
import com.std.inventario.data.preferences.PreferencesManager
import com.std.inventario.data.repository.InventoryRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    prefs: PreferencesManager,
    onLoginSuccess: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var codUsua by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var licenseInfo by remember { mutableStateOf<LicenseInfo?>(prefs.getLicenseInfo()) }

    // Validación periódica / silenciosa de licencia si hay BD configurada
    LaunchedEffect(Unit) {
        val config = prefs.getDatabaseConfig()
        if (config.server.isNotBlank() && config.database.isNotBlank()) {
            try {
                val updated = LicenseService.validate(config, prefs, forceOnline = false)
                licenseInfo = updated
            } catch (_: Throwable) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STD Inventario") },
                actions = {
                    IconButton(onClick = onOpenConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "STD Inventario Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "STD Inventario",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Toma de Inventario Físico en Almacén",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Banner informativo de Licencia
            licenseInfo?.let { lic ->
                when {
                    lic.expired || !lic.valid -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (lic.expired) "⚠️ Licencia Vencida" else "⚠️ Licencia Requerida",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = lic.error ?: "Ingrese a Configuración para validar la licencia.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    lic.advertenciaProxima -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Atención: Su licencia vence en ${lic.diasRestantes} días.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    else -> {
                        Text(
                            text = "${lic.razonSocial} • ${lic.diasRestantes} días de licencia",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            OutlinedTextField(
                value = codUsua,
                onValueChange = { codUsua = it },
                label = { Text("Código de Usuario (CodUsua)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val config = prefs.getDatabaseConfig()
                    if (config.server.isBlank()) {
                        errorMessage = "Configure los parámetros de SQL Server primero"
                        onOpenConfig()
                        return@Button
                    }
                    if (!prefs.isLicenseValid()) {
                        errorMessage = "La licencia no es válida o está vencida. Ingrese a Configuración."
                        onOpenConfig()
                        return@Button
                    }
                    if (codUsua.isBlank() || email.isBlank()) {
                        errorMessage = "Ingrese usuario y correo"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val repo = InventoryRepository(config)
                            val usuario = repo.login(codUsua.trim(), email.trim())
                            if (usuario == null) {
                                errorMessage = "Usuario o correo no válidos"
                            } else {
                                if (usuario.clave == clave.trim()) {
                                    prefs.saveLoggedUser(usuario.codUsua, usuario.descrip)
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Contraseña incorrecta"
                                }
                            }
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            errorMessage = "Error de conexión: $msg"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Ingresar al Sistema", fontSize = 16.sp)
                }
            }
        }
    }
}
