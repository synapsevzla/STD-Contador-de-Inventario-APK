package com.std.inventario.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.std.inventario.data.model.ItemConteo
import com.std.inventario.data.preferences.PreferencesManager
import com.std.inventario.data.repository.InventoryRepository
import com.std.inventario.ui.components.NumericKeypad
import kotlinx.coroutines.launch

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

    var items by remember { mutableStateOf<List<ItemConteo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isContinuousMode by remember { mutableStateOf(false) } // Modo escáner continuo (+1)
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Estado del teclado numérico
    var selectedItemForEdit by remember { mutableStateOf<ItemConteo?>(null) }
    var keypadValue by remember { mutableStateOf("") }

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

    fun loadItems() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                items = repo.getItemsOperacion(numeroD)
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                errorMessage = "Error al cargar: $msg"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(numeroD) {
        loadItems()
    }

    fun onScanBarcode(barcode: String) {
        val found = items.firstOrNull { it.codItem.equals(barcode.trim(), ignoreCase = true) }
        if (found != null) {
            triggerSuccessFeedback()
            if (isContinuousMode) {
                val newQty = found.existenciaContada + 1.0
                coroutineScope.launch {
                    try {
                        repo.updateCantidadConteo(numeroD, found.codItem, newQty, currentUser)
                        loadItems()
                    } catch (t: Throwable) {
                        val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                        errorMessage = "Error al actualizar: $msg"
                    }
                }
            } else {
                selectedItemForEdit = found
                keypadValue = found.existenciaContada.toString().removeSuffix(".0")
            }
        }
    }

    // ZXing Barcode Scanner Launcher
    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            onScanBarcode(scanned)
        }
    }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.codItem.contains(searchQuery, ignoreCase = true) ||
            it.descripcion.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conteo Op: $numeroD") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
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
                    placeholder = { Text("Buscar código o nombre...") },
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        ItemConteoRow(item = item, onClick = {
                            selectedItemForEdit = item
                            keypadValue = item.existenciaContada.toString().removeSuffix(".0")
                        })
                    }
                }
            }
        }

        // Diálogo de teclado táctil
        selectedItemForEdit?.let { target ->
            NumericKeypad(
                value = keypadValue,
                onValueChange = { keypadValue = it },
                allowDecimals = target.exDecimal,
                onConfirm = {
                    val finalQty = keypadValue.toDoubleOrNull() ?: 0.0
                    coroutineScope.launch {
                        try {
                            repo.updateCantidadConteo(numeroD, target.codItem, finalQty, currentUser)
                            selectedItemForEdit = null
                            loadItems()
                        } catch (t: Throwable) {
                            val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
                            errorMessage = "Error al guardar conteo: $msg"
                        }
                    }
                },
                onDismiss = { selectedItemForEdit = null }
            )
        }
    }
}

@Composable
fun ItemConteoRow(item: ItemConteo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Teórico: ${item.existenciaActual} ${item.unidad}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = if (item.existenciaContada > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (item.exDecimal) "%.2f".format(item.existenciaContada) else "%.0f".format(item.existenciaContada),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Contado", fontSize = 10.sp)
                }
            }
        }
    }
}
