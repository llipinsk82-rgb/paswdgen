package com.blackserv.passwdgen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PremiumVaultHostGroup(
    group: VaultHostGroup,
    expanded: Boolean,
    isRevealed: (String) -> Boolean,
    onToggleExpanded: () -> Unit,
    onRevealChange: (String, Boolean) -> Unit,
    onCopyLogin: (VaultEntry) -> Unit,
    onCopyPassword: (VaultEntry) -> Unit,
    onEdit: (VaultEntry) -> Unit,
    onDelete: (VaultEntry) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = PgCyan,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PgCyan.copy(alpha = 0.10f))
                    .border(1.dp, PgCyan.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
                    .padding(7.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = PgText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = accountCountLabel(group.entries.size),
                    color = PgTextMuted,
                    fontSize = 11.sp,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Zwiń konta" else "Rozwiń konta",
                tint = PgTextMuted,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = PgStroke)
                group.entries.forEachIndexed { index, entry ->
                    VaultAccountRow(
                        entry = entry,
                        revealed = isRevealed(entry.id),
                        onRevealChange = { onRevealChange(entry.id, it) },
                        onCopyLogin = { onCopyLogin(entry) },
                        onCopyPassword = { onCopyPassword(entry) },
                        onEdit = { onEdit(entry) },
                        onDelete = { onDelete(entry) },
                    )
                    if (index != group.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = PgStroke.copy(alpha = 0.72f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultAccountRow(
    entry: VaultEntry,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onCopyLogin: () -> Unit,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.username,
                    color = PgText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (revealed) entry.password else "••••••••••••",
                    color = if (revealed) PgText else PgTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SmallAction(Icons.Outlined.ContentCopy, "Kopiuj login", onCopyLogin)
            SmallAction(
                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (revealed) "Ukryj hasło" else "Pokaż hasło",
            ) { onRevealChange(!revealed) }
            SmallAction(Icons.Outlined.ContentCopy, "Kopiuj hasło", onCopyPassword)
            SmallAction(Icons.Outlined.Edit, "Edytuj konto", onEdit)
            SmallAction(Icons.Outlined.DeleteOutline, "Usuń konto", onDelete, danger = true)
        }
        if (entry.service.isNotBlank() && entry.service != entry.website) {
            Text(
                text = entry.service,
                color = PgTextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (danger) PgDanger else PgTextMuted,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun accountCountLabel(count: Int): String = when {
    count == 1 -> "1 konto"
    count in 2..4 -> "$count konta"
    else -> "$count kont"
}
