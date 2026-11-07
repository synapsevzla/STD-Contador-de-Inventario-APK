package com.std.inventario.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    allowDecimals: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ingresar Cantidad") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Display de cantidad actual
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (value.isEmpty()) "0" else value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Botones Delta rápidos (+1, +5, +10, -1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DeltaButton(label = "+1", modifier = Modifier.weight(1f)) {
                        val current = value.toDoubleOrNull() ?: 0.0
                        onValueChange((current + 1).toString().removeSuffix(".0"))
                    }
                    DeltaButton(label = "+5", modifier = Modifier.weight(1f)) {
                        val current = value.toDoubleOrNull() ?: 0.0
                        onValueChange((current + 5).toString().removeSuffix(".0"))
                    }
                    DeltaButton(label = "+10", modifier = Modifier.weight(1f)) {
                        val current = value.toDoubleOrNull() ?: 0.0
                        onValueChange((current + 10).toString().removeSuffix(".0"))
                    }
                    DeltaButton(label = "-1", modifier = Modifier.weight(1f)) {
                        val current = value.toDoubleOrNull() ?: 0.0
                        val res = if (current > 0) current - 1 else 0.0
                        onValueChange(res.toString().removeSuffix(".0"))
                    }
                }

                // Grid de teclado numérico (1-9, C, 0, .)
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", if (allowDecimals) "." else "⌫")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            Button(
                                onClick = {
                                    when (key) {
                                        "C" -> onValueChange("")
                                        "⌫" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                        "." -> if (!value.contains(".")) onValueChange(if (value.isEmpty()) "0." else "$value.")
                                        else -> onValueChange(value + key)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = if (key == "C" || key == "⌫") {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                }
                            ) {
                                Text(key, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeltaButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
