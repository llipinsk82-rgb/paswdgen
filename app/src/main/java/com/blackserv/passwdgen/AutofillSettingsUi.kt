package com.blackserv.passwdgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Password
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AutofillSettingsCard() {
    val context = LocalContext.current
    var diagnostic by remember { mutableStateOf(AutofillDiagnosticStore.load(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PgSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, PgStroke),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Password, contentDescription = null, tint = PgCyan)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Autouzupełnianie Android", color = PgText, fontSize = 14.sp)
                    Text(
                        "Ustaw PasswdGen jako usługę haseł. Dane są oferowane dopiero po uwierzytelnieniu i tylko dla zweryfikowanej witryny lub aplikacji.",
                        color = PgTextMuted,
                        fontSize = 10.sp,
                    )
                }
            }

            Button(
                onClick = {
                    val request = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { context.startActivity(request) }
                        .onFailure {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PgCyan,
                    contentColor = Color(0xFF001517),
                ),
            ) {
                Text("Ustaw jako domyślny sejf", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { diagnostic = AutofillDiagnosticStore.load(context) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, PgStrokeStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PgText),
            ) {
                Icon(Icons.Outlined.BugReport, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Odśwież diagnostykę", fontSize = 12.sp)
            }

            diagnostic?.let { value ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        "Ostatnie żądanie Autofill",
                        color = PgText,
                        fontSize = 12.sp,
                    )
                    Text(
                        value.asReport(),
                        color = PgTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                    Text(
                        "Raport nie zawiera loginów, haseł ani tekstu wpisanego w formularzu.",
                        color = PgTextMuted,
                        fontSize = 9.sp,
                    )
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("PasswdGen Autofill diagnostic", value.asReport()),
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = PgCyan)
                        Spacer(Modifier.width(5.dp))
                        Text("Kopiuj raport", color = PgCyan, fontSize = 11.sp)
                    }
                }
            } ?: Text(
                "Brak zapisanego żądania. Wykonaj próbę autouzupełniania i wróć tutaj.",
                color = PgTextMuted,
                fontSize = 10.sp,
            )
        }
    }
}
