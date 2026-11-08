package com.std.inventario.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.std.inventario.data.model.ConteoStats
import com.std.inventario.data.model.ItemConteo
import com.std.inventario.data.preferences.PreferencesManager
import com.std.inventario.data.repository.InventoryRepository
import com.std.inventario.ui.components.CleanUncountedDialog
import com.std.inventario.ui.components.DeleteOperationDialog
import com.std.inventario.ui.components.NumericKeypad
import kotlinx.coroutines.launch

enum class ConteoFilter { TODOS, CON_DIFERENCIA, CUADRADOS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteoScreen(
    numeroD: String,
    prefs: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { InventoryRepository(prefs.getDatabaseConfig()) }
    val currentUser = remember { prefs.getLoggedUser() ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }

    val pageSize = 50
    var items by remember { mutableStateOf<List<ItemConteo>>(emptyList()) }
    var stats by remember { mutableStateOf(ConteoStats()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(ConteoFilter.TODOS) }
    var isContinuousMode by remember { mutableStateOf(false) }

    var isInitialLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreItems by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Estado del teclado numérico
    var selectedItemForEdit by remember { mutableStateOf<ItemConteo?>(null) }
    var keypadValue by remember { mutableStateOf("") }

    // Menú de opciones y diálogos
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }
    var cleanError by remember { mutableStateOf<String?>(null) }

    var showDeleteOpDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Feedback háptico y sonoro
    fun triggerSuccessFeedback() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    // Carga de primera página y estadísticas al cambiar búsqueda o filtro
    LaunchedEffect(searchQuery, filterMode) {
        isInitialLoading = true
        errorMessage = null
        hasMoreItems = true
        try {
            val firstPage = repo.getItemsOperacionPaginado(
                numeroD = numeroD,
                offset = 0,
                limit = pageSize,
                searchQuery = searchQuery,
                filterMode = filterMode.name
            )
            items = firstPage
            hasMoreItems = firstPage.size >= pageSize
            stats = repo.getConteoStats(numeroD)
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
            errorMessage = "Error al cargar productos: $msg"
        } finally {
            isInitialLoading = false
        }
    }

    // Lazy load: detección de scroll al final para cargar la siguiente página
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !isLoadingMore && hasMoreItems && !isInitialLoading && total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            try {
                val nextItems = repo.getItemsOperacionPaginado(
                    numeroD = numeroD,
                    offset = items.size,
                    limit = pageSize,
                    searchQuery = searchQuery,
                    filterMode = filterMode.name
                )
                if (nextItems.size < pageSize) {
                    hasMoreItems = false
                }
                val newItems = nextItems.filter { next ->
                    items.none { it.codItem == next.codItem && it.nroLinea == next.nroLinea }
                }
                if (newItems.isEmpty()) {
                    hasMoreItems = false
                } else {
                    items = items + newItems
                }
            } catch (_: Throwable) {
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun onScanBarcode(barcode: String) {
        val trimmedCode = barcode.trim()
        if (trimmedCode.isBlank()) return

        coroutineScope.launch {
            try {
                // 1. Buscar en memoria local
                var found = items.firstOrNull { it.codItem.equals(trimmedCode, ignoreCase = true) }

                // 2. Si no está en la página local, buscar en SQL Server (código principal o código alterno SACODBAR)
                if (found == null) {
                    found = repo.findItemByCodeOrBarcode(numeroD, trimmedCode)
                    if (found != null) {
                        items = listOf(found) + items
                    }
                }

                if (found != null) {
                    triggerSuccessFeedback()
                    if (isContinuousMode) {
                        val newQty = found.existenciaContada + 1.0
                        repo.updateCantidadConteo(numeroD, found.codItem, newQty, currentUser)
                        items = items.map {
                            if (it.codItem == found.codItem) {
                                it.copy(
                                    existenciaContada = newQty,
                                    diferencia = newQty - it.existenciaActual,
                                    codUsuaConteo = currentUser
                                )
                            } else it
                        }
                        stats = repo.getConteoStats(numeroD)
                    } else {
                        selectedItemForEdit = found
                        keypadValue = formatQty(found.existenciaContada, found.exDecimal)
                    }
                } else {
                    errorMessage = "Producto con código '$trimmedCode' no pertenece a esta operación."
                }
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                errorMessage = "Error al escanear: $msg"
            }
        }
    }

    // ZXing Barcode Scanner Launcher
    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            onScanBarcode(scanned)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Conteo Op: $numeroD") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                            setPrompt("Escanee código de barras del producto")
                            setCameraId(0)
                            setBeepEnabled(false)
                            setBarcodeImageEnabled(false)
                        }
                        barcodeLauncher.launch(options)
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear")
                    }

                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Limpiar no contados", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                cleanError = null
                                showCleanDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Eliminar conteo completo", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                deleteError = null
                                showDeleteOpDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Barra de filtros y switch de modo continuo (+1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+1 Auto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = isContinuousMode,
                        onCheckedChange = { isContinuousMode = it }
                    )
                }
            }

            // Chips de filtrado con totales exactos de la BD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == ConteoFilter.TODOS,
                    onClick = { filterMode = ConteoFilter.TODOS },
                    label = { Text("Todos (${stats.totalItems})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = filterMode == ConteoFilter.CON_DIFERENCIA,
                    onClick = { filterMode = ConteoFilter.CON_DIFERENCIA },
                    label = { Text("Con Dif. (${stats.conDiferencia})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
                FilterChip(
                    selected = filterMode == ConteoFilter.CUADRADOS,
                    onClick = { filterMode = ConteoFilter.CUADRADOS },
                    label = { Text("Cuadrados (${stats.cuadrados})", fontSize = 12.sp) }
                )
            }

            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (isInitialLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando productos...", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No se encontraron productos con el filtro aplicado.",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items, key = { index, it -> "${it.codItem}_${it.nroLinea}_$index" }) { _, item ->
                        ItemConteoRow(item = item, onClick = {
                            selectedItemForEdit = item
                            keypadValue = formatQty(item.existenciaContada, item.exDecimal)
                        })
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Cargando más productos...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    } else if (!hasMoreItems && items.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Fin de la lista (${items.size} de ${stats.totalItems} productos)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de teclado táctil con cálculo en vivo de diferencia
        selectedItemForEdit?.let { target ->
            NumericKeypad(
                value = keypadValue,
                onValueChange = { keypadValue = it },
                allowDecimals = target.exDecimal,
                title = target.codItem,
                itemDescription = target.descripcion,
                existenciaTeorica = target.existenciaActual,
                unidad = target.unidad,
                onConfirm = {
                    val finalQty = keypadValue.toDoubleOrNull() ?: 0.0
                    coroutineScope.launch {
                        try {
                            repo.updateCantidadConteo(numeroD, target.codItem, finalQty, currentUser)
                            selectedItemForEdit = null
                            items = items.map {
                                if (it.codItem == target.codItem) {
                                    it.copy(
                                        existenciaContada = finalQty,
                                        diferencia = finalQty - it.existenciaActual,
                                        codUsuaConteo = currentUser
                                    )
                                } else it
                            }
                            stats = repo.getConteoStats(numeroD)
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            errorMessage = "Error al guardar conteo: $msg"
                        }
                    }
                },
                onDismiss = { selectedItemForEdit = null }
            )
        }

        // Diálogo de confirmación para eliminar artículos no contados en esta operación
        if (showCleanDialog) {
            CleanUncountedDialog(
                numeroD = numeroD,
                isCleaning = isCleaning,
                cleanError = cleanError,
                onConfirm = {
                    isCleaning = true
                    cleanError = null
                    coroutineScope.launch {
                        try {
                            val deletedCount = repo.cleanUncountedItems(numeroD)
                            showCleanDialog = false
                            // Recargar primera página y actualizar stats
                            val firstPage = repo.getItemsOperacionPaginado(
                                numeroD = numeroD,
                                offset = 0,
                                limit = pageSize,
                                searchQuery = searchQuery,
                                filterMode = filterMode.name
                            )
                            items = firstPage
                            hasMoreItems = firstPage.size >= pageSize
                            stats = repo.getConteoStats(numeroD)
                            snackbarHostState.showSnackbar(
                                "Se eliminaron $deletedCount artículos no contados de la operación #$numeroD."
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
                    showCleanDialog = false
                    cleanError = null
                }
            )
        }

        // Diálogo de confirmación para eliminar la operación completa
        if (showDeleteOpDialog) {
            DeleteOperationDialog(
                numeroD = numeroD,
                isDeleting = isDeleting,
                deleteError = deleteError,
                onConfirm = {
                    isDeleting = true
                    deleteError = null
                    coroutineScope.launch {
                        try {
                            repo.deleteOperacionCompleta(numeroD)
                            showDeleteOpDialog = false
                            onBack()
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            deleteError = "Error al eliminar operación: $msg"
                        } finally {
                            isDeleting = false
                        }
                    }
                },
                onDismiss = {
                    showDeleteOpDialog = false
                    deleteError = null
                }
            )
        }
    }
}

@Composable
fun ItemConteoRow(item: ItemConteo, onClick: () -> Unit) {
    val dif = item.existenciaContada - item.existenciaActual
    val (difContainerColor, difContentColor, difLabel) = when {
        dif == 0.0 -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Dif: 0 (Cuadra)"
        )
        dif > 0.0 -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Dif: +${formatQty(dif, item.exDecimal)} (Sobrante)"
        )
        else -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Dif: ${formatQty(dif, item.exDecimal)} (Faltante)"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.codItem,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (item.existenciaContada > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatQty(item.existenciaContada, item.exDecimal),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.existenciaContada > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Contado",
                            fontSize = 10.sp,
                            color = if (item.existenciaContada > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Fila de Comparativa: Teórico vs Diferencia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Teórico: ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatQty(item.existenciaActual, item.exDecimal)} ${item.unidad}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = difContainerColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = difLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = difContentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun formatQty(qty: Double, exDecimal: Boolean): String {
    return if (exDecimal) {
        "%.2f".format(qty)
    } else {
        if (qty % 1.0 == 0.0) qty.toLong().toString() else "%.2f".format(qty)
    }
}
