package com.std.inventario

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.std.inventario.data.preferences.PreferencesManager
import com.std.inventario.ui.screens.ConfigScreen
import com.std.inventario.ui.screens.ConteoScreen
import com.std.inventario.ui.screens.LoginScreen
import com.std.inventario.ui.screens.OperacionesScreen
import com.std.inventario.ui.theme.STDContadorTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager

    // Buffer para pistolas lectoras de códigos de barras (emulación teclado HID)
    private val barcodeBuffer = StringBuilder()
    private var onBarcodeScannedCallback: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        setContent {
            STDContadorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (prefs.getLoggedUser() != null && prefs.isLicenseValid()) "operaciones" else "login"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                prefs = prefs,
                                onLoginSuccess = { navController.navigate("operaciones") { popUpTo("login") { inclusive = true } } },
                                onOpenConfig = { navController.navigate("config") }
                            )
                        }
                        composable("config") {
                            ConfigScreen(
                                prefs = prefs,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("operaciones") {
                            OperacionesScreen(
                                prefs = prefs,
                                onSelectOperacion = { numeroD -> navController.navigate("conteo/$numeroD") },
                                onLogout = {
                                    prefs.clearSession()
                                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                                }
                            )
                        }
                        composable("conteo/{numeroD}") { backStackEntry ->
                            val numeroD = backStackEntry.arguments?.getString("numeroD") ?: ""
                            ConteoScreen(
                                numeroD = numeroD,
                                prefs = prefs,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    // Captura de eventos de escáner láser físico / teclado HID
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar != 0 && event.keyCode != KeyEvent.KEYCODE_ENTER) {
                barcodeBuffer.append(unicodeChar.toChar())
            } else if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val scannedCode = barcodeBuffer.toString().trim()
                barcodeBuffer.clear()
                if (scannedCode.isNotEmpty()) {
                    onBarcodeScannedCallback?.invoke(scannedCode)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
