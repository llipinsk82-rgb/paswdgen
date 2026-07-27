#!/usr/bin/env python3
from pathlib import Path

app_path = Path("app/src/main/java/com/blackserv/passwdgen/AppUi.kt")
app = app_path.read_text(encoding="utf-8")

old = "internal fun PasswdGenApp(viewModel: MainViewModel, onUnlockRequest: () -> Unit) {"
new = '''internal fun PasswdGenApp(
    viewModel: MainViewModel,
    onUnlockRequest: () -> Unit,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
) {'''
if app.count(old) != 1:
    raise SystemExit(f"PasswdGenApp signature marker: {app.count(old)}")
app = app.replace(old, new, 1)

old = '''                    onUnlockRequest = onUnlockRequest,
                    onSearchChange = viewModel::setSearchQuery,'''
new = '''                    onUnlockRequest = onUnlockRequest,
                    onSensitiveActionRequest = onSensitiveActionRequest,
                    onExternalFlowChanged = onExternalFlowChanged,
                    onSearchChange = viewModel::setSearchQuery,'''
if app.count(old) != 1:
    raise SystemExit(f"VaultScreen call marker: {app.count(old)}")
app = app.replace(old, new, 1)

old = '''    state: AppUiState,
    onUnlockRequest: () -> Unit,
    onSearchChange: (String) -> Unit,'''
new = '''    state: AppUiState,
    onUnlockRequest: () -> Unit,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,'''
if app.count(old) != 1:
    raise SystemExit(f"VaultScreen signature marker: {app.count(old)}")
app = app.replace(old, new, 1)

old = '''        VaultBackupControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
            scheduledBackup = state.scheduledBackup,
        )
        Spacer(Modifier.height(10.dp))'''
new = '''        VaultBackupControls(
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
        Spacer(Modifier.height(10.dp))'''
if app.count(old) != 1:
    raise SystemExit(f"Backup controls marker: {app.count(old)}")
app = app.replace(old, new, 1)
app_path.write_text(app, encoding="utf-8")

backup_path = Path("app/src/main/java/com/blackserv/passwdgen/BackupUi.kt")
backup = backup_path.read_text(encoding="utf-8")

old = '''internal fun VaultBackupControls(
    viewModel: MainViewModel,
    enabled: Boolean,
    scheduledBackup: ScheduledBackupStatus,
) {'''
new = '''internal fun VaultBackupControls(
    viewModel: MainViewModel,
    enabled: Boolean,
    scheduledBackup: ScheduledBackupStatus,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
) {'''
if backup.count(old) != 1:
    raise SystemExit(f"VaultBackupControls signature marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''    ) { uri ->
        val secret = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && secret != null) viewModel.exportBackup(uri, secret)
    }'''
new = '''    ) { uri ->
        onExternalFlowChanged(false)
        val secret = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && secret != null) viewModel.exportBackup(uri, secret)
    }'''
if backup.count(old) != 1:
    raise SystemExit(f"createDocument callback marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''    ) { uri ->
        if (uri != null) {
            importUri = uri'''
new = '''    ) { uri ->
        onExternalFlowChanged(false)
        if (uri != null) {
            importUri = uri'''
if backup.count(old) != 1:
    raise SystemExit(f"openDocument callback marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''    ) { uri ->
        val secret = pendingSchedulePassphrase
        val wifiOnly = pendingScheduleWifiOnly'''
new = '''    ) { uri ->
        onExternalFlowChanged(false)
        val secret = pendingSchedulePassphrase
        val wifiOnly = pendingScheduleWifiOnly'''
if backup.count(old) != 1:
    raise SystemExit(f"folder callback marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''                    onClick = {
                        openDocument.launch(arrayOf(VaultBackupCodec.MIME_TYPE, "application/octet-stream"))
                    },'''
new = '''                    onClick = {
                        onSensitiveActionRequest("Import zaszyfrowanej kopii") {
                            onExternalFlowChanged(true)
                            runCatching {
                                openDocument.launch(arrayOf(VaultBackupCodec.MIME_TYPE, "application/octet-stream"))
                            }.onFailure {
                                onExternalFlowChanged(false)
                                viewModel.showMessage("Nie udało się otworzyć selektora plików.")
                            }
                        }
                    },'''
if backup.count(old) != 1:
    raise SystemExit(f"manual import launch marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''                        pendingExportPassphrase = passphrase
                        passphrase = ""
                        confirmation = ""
                        showExportDialog = false
                        createDocument.launch(defaultBackupName())'''
new = '''                        pendingExportPassphrase = passphrase
                        passphrase = ""
                        confirmation = ""
                        showExportDialog = false
                        onSensitiveActionRequest("Eksport zaszyfrowanej kopii") {
                            onExternalFlowChanged(true)
                            runCatching { createDocument.launch(defaultBackupName()) }
                                .onFailure {
                                    pendingExportPassphrase = null
                                    onExternalFlowChanged(false)
                                    viewModel.showMessage("Nie udało się otworzyć selektora plików.")
                                }
                        }'''
if backup.count(old) != 1:
    raise SystemExit(f"manual export launch marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)

old = '''                        pendingSchedulePassphrase = schedulePassphrase
                        pendingScheduleWifiOnly = scheduleWifiOnly
                        schedulePassphrase = ""
                        scheduleConfirmation = ""
                        showScheduleDialog = false
                        chooseBackupFolder.launch(null)'''
new = '''                        pendingSchedulePassphrase = schedulePassphrase
                        pendingScheduleWifiOnly = scheduleWifiOnly
                        schedulePassphrase = ""
                        scheduleConfirmation = ""
                        showScheduleDialog = false
                        onSensitiveActionRequest("Konfiguracja automatycznej kopii") {
                            onExternalFlowChanged(true)
                            runCatching { chooseBackupFolder.launch(null) }
                                .onFailure {
                                    pendingSchedulePassphrase = null
                                    pendingScheduleWifiOnly = true
                                    onExternalFlowChanged(false)
                                    viewModel.showMessage("Nie udało się otworzyć wyboru folderu.")
                                }
                        }'''
if backup.count(old) != 1:
    raise SystemExit(f"schedule folder launch marker: {backup.count(old)}")
backup = backup.replace(old, new, 1)
backup_path.write_text(backup, encoding="utf-8")
