package com.blackserv.passwdgen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VaultBackupControls(
    viewModel: MainViewModel,
    enabled: Boolean,
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(VaultBackupCodec.MIME_TYPE),
    ) { uri ->
        val secret = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && secret != null) viewModel.exportBackup(uri, secret)
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importUri = uri
            passphrase = ""
            dialogError = null
            showImportDialog = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PgSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, PgStroke),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = PgCyan)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Kopia sejfu", color = PgText, fontSize = 14.sp)
                    Text(
                        "Zaszyfrowany plik .${VaultBackupCodec.FILE_EXTENSION} · lokalnie lub w Twojej chmurze",
                        color = PgTextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        passphrase = ""
                        confirmation = ""
                        dialogError = null
                        showExportDialog = true
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, PgCyan.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PgCyanBright),
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Eksport", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        openDocument.launch(arrayOf(VaultBackupCodec.MIME_TYPE, "application/octet-stream", "application/json"))
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, PgStrokeStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PgText),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import", fontSize = 11.sp)
                }
            }
        }
    }

    if (showExportDialog) {
        BackupPassphraseDialog(
            title = "Zabezpiecz kopię",
            explanation = "Ustaw osobne hasło kopii. Bez niego nie będzie można odtworzyć sejfu.",
            passphrase = passphrase,
            confirmation = confirmation,
            requireConfirmation = true,
            error = dialogError,
            onPassphraseChange = { passphrase = it; dialogError = null },
            onConfirmationChange = { confirmation = it; dialogError = null },
            onDismiss = {
                passphrase = ""
                confirmation = ""
                showExportDialog = false
            },
            onConfirm = {
                when {
                    passphrase.length < 12 -> dialogError = "Hasło kopii musi mieć co najmniej 12 znaków."
                    passphrase != confirmation -> dialogError = "Hasła kopii nie są identyczne."
                    else -> {
                        pendingExportPassphrase = passphrase
                        passphrase = ""
                        confirmation = ""
                        showExportDialog = false
                        createDocument.launch(defaultBackupName())
                    }
                }
            },
        )
    }

    if (showImportDialog) {
        BackupPassphraseDialog(
            title = "Odtwórz kopię",
            explanation = "Podaj hasło użyte podczas tworzenia kopii. Nowsze wpisy zastąpią starsze wpisy o tym samym identyfikatorze.",
            passphrase = passphrase,
            confirmation = "",
            requireConfirmation = false,
            error = dialogError,
            onPassphraseChange = { passphrase = it; dialogError = null },
            onConfirmationChange = {},
            onDismiss = {
                passphrase = ""
                importUri = null
                showImportDialog = false
            },
            onConfirm = {
                val selectedUri = importUri
                if (passphrase.length < 12) {
                    dialogError = "Hasło kopii musi mieć co najmniej 12 znaków."
                } else if (selectedUri != null) {
                    val secret = passphrase
                    passphrase = ""
                    importUri = null
                    showImportDialog = false
                    viewModel.importBackup(selectedUri, secret)
                }
            },
        )
    }
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    explanation: String,
    passphrase: String,
    confirmation: String,
    requireConfirmation: Boolean,
    error: String?,
    onPassphraseChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PgSurface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, color = PgText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation, color = PgTextMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("Hasło kopii") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = backupFieldColors(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = onConfirmationChange,
                        label = { Text("Powtórz hasło kopii") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = backupFieldColors(),
                    )
                }
                error?.let { Text(it, color = PgDanger, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PgCyan, contentColor = Color(0xFF001517)),
            ) {
                Text(if (requireConfirmation) "Utwórz kopię" else "Odtwórz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj", color = PgTextMuted) }
        },
    )
}

@Composable
private fun backupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PgText,
    unfocusedTextColor = PgText,
    focusedBorderColor = PgCyan,
    unfocusedBorderColor = PgStrokeStrong,
    focusedLabelColor = PgCyan,
    unfocusedLabelColor = PgTextMuted,
    cursorColor = PgCyan,
)

private fun defaultBackupName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "PasswdGen-backup-$timestamp.${VaultBackupCodec.FILE_EXTENSION}"
}