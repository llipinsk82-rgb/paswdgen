#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/blackserv/passwdgen/AppUi.kt")
text = path.read_text(encoding="utf-8")

old_call = """                AppSection.VAULT -> VaultScreen(
                    state = state,"""
new_call = """                AppSection.VAULT -> VaultScreen(
                    viewModel = viewModel,
                    state = state,"""
if text.count(old_call) != 1:
    raise SystemExit(f"VaultScreen call marker count: {text.count(old_call)}")
text = text.replace(old_call, new_call, 1)

old_signature = """private fun VaultScreen(
    state: AppUiState,"""
new_signature = """private fun VaultScreen(
    viewModel: MainViewModel,
    state: AppUiState,"""
if text.count(old_signature) != 1:
    raise SystemExit(f"VaultScreen signature marker count: {text.count(old_signature)}")
text = text.replace(old_signature, new_signature, 1)

old_toolbar = """        PremiumVaultToolbar(
            query = state.searchQuery,
            visibleCount = filtered.size,
            totalCount = state.entries.size,
            onSearchChange = onSearchChange,
            onAdd = { creating = true },
        )
        Spacer(Modifier.height(10.dp))"""
new_toolbar = """        PremiumVaultToolbar(
            query = state.searchQuery,
            visibleCount = filtered.size,
            totalCount = state.entries.size,
            onSearchChange = onSearchChange,
            onAdd = { creating = true },
        )
        Spacer(Modifier.height(8.dp))
        VaultBackupControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
        )
        Spacer(Modifier.height(10.dp))"""
if text.count(old_toolbar) != 1:
    raise SystemExit(f"Vault toolbar marker count: {text.count(old_toolbar)}")
text = text.replace(old_toolbar, new_toolbar, 1)

path.write_text(text, encoding="utf-8")
