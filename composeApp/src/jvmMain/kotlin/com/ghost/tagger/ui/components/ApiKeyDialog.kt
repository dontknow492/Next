package com.ghost.tagger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI

@Composable
fun ApiKeyDialog(
    isOpen: Boolean,
    currentKey: String?,
    isVerifying: Boolean,
    verificationError: String?,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit
) {
    if (isOpen) {
        var apiKeyInput by remember(currentKey) { mutableStateOf(currentKey ?: "") }
        var isKeyVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isVerifying) onDismiss() },
            title = {
                Text(
                    text = "Hugging Face Authentication",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Enter your User Access Token to download restricted models or increase rate limits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 🔑 The Input Field
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Access Token") },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        isError = verificationError != null,
                        enabled = !isVerifying
                    )

                    // ❌ Error Message
                    if (verificationError != null) {
                        Text(
                            text = verificationError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // 🔗 Help Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Get your token here",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().browse(URI("https://huggingface.co/settings/tokens"))
                                    }
                                } catch (e: Exception) {
                                    // Fallback or log if browser fails to open
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onVerify(apiKeyInput) },
                    enabled = apiKeyInput.isNotBlank() && !isVerifying
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Verify & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isVerifying
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}