#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/blackserv/passwdgen/AppUi.kt")
text = path.read_text(encoding="utf-8")

old_state = '''    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var toolsExpanded by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }'''
new_state = '''    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var vaultTab by remember { mutableStateOf(VaultTab.ENTRIES) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }'''
if text.count(old_state) != 1:
    raise SystemExit(f"state marker mismatch: {text.count(old_state)}")
text = text.replace(old_state, new_state, 1)

old_effect = '''    LaunchedEffect(state.entries) {
        toolsExpanded = false
    }

'''
new_effect = '''    LaunchedEffect(state.entries) {
        if (state.entries.isNotEmpty()) vaultTab = VaultTab.ENTRIES
    }

'''
if text.count(old_effect) != 1:
    raise SystemExit(f"effect marker mismatch: {text.count(old_effect)}")
text = text.replace(old_effect, new_effect, 1)

start = text.index('''        PremiumVaultToolbar(
            query = state.searchQuery,''')
end_marker = '''            item { Spacer(Modifier.height(14.dp)) }
        }'''
end = text.index(end_marker, start) + len(end_marker)

replacement = '''        VaultTabBar(
            selected = vaultTab,
            onSelect = { vaultTab = it },
        )
        Spacer(Modifier.height(8.dp))

        when (vaultTab) {
            VaultTab.ENTRIES -> {
                PremiumVaultToolbar(
                    query = state.searchQuery,
                    visibleCount = filtered.size,
                    totalCount = state.entries.size,
                    onSearchChange = onSearchChange,
                    onAdd = { creating = true },
                )
                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.",
                            color = PgTextMuted,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        items(filtered, key = VaultEntry::id) { entry ->
                            PremiumVaultRow(
                                entry = entry,
                                expanded = expanded[entry.id] == true,
                                revealed = revealed[entry.id] == true,
                                onToggleExpanded = { expanded[entry.id] = expanded[entry.id] != true },
                                onRevealChange = { isRevealed -> revealed[entry.id] = isRevealed },
                                onCopyLogin = {
                                    copySensitive(context, entry.username)
                                    onMessage("Login skopiowany. Schowek wyczyści się po 60 sekundach.")
                                },
                                onCopyPassword = {
                                    copySensitive(context, entry.password)
                                    onMessage("Hasło skopiowane. Schowek wyczyści się po 60 sekundach.")
                                },
                                onEdit = { editing = entry },
                                onDelete = { deleting = entry },
                            )
                        }
                        item { Spacer(Modifier.height(14.dp)) }
                    }
                }
            }

            VaultTab.BACKUP -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    item {
                        VaultBackupControls(
                            viewModel = viewModel,
                            enabled = !state.vaultBusy,
                            scheduledBackup = state.scheduledBackup,
                            onSensitiveActionRequest = onSensitiveActionRequest,
                            onExternalFlowChanged = onExternalFlowChanged,
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }
            }

            VaultTab.MIGRATION -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    item {
                        VaultMigrationControls(
                            viewModel = viewModel,
                            enabled = !state.vaultBusy,
                            preview = state.csvImportPreview,
                            onSensitiveActionRequest = onSensitiveActionRequest,
                            onExternalFlowChanged = onExternalFlowChanged,
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }
            }
        }'''
text = text[:start] + replacement + text[end:]

insert_marker = '''@Composable
private fun EntryDialog('''
helper = '''private enum class VaultTab { ENTRIES, BACKUP, MIGRATION }

@Composable
private fun VaultTabBar(
    selected: VaultTab,
    onSelect: (VaultTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            VaultTab.ENTRIES to "Wpisy",
            VaultTab.BACKUP to "Kopia",
            VaultTab.MIGRATION to "Migracja",
        ).forEach { (tab, label) ->
            Button(
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == tab) PgCyan else PgSurface.copy(alpha = 0.75f),
                    contentColor = if (selected == tab) Color(0xFF001517) else PgTextMuted,
                ),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun EntryDialog('''
if text.count(insert_marker) != 1:
    raise SystemExit(f"helper marker mismatch: {text.count(insert_marker)}")
text = text.replace(insert_marker, helper, 1)

path.write_text(text, encoding="utf-8")
