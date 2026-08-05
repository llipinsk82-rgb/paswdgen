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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VaultMigrationControls(
    viewModel: MainViewModel,
    enabled: Boolean,
    preview: CsvImportPreview?,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
) {
    var showExportWarning by remember { mutableStateOf(false) }

    val importCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        onExternalFlowChanged(false)
        if (uri != null) viewModel.previewCsvImport(uri)
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PasswordCsvCodec.MIME_TYPE),
    ) { uri: Uri? ->
        onExternalFlowChanged(false)
        if (uri != null) viewModel.exportGoogleCsv(uri)
    }

    AutofillSettingsCard()
    Spacer(Modifier.height(8.dp))

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SyncAlt, contentDescription = null, tint = PgCyan)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Migracja Google CSV", color = PgText, fontSize = 14.sp)
                    Text(
                        "Import lub eksport do Google Password Manager · plik CSV nie jest szyfrowany",
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
                        onSensitiveActionRequest("Import haseł z CSV") {
                            onExternalFlowChanged(true)
                            importCsv.launch(
                                arrayOf(
                                    PasswordCsvCodec.MIME_TYPE,
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/plain",
                                ),
                            )
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, PgStrokeStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PgText),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Import CSV", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showExportWarning = true },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, PgDanger.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PgDanger),
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Eksport CSV", fontSize = 11.sp)
                }
            }
        }
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            containerColor = PgSurface,
            shape = RoundedCornerShape(22.dp),
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = PgDanger) },
            title = { Text("CSV zawiera jawne hasła", color = PgText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Plik będzie czytelny dla każdej aplikacji i osoby mającej do niego dostęp. Używaj go wyłącznie do migracji, zaimportuj w Google Password Manager i natychmiast usuń wszystkie kopie CSV.",
                        color = PgTextMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        "Google przyjmuje maksymalnie ${PasswordCsvCodec.MAX_GOOGLE_EXPORT_ROWS} rekordów w jednym pliku. Wpisy bez adresu witryny nie zostaną wyeksportowane.",
                        color = PgTextMuted,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportWarning = false
                        onSensitiveActionRequest("Eksport jawnego pliku CSV") {
                            onExternalFlowChanged(true)
                            exportCsv.launch(defaultCsvName())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PgDanger),
                ) {
                    Text("Rozumiem, eksportuj")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Anuluj", color = PgTextMuted)
                }
            },
        )
    }

    preview?.let { value ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCsvImport,
            containerColor = PgSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Podgląd importu CSV", color = PgText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    PreviewLine("Wiersze danych", value.totalRows)
                    PreviewLine("Gotowe do importu", value.readyRows)
                    PreviewLine("Odrzucone", value.rejectedRows)
                    PreviewLine("Duplikaty", value.duplicateRows)
                    if (value.formulaLikeFields > 0) {
                        Text(
                            "Wykryto ${value.formulaLikeFields} pól zaczynających się od =, +, - lub @. Dane nie zostały zmienione, ale jawnego CSV nie należy otwierać w arkuszu kalkulacyjnym.",
                            color = PgDanger,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "Po imporcie usuń źródłowy CSV z telefonu, kosza i chmury. PasswdGen zapisze zaakceptowane rekordy w zaszyfrowanym sejfie.",
                        color = PgTextMuted,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmCsvImport,
                    enabled = value.readyRows > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PgCyan,
                        contentColor = Color(0xFF001517),
                    ),
                ) {
                    Text("Importuj ${value.readyRows}")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCsvImport) {
                    Text("Anuluj", color = PgTextMuted)
                }
            },
        )
    }
}

@Composable
private fun PreviewLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PgTextMuted, fontSize = 12.sp)
        Text(value.toString(), color = PgText, fontSize = 12.sp)
    }
}

private fun defaultCsvName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "PasswdGen-Google-$timestamp.csv"
}
