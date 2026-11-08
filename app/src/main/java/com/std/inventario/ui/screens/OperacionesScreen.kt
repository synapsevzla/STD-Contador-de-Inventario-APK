package com.std.inventario.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.std.inventario.data.model.OperacionConteo
import com.std.inventario.data.preferences.PreferencesManager
import com.std.inventario.data.repository.InventoryRepository
import com.std.inventario.ui.components.CleanUncountedDialog
import com.std.inventario.ui.components.DeleteOperationDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperacionesScreen(
    prefs: PreferencesManager,
    onSelectOperacion: (String) -> Unit,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var operaciones by remember { mutableStateOf<List<OperacionConteo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Control del diálogo de limpieza de no contados
    var opToClean by remember { mutableStateOf<OperacionConteo?>(null) }
    var isCleaning by remember { mutableStateOf(false) }
    var cleanError by remember { mutableStateOf<String?>(null) }

    // Control del diálogo de eliminación total de la operación
    var opToDelete by remember { mutableStateOf<OperacionConteo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val repo = InventoryRepository(prefs.getDatabaseConfig())
                operaciones = repo.getOperaciones()
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                errorMessage = "Error al cargar: $msg"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Operaciones de Inventario") },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar Sesión")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { loadData() }) {
                            Text("Reintentar")
                        }
                    }
                }
                operaciones.isEmpty() -> {
                    Text(
                        text = "No hay operaciones de conteo abiertas (TipoOpI = 'r')",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(operaciones) { op ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectOperacion(op.numeroD) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Op: ${op.numeroD}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Badge {
                                            Text("${op.totalItems} ítems")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${op.deposito} (${op.codUbic})",
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (op.sucursal.isNotBlank()) {
                                        Text(
                                            text = op.sucursal,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Barra de acciones por comprobante
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Limpiar artículos no contados
                                            OutlinedButton(
                                                onClick = {
                                                    opToClean = op
                                                    cleanError = null
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteSweep,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Limpiar", fontSize = 12.sp)
                                            }

                                            // Eliminar Operación completa
                                            OutlinedButton(
                                                onClick = {
                                                    opToDelete = op
                                                    deleteError = null
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Eliminar", fontSize = 12.sp)
                                            }
                                        }

                                        Button(
                                            onClick = { onSelectOperacion(op.numeroD) },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("Contar", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de Confirmación para Limpieza de Items No Contados
        opToClean?.let { targetOp ->
            CleanUncountedDialog(
                numeroD = targetOp.numeroD,
                depositoNombre = targetOp.deposito,
                isCleaning = isCleaning,
                cleanError = cleanError,
                onConfirm = {
                    isCleaning = true
                    cleanError = null
                    coroutineScope.launch {
                        try {
                            val repo = InventoryRepository(prefs.getDatabaseConfig())
                            val deletedCount = repo.cleanUncountedItems(targetOp.numeroD)
                            opToClean = null
                            loadData()
                            snackbarHostState.showSnackbar(
                                "Se eliminaron $deletedCount artículos no contados de la operación #${targetOp.numeroD}."
                            )
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            cleanError = "Error al limpiar operación: $msg"
                        } finally {
                            isCleaning = false
                        }
                    }
                },
                onDismiss = {
                    opToClean = null
                    cleanError = null
                }
            )
        }

        // Diálogo de Confirmación para Eliminación Total de la Operación
        opToDelete?.let { targetOp ->
            DeleteOperationDialog(
                numeroD = targetOp.numeroD,
                depositoNombre = targetOp.deposito,
                isDeleting = isDeleting,
                deleteError = deleteError,
                onConfirm = {
                    isDeleting = true
                    deleteError = null
                    coroutineScope.launch {
                        try {
                            val repo = InventoryRepository(prefs.getDatabaseConfig())
                            repo.deleteOperacionCompleta(targetOp.numeroD)
                            opToDelete = null
                            loadData()
                            snackbarHostState.showSnackbar(
                                "Se eliminó por completo la operación #${targetOp.numeroD} y todos sus artículos."
                            )
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            deleteError = "Error al eliminar operación: $msg"
                        } finally {
                            isDeleting = false
                        }
                    }
                },
                onDismiss = {
                    opToDelete = null
                    deleteError = null
                }
            )
        }
    }
}
