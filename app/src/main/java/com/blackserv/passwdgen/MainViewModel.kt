package com.blackserv.passwdgen

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

internal enum class AppSection { GENERATOR, VAULT }

internal data class AppUiState(
    val section: AppSection = AppSection.GENERATOR,
    val options: PasswordOptions = PasswordOptions(),
    val generated: GeneratedPassword = PasswordGenerator.generate(PasswordOptions()),
    val vaultUnlocked: Boolean = false,
    val vaultBusy: Boolean = false,
    val entries: List<VaultEntry> = emptyList(),
    val searchQuery: String = "",
    val scheduledBackup: ScheduledBackupStatus = ScheduledBackupStatus(),
    val csvImportPreview: CsvImportPreview? = null,
    val message: String? = null,
)

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)
    private val scheduledBackup = ScheduledBackupCoordinator(application)
    private val _state = MutableStateFlow(
        AppUiState(scheduledBackup = scheduledBackup.status()),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var pendingCsvCredentials: List<CsvCredential> = emptyList()

    private val backupStatusListener: SharedPreferences.OnSharedPreferenceChangeListener =
        scheduledBackup.registerStatusListener(::refreshScheduledBackupStatus)

    fun selectSection(section: AppSection) = _state.update { it.copy(section = section) }

    fun updateOptions(transform: (PasswordOptions) -> PasswordOptions) {
        val candidate = transform(_state.value.options)
        if (!candidate.lowerCase && !candidate.upperCase && !candidate.digits && !candidate.special) {
            showMessage("Wybierz co najmniej jeden zestaw znaków.")
            return
        }
        _state.update { it.copy(options = candidate) }
    }

    fun generatePassword() {
        runCatching { PasswordGenerator.generate(_state.value.options) }
            .onSuccess { generated -> _state.update { it.copy(generated = generated) } }
            .onFailure { error -> showMessage(error.message ?: "Nie udało się wygenerować hasła.") }
    }

    fun useGeneratedPassword(): String = _state.value.generated.value

    fun unlockVault() {
        if (_state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.unlockProbe()
                repository.loadAll().also(::refreshScheduledSnapshotSafely)
            }.onSuccess { entries ->
                _state.update {
                    it.copy(
                        vaultUnlocked = true,
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun lockVault() {
        clearPendingCsvImport()
        _state.update {
            it.copy(
                vaultUnlocked = false,
                vaultBusy = false,
                entries = emptyList(),
                searchQuery = "",
                csvImportPreview = null,
            )
        }
    }

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun saveEntry(entry: VaultEntry) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.save(entry)
                repository.loadAll().also(::refreshScheduledSnapshotSafely)
            }.onSuccess { entries ->
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Wpis zapisany.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun deleteEntry(id: String) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.delete(id)
                repository.loadAll().also(::refreshScheduledSnapshotSafely)
            }.onSuccess { entries ->
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Wpis usunięty.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun exportBackup(uri: Uri, passphrase: String) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = repository.loadAll()
                val encoded = VaultBackupCodec.encode(entries, passphrase)
                try {
                    writeDocument(uri, encoded)
                } finally {
                    encoded.fill(0)
                }
                entries.size
            }.onSuccess { count ->
                _state.update {
                    it.copy(vaultBusy = false, message = "Utworzono zaszyfrowaną kopię $count wpisów.")
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun importBackup(uri: Uri, passphrase: String) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val encoded = readDocument(uri, MAX_BACKUP_BYTES, "Plik kopii jest zbyt duży.")
                try {
                    val importedEntries = VaultBackupCodec.decode(encoded, passphrase)
                    val changed = repository.importEntries(importedEntries)
                    val entries = repository.loadAll()
                    refreshScheduledSnapshotSafely(entries)
                    Triple(entries, importedEntries.size, changed)
                } finally {
                    encoded.fill(0)
                }
            }.onSuccess { (entries, total, changed) ->
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Odczytano $total wpisów; dodano lub zaktualizowano $changed.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun configureScheduledBackup(
        treeUri: Uri,
        targetLabel: String,
        passphrase: String,
        wifiOnly: Boolean,
    ) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = repository.loadAll()
                scheduledBackup.configure(
                    treeUri = treeUri,
                    targetLabel = targetLabel,
                    wifiOnly = wifiOnly,
                    passphrase = passphrase,
                    entries = entries,
                )
                entries
            }.onSuccess { entries ->
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Automatyczna kopia została skonfigurowana. Pierwszy zapis został zlecony.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun runScheduledBackupNow() {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = repository.loadAll()
                scheduledBackup.enqueueNow(entries)
            }.onSuccess {
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Zlecono zaszyfrowaną kopię do wybranego folderu.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun disableScheduledBackup() {
        if (_state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { scheduledBackup.disable() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            vaultBusy = false,
                            scheduledBackup = scheduledBackup.status(),
                            message = "Automatyczna kopia została wyłączona.",
                        )
                    }
                }
                .onFailure(::handleVaultError)
        }
    }

    fun previewCsvImport(uri: Uri) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        clearPendingCsvImport()
        _state.update { it.copy(vaultBusy = true, csvImportPreview = null, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val data = readDocument(uri, PasswordCsvCodec.MAX_FILE_BYTES, "Plik CSV jest zbyt duży.")
                try {
                    val decoded = PasswordCsvCodec.decode(data)
                    val existingKeys = repository.loadAll()
                        .mapTo(HashSet()) { PasswordCsvCodec.duplicateKey(it.website, it.username) }
                    val ready = decoded.credentials.filter {
                        PasswordCsvCodec.duplicateKey(it.url, it.username) !in existingKeys
                    }
                    val existingDuplicates = decoded.credentials.size - ready.size
                    pendingCsvCredentials = ready
                    CsvImportPreview(
                        totalRows = decoded.totalRows,
                        readyRows = ready.size,
                        rejectedRows = decoded.rejectedRows,
                        duplicateRows = decoded.duplicateRows + existingDuplicates,
                        formulaLikeFields = decoded.formulaLikeFields,
                    )
                } finally {
                    data.fill(0)
                }
            }.onSuccess { preview ->
                _state.update { it.copy(vaultBusy = false, csvImportPreview = preview) }
            }.onFailure {
                clearPendingCsvImport()
                handleVaultError(it)
            }
        }
    }

    fun confirmCsvImport() {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        val pending = pendingCsvCredentials
        if (pending.isEmpty()) {
            cancelCsvImport()
            return
        }
        pendingCsvCredentials = emptyList()
        _state.update { it.copy(vaultBusy = true, csvImportPreview = null, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val entriesToImport = pending.map { credential ->
                    VaultEntry(
                        service = credential.name,
                        website = credential.url,
                        username = credential.username,
                        password = credential.password,
                        notes = credential.note,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                val changed = repository.importEntries(entriesToImport)
                val entries = repository.loadAll()
                refreshScheduledSnapshotSafely(entries)
                entries to changed
            }.onSuccess { (entries, changed) ->
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        entries = entries,
                        scheduledBackup = scheduledBackup.status(),
                        message = "Zaimportowano $changed rekordów CSV. Usuń jawny plik CSV z urządzenia i chmury.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun cancelCsvImport() {
        clearPendingCsvImport()
        _state.update { it.copy(csvImportPreview = null) }
    }

    fun exportGoogleCsv(uri: Uri) {
        if (!_state.value.vaultUnlocked || _state.value.vaultBusy) return
        _state.update { it.copy(vaultBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = PasswordCsvCodec.encode(repository.loadAll())
                try {
                    writeDocument(uri, result.bytes)
                } finally {
                    result.bytes.fill(0)
                }
                result
            }.onSuccess { result ->
                val skipped = if (result.skippedCount > 0) {
                    " Pominięto ${result.skippedCount} wpisów bez witryny lub ponad limit."
                } else {
                    ""
                }
                val formulaWarning = if (result.formulaLikeFields > 0) {
                    " Wykryto ${result.formulaLikeFields} pól podobnych do formuł — nie otwieraj CSV w arkuszu."
                } else {
                    ""
                }
                _state.update {
                    it.copy(
                        vaultBusy = false,
                        message = "Wyeksportowano ${result.exportedCount} rekordów CSV.$skipped$formulaWarning Usuń CSV po imporcie do Google.",
                    )
                }
            }.onFailure(::handleVaultError)
        }
    }

    fun showMessage(message: String) = _state.update { it.copy(message = message) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        clearPendingCsvImport()
        scheduledBackup.unregisterStatusListener(backupStatusListener)
        super.onCleared()
    }

    private fun clearPendingCsvImport() {
        pendingCsvCredentials = emptyList()
    }

    private fun refreshScheduledSnapshotSafely(entries: List<VaultEntry>) {
        if (!scheduledBackup.status().configured) return
        runCatching { scheduledBackup.refreshSnapshotIfConfigured(entries) }
    }

    private fun refreshScheduledBackupStatus() {
        _state.update { it.copy(scheduledBackup = scheduledBackup.status()) }
    }

    private fun writeDocument(uri: Uri, data: ByteArray) {
        val resolver = getApplication<Application>().contentResolver
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(data)
            output.flush()
        } ?: error("Nie udało się otworzyć dokumentu do zapisu.")
    }

    private fun readDocument(uri: Uri, limit: Int, sizeError: String): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: error("Nie udało się otworzyć wybranego dokumentu.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { sizeError }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun handleVaultError(error: Throwable) {
        when {
            error.hasCause<UserNotAuthenticatedException>() -> _state.update {
                clearPendingCsvImport()
                it.copy(
                    vaultUnlocked = false,
                    vaultBusy = false,
                    entries = emptyList(),
                    csvImportPreview = null,
                    scheduledBackup = scheduledBackup.status(),
                    message = "Sejf jest zablokowany. Uwierzytelnij się ponownie.",
                )
            }

            error.hasCause<KeyPermanentlyInvalidatedException>() -> _state.update {
                clearPendingCsvImport()
                it.copy(
                    vaultUnlocked = false,
                    vaultBusy = false,
                    entries = emptyList(),
                    csvImportPreview = null,
                    scheduledBackup = scheduledBackup.status(),
                    message = "Klucz sejfu został unieważniony przez zmianę zabezpieczeń urządzenia. Nie zapisuj nowych danych i skontaktuj się z pomocą.",
                )
            }

            else -> _state.update {
                it.copy(
                    vaultBusy = false,
                    scheduledBackup = scheduledBackup.status(),
                    message = error.message ?: "Błąd sejfu.",
                )
            }
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
    }
}
