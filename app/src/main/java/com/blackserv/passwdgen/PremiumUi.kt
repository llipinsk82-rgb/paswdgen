package com.blackserv.passwdgen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PremiumGold = Color(0xFFFFD58A)
private val PremiumGoldSoft = Color(0x33FFD58A)
private val PremiumCyan = Color(0xFF35E7D0)
private val PremiumSurface = Color(0xFF151C24)
private val PremiumSurfaceRaised = Color(0xFF1D2631)
private val PremiumStroke = Color(0xFF31404F)

@Composable
internal fun PremiumGeneratorContent(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    var advancedVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Generator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Prywatnie. Lokalnie. Bez kompromisów.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PremiumGoldSoft)
                    .border(1.dp, PremiumGold.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("AES VAULT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PremiumGold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1B3440), Color(0xFF16232D), Color(0xFF221D31)),
                        ),
                    )
                    .border(1.dp, PremiumCyan.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "WYGENEROWANE HASŁO",
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                            color = PremiumCyan,
                        )
                        StrengthBadge(state.generated.entropyBits)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xCC0C1218))
                            .border(1.dp, PremiumGold.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = state.generated.value,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumGold,
                        )
                    }

                    Text(
                        text = "${state.generated.entropyBits} bitów entropii · ${state.options.length} znaków",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onGenerate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PremiumCyan,
                                contentColor = Color(0xFF071310),
                            ),
                        ) {
                            Text("Nowe hasło", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Kopiuj")
                        }
                    }
                }
            }
        }

        PremiumSectionCard(title = "Długość", trailing = "${state.options.length}") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 16, 20, 24).forEach { preset ->
                    LengthChip(
                        value = preset,
                        selected = state.options.length == preset,
                        modifier = Modifier.weight(1f),
                        onClick = { onOptionsChange { it.copy(length = preset) } },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = state.options.length.toFloat(),
                onValueChange = { value ->
                    onOptionsChange {
                        it.copy(
                            length = value.toInt().coerceIn(
                                PasswordGenerator.MIN_LENGTH,
                                PasswordGenerator.MAX_LENGTH,
                            ),
                        )
                    }
                },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("8", style = MaterialTheme.typography.labelSmall)
                Text("zalecane 16–20", style = MaterialTheme.typography.labelSmall, color = PremiumCyan)
                Text("32", style = MaterialTheme.typography.labelSmall)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advancedVisible = !advancedVisible },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PremiumSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PremiumStroke),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Zestawy znaków", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Dostosuj skład hasła",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(if (advancedVisible) "−" else "+", fontSize = 24.sp, color = PremiumCyan)
                }
                if (advancedVisible) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PremiumStroke)
                    PremiumOptionRow("Małe litery", "a–z", state.options.lowerCase) {
                        onOptionsChange { options -> options.copy(lowerCase = it) }
                    }
                    PremiumOptionRow("Wielkie litery", "A–Z", state.options.upperCase) {
                        onOptionsChange { options -> options.copy(upperCase = it) }
                    }
                    PremiumOptionRow("Cyfry", "0–9", state.options.digits) {
                        onOptionsChange { options -> options.copy(digits = it) }
                    }
                    PremiumOptionRow("Znaki specjalne", "!@#", state.options.special) {
                        onOptionsChange { options -> options.copy(special = it) }
                    }
                    PremiumOptionRow("Pomijaj podobne", "I l 1 O 0", state.options.avoidAmbiguous) {
                        onOptionsChange { options -> options.copy(avoidAmbiguous = it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSectionCard(
    title: String,
    trailing: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumSurfaceRaised),
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumStroke),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(trailing, fontWeight = FontWeight.Bold, color = PremiumGold)
            }
            content()
        }
    }
}

@Composable
private fun StrengthBadge(bits: Int) {
    val label = when {
        bits >= 120 -> "BARDZO MOCNE"
        bits >= 80 -> "MOCNE"
        bits >= 60 -> "DOBRE"
        else -> "PODSTAWOWE"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PremiumCyan.copy(alpha = 0.12f))
            .border(1.dp, PremiumCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PremiumCyan)
    }
}

@Composable
private fun LengthChip(
    value: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PremiumGold else Color.Transparent)
            .border(
                1.dp,
                if (selected) PremiumGold else PremiumStroke,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$value",
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF17130C) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PremiumOptionRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun PremiumVaultRow(
    entry: VaultEntry,
    expanded: Boolean,
    revealed: Boolean,
    onToggleExpanded: () -> Unit,
    onRevealChange: (Boolean) -> Unit,
    onCopyLogin: () -> Unit,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumSurfaceRaised),
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumStroke),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PremiumCyan.copy(alpha = 0.85f), Color(0xFF6685FF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.service.trim().firstOrNull()?.uppercase() ?: "•",
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF071310),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.service,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.website.isNotBlank()) {
                        Text(
                            entry.website,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(if (expanded) "⌃" else "⌄", color = PremiumCyan, fontSize = 20.sp)
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PremiumStroke)
                Text("LOGIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PremiumCyan)
                Text(entry.username, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Text("HASŁO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PremiumCyan)
                Text(
                    if (revealed) entry.password else "••••••••••••",
                    fontFamily = FontFamily.Monospace,
                    color = PremiumGold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pokaż", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = revealed, onCheckedChange = onRevealChange)
                    }
                    Row {
                        TextButton(onClick = onCopyLogin) { Text("Login") }
                        TextButton(onClick = onCopyPassword) { Text("Hasło") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text("Edytuj") }
                    TextButton(onClick = onDelete) { Text("Usuń") }
                }
            }
        }
    }
}
