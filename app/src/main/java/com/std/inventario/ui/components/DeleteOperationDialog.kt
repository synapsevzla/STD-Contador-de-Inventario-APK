package com.std.inventario.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeleteOperationDialog(
    numeroD: String,
    depositoNombre: String? = null,
    isDeleting: Boolean,
    deleteError: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmInputText by remember { mutableStateOf("") }
    val canConfirm = confirmInputText.trim().equals(numeroD.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(38.dp)
            )
        },
        title = {
            Text(
                text = "Eliminar Operación Completa",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val detailText = if (!depositoNombre.isNullOrBlank()) {
                    "Usted está a punto de eliminar por completo la operación #$numeroD ($depositoNombre) y TODOS sus artículos asociados."
                } else {
                    "Usted está a punto de eliminar por completo la operación #$numeroD y TODOS sus artículos asociados."
                }

                Text(
                    text = detailText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ADVERTENCIA: Esta acción es destructiva e irreversible. Se eliminará el encabezado del conteo y cada una de sus líneas registradas en SQL Server.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Para confirmar la eliminación total, escriba el número de la operación ($numeroD):",
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
                    enabled = !isDeleting
                )

                deleteError?.let { err ->
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
                enabled = canConfirm && !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Eliminar Operación")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancelar")
            }
        }
    )
}
