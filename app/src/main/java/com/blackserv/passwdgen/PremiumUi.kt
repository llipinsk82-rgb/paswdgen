package com.blackserv.passwdgen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PgBackground = Color(0xFF030B11)
internal val PgSurface = Color(0xFF0A151E)
internal val PgSurfaceRaised = Color(0xFF101E29)
internal val PgStroke = Color(0xFF263B49)
internal val PgStrokeStrong = Color(0xFF345365)
internal val PgCyan = Color(0xFF22DDE5)
internal val PgCyanBright = Color(0xFF48F5EF)
internal val PgGreen = Color(0xFF23E58B)
internal val PgText = Color(0xFFF2F8FA)
internal val PgTextMuted = Color(0xFF91A7B3)
internal val PgDanger = Color(0xFFFF6F7D)

private val PanelShape = RoundedCornerShape(20.dp)

@Composable
internal fun PremiumAppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF06121A), PgBackground, Color(0xFF02070B)),
                ),
            )
            .drawWithCache {
                val cyanGlow = Brush.radialGradient(
                    colors = listOf(PgCyan.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.04f, size.height * 0.27f),
                    radius = size.minDimension * 0.92f,
                )
                val blueGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF2565D9).copy(alpha = 0.055f), Color.Transparent),
                    center = Offset(size.width * 0.96f, size.height * 0.84f),
                    radius = size.minDimension * 0.78f,
                )
                onDrawBehind {
                    drawRect(cyanGlow)
                    drawRect(blueGlow)
                }
            },
    ) {
        content()
    }
}

@Composable
internal fun PremiumTopBar(section: AppSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.passwdgen_brand_icon),
            contentDescription = "PasswdGen",
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (section == AppSection.GENERATOR) "Generator" else "Sejf",
                color = PgText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("PasswdGen · lokalna ochrona", color = PgTextMuted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PgSurfaceRaised)
                .border(1.dp, PgStroke, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = PgCyan, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
internal fun PremiumBottomBar(section: AppSection, onSelect: (AppSection) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(22.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF20A151E))
            .border(1.dp, PgStroke, RoundedCornerShape(24.dp))
            .padding(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottomDestination(
                label = "Generator",
                icon = Icons.Outlined.AutoAwesome,
                selected = section == AppSection.GENERATOR,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(AppSection.GENERATOR) },
            )
            BottomDestination(
                label = "Sejf",
                icon = Icons.Outlined.Lock,
                selected = section == AppSection.VAULT,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(AppSection.VAULT) },
            )
        }
    }
}

@Composable
private fun BottomDestination(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) PgCyan.copy(alpha = 0.13f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) PgCyan.copy(alpha = 0.38f) else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) PgCyanBright else PgTextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) PgText else PgTextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun PremiumGeneratorContent(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PasswordHeroCard(state = state, onCopy = onCopy)
        LengthPanel(state = state, onOptionsChange = onOptionsChange)
        CharacterOptionsPanel(state = state, onOptionsChange = onOptionsChange)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onGenerate,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PgCyan, contentColor = Color(0xFF001517)),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generuj", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, PgStrokeStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PgText),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kopiuj", fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = PgTextMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Schowek czyści się automatycznie po 60 s", color = PgTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PasswordHeroCard(state: AppUiState, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(18.dp, PanelShape),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF132A36), Color(0xFF0B1922), Color(0xFF101925)),
                    ),
                )
                .border(1.dp, PgStrokeStrong, PanelShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wygenerowane hasło", color = PgTextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = state.generated.value,
                        color = PgText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(PgSurface.copy(alpha = 0.82f))
                        .border(1.dp, PgStroke, RoundedCornerShape(13.dp)),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopiuj hasło", tint = PgText, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = PgStroke.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Siła hasła", color = PgTextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(strengthLabel(state.generated.entropyBits), color = PgGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            StrengthSegments(state.generated.entropyBits)
        }
    }
}

@Composable
private fun StrengthSegments(bits: Int) {
    val active = when {
        bits >= 120 -> 5
        bits >= 90 -> 4
        bits >= 65 -> 3
        bits >= 45 -> 2
        else -> 1
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (index < active) PgGreen else PgStroke),
            )
        }
    }
}

@Composable
private fun LengthPanel(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
) {
    PremiumPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Długość hasła", color = PgText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${state.options.length}", color = PgCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("  ·  8–32", color = PgTextMuted, fontSize = 11.sp)
        }
        Slider(
            value = state.options.length.toFloat(),
            onValueChange = { value ->
                onOptionsChange {
                    it.copy(length = value.toInt().coerceIn(PasswordGenerator.MIN_LENGTH, PasswordGenerator.MAX_LENGTH))
                }
            },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1,
            colors = SliderDefaults.colors(
                thumbColor = PgCyanBright,
                activeTrackColor = PgCyan,
                inactiveTrackColor = PgStroke,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(12, 16, 20, 24, 32).forEach { preset ->
                LengthChip(
                    value = preset,
                    selected = state.options.length == preset,
                    modifier = Modifier.weight(1f),
                    onClick = { onOptionsChange { it.copy(length = preset) } },
                )
            }
        }
    }
}

@Composable
private fun CharacterOptionsPanel(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = listOf(
        state.options.lowerCase,
        state.options.upperCase,
        state.options.digits,
        state.options.special,
        state.options.avoidAmbiguous,
    ).count { it }

    PremiumPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PgCyan.copy(alpha = 0.11f))
                    .border(1.dp, PgCyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = PgCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Opcje znaków", color = PgText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$enabledCount z 5 aktywnych", color = PgTextMuted, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Zwiń opcje znaków" else "Rozwiń opcje znaków",
                tint = PgCyan,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionToggleRow("a", "Małe litery", state.options.lowerCase) {
                    onOptionsChange { options -> options.copy(lowerCase = it) }
                }
                OptionToggleRow("A", "Wielkie litery", state.options.upperCase) {
                    onOptionsChange { options -> options.copy(upperCase = it) }
                }
                OptionToggleRow("1", "Cyfry", state.options.digits) {
                    onOptionsChange { options -> options.copy(digits = it) }
                }
                OptionToggleRow("#", "Znaki specjalne", state.options.special) {
                    onOptionsChange { options -> options.copy(special = it) }
                }
                OptionToggleRow("Ø", "Pomiń podobne", state.options.avoidAmbiguous) {
                    onOptionsChange { options -> options.copy(avoidAmbiguous = it) }
                }
            }
        }
    }
}

@Composable
private fun PremiumPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PgSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, PgStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun LengthChip(value: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) PgCyan.copy(alpha = 0.14f) else PgSurfaceRaised)
            .border(1.dp, if (selected) PgCyan else PgStroke, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$value",
            color = if (selected) PgCyanBright else PgTextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun OptionToggleRow(symbol: String, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PgSurfaceRaised.copy(alpha = 0.78f))
            .border(1.dp, PgStroke.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF071219))
                .border(1.dp, PgStroke, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = PgCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = PgText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PgCyan,
                uncheckedThumbColor = PgTextMuted,
                uncheckedTrackColor = PgStroke,
                uncheckedBorderColor = PgStroke,
            ),
        )
    }
}

@Composable
internal fun PremiumLockedVault(vaultBusy: Boolean, onUnlockRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.passwdgen_brand_icon),
            contentDescription = null,
            modifier = Modifier.size(124.dp).clip(RoundedCornerShape(30.dp)).shadow(20.dp, RoundedCornerShape(30.dp)),
            contentScale = ContentScale.Crop,
        )
        Text("Sejf jest zablokowany", color = PgText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            "AES-256-GCM · Android Keystore\nDane pozostają lokalnie na urządzeniu",
            color = PgTextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Button(
            onClick = onUnlockRequest,
            enabled = !vaultBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PgCyan, contentColor = Color(0xFF001517)),
        ) {
            if (vaultBusy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF001517), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Odblokuj sejf", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun PremiumVaultToolbar(
    query: String,
    visibleCount: Int,
    totalCount: Int,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextField(
            value = query,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Szukaj", color = PgTextMuted) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = PgTextMuted) },
            trailingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = PgTextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PgSurface,
                unfocusedContainerColor = PgSurface,
                disabledContainerColor = PgSurface,
                focusedIndicatorColor = PgCyan,
                unfocusedIndicatorColor = PgStroke,
                cursorColor = PgCyan,
                focusedTextColor = PgText,
                unfocusedTextColor = PgText,
            ),
        )
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, PgCyan.copy(alpha = 0.78f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PgCyanBright),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Dodaj wpis", fontWeight = FontWeight.SemiBold)
        }
        Text("$visibleCount z $totalCount wpisów", color = PgTextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
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
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PgSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, PgStroke),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServiceMark(entry.service)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.service,
                        color = PgText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.website.isNotBlank()) {
                        Text(
                            entry.website,
                            color = PgTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Zwiń" else "Otwórz",
                    tint = PgCyan,
                )
            }

            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 11.dp), color = PgStroke)
                    CredentialLine("LOGIN", entry.username)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("HASŁO", color = PgCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Text(
                                if (revealed) entry.password else "••••••••••••",
                                color = PgText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onRevealChange(!revealed) }) {
                            Icon(
                                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (revealed) "Ukryj hasło" else "Pokaż hasło",
                                tint = PgTextMuted,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VaultAction(Icons.Outlined.ContentCopy, "Login", onCopyLogin, Modifier.weight(1f))
                        VaultAction(Icons.Outlined.ContentCopy, "Hasło", onCopyPassword, Modifier.weight(1f))
                        VaultAction(Icons.Outlined.Edit, "Edytuj", onEdit, Modifier.weight(1f))
                        VaultAction(Icons.Outlined.DeleteOutline, "Usuń", onDelete, Modifier.weight(1f), danger = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceMark(service: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(PgCyanBright, Color(0xFF1B87DF)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            service.trim().firstOrNull()?.uppercase() ?: "•",
            color = Color(0xFF001517),
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CredentialLine(label: String, value: String) {
    Column {
        Text(label, color = PgCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(value, color = PgText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VaultAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) PgDanger else PgTextMuted),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, maxLines = 1)
    }
}

private fun strengthLabel(bits: Int): String = when {
    bits >= 120 -> "Bardzo mocne"
    bits >= 80 -> "Mocne"
    bits >= 60 -> "Dobre"
    else -> "Podstawowe"
}
