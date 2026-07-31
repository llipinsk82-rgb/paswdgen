#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/blackserv/passwdgen/AppUi.kt")
text = path.read_text(encoding="utf-8")

# Keep the tabbed layout but avoid ColumnScope weight resolution inside when branches
# by wrapping the active tab content in one weighted Box.
old = '''        when (vaultTab) {
            VaultTab.ENTRIES -> {
                PremiumVaultToolbar('''
new = '''        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when (vaultTab) {
                VaultTab.ENTRIES -> Column(Modifier.fillMaxSize()) {
                    PremiumVaultToolbar('''
if text.count(old) != 1:
    raise SystemExit(f"start marker mismatch: {text.count(old)}")
text = text.replace(old, new, 1)

text = text.replace(
    '''                        modifier = Modifier.fillMaxWidth().weight(1f),''',
    '''                        modifier = Modifier.fillMaxSize(),''',
)
text = text.replace(
    '''                        modifier = Modifier.fillMaxWidth().weight(1f),''',
    '''                        modifier = Modifier.fillMaxSize(),''',
)

old_backup = '''            VaultTab.BACKUP -> {
                LazyColumn('''
new_backup = '''                VaultTab.BACKUP -> LazyColumn('''
if text.count(old_backup) != 1:
    raise SystemExit(f"backup marker mismatch: {text.count(old_backup)}")
text = text.replace(old_backup, new_backup, 1)

old_migration = '''            VaultTab.MIGRATION -> {
                LazyColumn('''
new_migration = '''                VaultTab.MIGRATION -> LazyColumn('''
if text.count(old_migration) != 1:
    raise SystemExit(f"migration marker mismatch: {text.count(old_migration)}")
text = text.replace(old_migration, new_migration, 1)

# Close the weighted Box after the tab when block.
old_end = '''            }
        }
    }

    if (creating) {'''
new_end = '''                }
            }
        }
    }

    if (creating) {'''
if text.count(old_end) != 1:
    raise SystemExit(f"end marker mismatch: {text.count(old_end)}")
text = text.replace(old_end, new_end, 1)

path.write_text(text, encoding="utf-8")
