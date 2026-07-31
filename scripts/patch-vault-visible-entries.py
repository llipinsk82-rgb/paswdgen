#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/blackserv/passwdgen/AppUi.kt")
text = path.read_text(encoding="utf-8")

old_state = '''    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }'''
new_state = '''    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var toolsExpanded by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }'''
if text.count(old_state) != 1:
    raise SystemExit(f"state marker mismatch: {text.count(old_state)}")
text = text.replace(old_state, new_state, 1)

old_effect_marker = '''    val filtered = remember(state.entries, state.searchQuery) {
        val query = state.searchQuery.trim().lowercase()
        if (query.isBlank()) {
            state.entries
        } else {
            state.entries.filter {
                it.service.lowercase().contains(query) ||
                    it.website.lowercase().contains(query) ||
                    it.username.lowercase().contains(query) ||
                    it.notes.lowercase().contains(query)
            }
        }
    }

    Column('''
new_effect_marker = '''    val filtered = remember(state.entries, state.searchQuery) {
        val query = state.searchQuery.trim().lowercase()
        if (query.isBlank()) {
            state.entries
        } else {
            state.entries.filter {
                it.service.lowercase().contains(query) ||
                    it.website.lowercase().contains(query) ||
                    it.username.lowercase().contains(query) ||
                    it.notes.lowercase().contains(query)
            }
        }
    }

    LaunchedEffect(state.entries) {
        toolsExpanded = false
    }

    Column('''
if text.count(old_effect_marker) != 1:
    raise SystemExit(f"effect marker mismatch: {text.count(old_effect_marker)}")
text = text.replace(old_effect_marker, new_effect_marker, 1)

old_layout = '''        Spacer(Modifier.height(8.dp))
        VaultBackupControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
            scheduledBackup = state.scheduledBackup,
            onSensitiveActionRequest = onSensitiveActionRequest,
            onExternalFlowChanged = onExternalFlowChanged,
        )
        Spacer(Modifier.height(8.dp))
        VaultMigrationControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
            preview = state.csvImportPreview,
            onSensitiveActionRequest = onSensitiveActionRequest,
            onExternalFlowChanged = onExternalFlowChanged,
        )
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.",
                    color = PgTextMuted,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
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
        }'''

new_layout = '''        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Narzędzia sejfu", color = PgTextMuted)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { toolsExpanded = !toolsExpanded }) {
                Text(
                    if (toolsExpanded) "Zwiń" else "Kopia i migracja",
                    color = PgCyan,
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (toolsExpanded) {
                item {
                    VaultBackupControls(
                        viewModel = viewModel,
                        enabled = !state.vaultBusy,
                        scheduledBackup = state.scheduledBackup,
                        onSensitiveActionRequest = onSensitiveActionRequest,
                        onExternalFlowChanged = onExternalFlowChanged,
                    )
                }
                item {
                    VaultMigrationControls(
                        viewModel = viewModel,
                        enabled = !state.vaultBusy,
                        preview = state.csvImportPreview,
                        onSensitiveActionRequest = onSensitiveActionRequest,
                        onExternalFlowChanged = onExternalFlowChanged,
                    )
                }
                item { Spacer(Modifier.height(2.dp)) }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.",
                            color = PgTextMuted,
                        )
                    }
                }
            } else {
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
            }
            item { Spacer(Modifier.height(14.dp)) }
        }'''

if text.count(old_layout) != 1:
    raise SystemExit(f"layout marker mismatch: {text.count(old_layout)}")
text = text.replace(old_layout, new_layout, 1)
path.write_text(text, encoding="utf-8")
