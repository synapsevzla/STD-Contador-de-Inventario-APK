package com.std.inventario.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CleanUncountedDialog(
    numeroD: String,
    depositoNombre: String? = null,
    isCleaning: Boolean,
    cleanError: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmInputText by remember { mutableStateOf("") }
    val canConfirm = confirmInputText.trim().equals(numeroD.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = {
            if (!isCleaning) onDismiss()
        },
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Confirmar Limpieza de Conteo",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val detailText = if (!depositoNombre.isNullOrBlank()) {
                    "Usted está a punto de eliminar de la operación #$numeroD ($depositoNombre) todos los artículos que NO fueron contados (con cantidad 0 o no registrados)."
                } else {
                    "Usted está a punto de eliminar de la operación #$numeroD todos los artículos que NO fueron contados (con cantidad 0 o no registrados)."
                }

                Text(
                    text = detailText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Esta acción eliminará permanentemente los artículos no contados de la base de datos.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Por favor, escriba el número de la operación para confirmar:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = confirmInputText,
                    onValueChange = { confirmInputText = it },
                    placeholder = {
                        Text(
                            numeroD,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCleaning
                )

                cleanError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !isCleaning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isCleaning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Eliminar No Contados")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCleaning
            ) {
                Text("Cancelar")
            }
        }
    )
}
