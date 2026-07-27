package com.blackserv.passwdgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PasswdGenApp(
    viewModel: MainViewModel,
    onUnlockRequest: () -> Unit,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    PremiumAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { PremiumTopBar(state.section) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                PremiumBottomBar(
                    section = state.section,
                    onSelect = viewModel::selectSection,
                )
            },
        ) { padding ->
            when (state.section) {
                AppSection.GENERATOR -> GeneratorScreen(
                    state = state,
                    onOptionsChange = viewModel::updateOptions,
                    onGenerate = viewModel::generatePassword,
                    onMessage = viewModel::showMessage,
                    modifier = Modifier.padding(padding),
                )
                AppSection.VAULT -> VaultScreen(
                    viewModel = viewModel,
                    state = state,
                    onUnlockRequest = onUnlockRequest,
                    onSensitiveActionRequest = onSensitiveActionRequest,
                    onExternalFlowChanged = onExternalFlowChanged,
                    onSearchChange = viewModel::setSearchQuery,
                    onSave = viewModel::saveEntry,
                    onDelete = viewModel::deleteEntry,
                    onUseGenerated = viewModel::useGeneratedPassword,
                    onMessage = viewModel::showMessage,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun GeneratorScreen(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
    onGenerate: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        PremiumGeneratorContent(
            state = state,
            onOptionsChange = onOptionsChange,
            onGenerate = onGenerate,
            onCopy = {
                copySensitive(context, state.generated.value)
                onMessage("Hasło skopiowane. Schowek wyczyści się po 60 sekundach.")
            },
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun VaultScreen(
    viewModel: MainViewModel,
    state: AppUiState,
    onUnlockRequest: () -> Unit,
    onSensitiveActionRequest: (title: String, action: () -> Unit) -> Unit,
    onExternalFlowChanged: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSave: (VaultEntry) -> Unit,
    onDelete: (String) -> Unit,
    onUseGenerated: () -> String,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.vaultUnlocked) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PremiumLockedVault(
                vaultBusy = state.vaultBusy,
                onUnlockRequest = onUnlockRequest,
            )
        }
        return
    }

    val context = LocalContext.current
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val filtered = remember(state.entries, state.searchQuery) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
    ) {
        PremiumVaultToolbar(
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
        }
    }

    if (creating) {
        EntryDialog(
            initial = null,
            generatedPassword = onUseGenerated,
            onDismiss = { creating = false },
            onSave = {
                creating = false
                onSave(it)
            },
        )
    }

    editing?.let { entry ->
        EntryDialog(
            initial = entry,
            generatedPassword = onUseGenerated,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                onSave(it)
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = PgSurface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Usunąć wpis?", color = PgText) },
            text = { Text("Wpis „${entry.service}” zostanie trwale usunięty.", color = PgTextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = null
                        onDelete(entry.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PgDanger),
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Anuluj", color = PgTextMuted)
                }
            },
        )
    }
}

@Composable
private fun EntryDialog(
    initial: VaultEntry?,
    generatedPassword: () -> String,
    onDismiss: () -> Unit,
    onSave: (VaultEntry) -> Unit,
) {
    var service by remember(initial?.id) { mutableStateOf(initial?.service.orEmpty()) }
    var website by remember(initial?.id) { mutableStateOf(initial?.website.orEmpty()) }
    var username by remember(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial?.id) { mutableStateOf(initial?.password.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var visible by remember(initial?.id) { mutableStateOf(false) }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PgSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (initial == null) "Nowy wpis" else "Edytuj wpis",
                color = PgText,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PremiumTextField(service, { service = it }, "Nazwa usługi*")
                PremiumTextField(website, { website = it }, "Adres witryny")
                PremiumTextField(username, { username = it }, "Login / e-mail*")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Ukryj hasło" else "Pokaż hasło",
                            )
                        }
                    },
                    colors = premiumOutlinedFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokaż", color = PgTextMuted)
                    Switch(
                        checked = visible,
                        onCheckedChange = { visible = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PgCyan),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { password = generatedPassword() }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = PgCyan)
                        Spacer(Modifier.width(5.dp))
                        Text("Użyj generatora", color = PgCyan)
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notatki") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = premiumOutlinedFieldColors(),
                )
                error?.let { Text(it, color = PgDanger) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = when {
                        service.isBlank() -> "Podaj nazwę usługi."
                        username.isBlank() -> "Podaj login."
                        password.isBlank() -> "Podaj hasło."
                        else -> null
                    }
                    if (error == null) {
                        onSave(
                            (initial ?: VaultEntry(
                                service = service,
                                username = username,
                                password = password,
                            )).copy(
                                service = service,
                                website = website,
                                username = username,
                                password = password,
                                notes = notes,
                            ),
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PgCyan, contentColor = Color(0xFF001517)),
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = PgTextMuted)
            }
        },
    )
}

@Composable
private fun PremiumTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = premiumOutlinedFieldColors(),
    )
}

@Composable
private fun premiumOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PgText,
    unfocusedTextColor = PgText,
    focusedBorderColor = PgCyan,
    unfocusedBorderColor = PgStrokeStrong,
    focusedLabelColor = PgCyan,
    unfocusedLabelColor = PgTextMuted,
    cursorColor = PgCyan,
    focusedTrailingIconColor = PgCyan,
    unfocusedTrailingIconColor = PgTextMuted,
)

private fun copySensitive(context: Context, value: String) {
    val appContext = context.applicationContext
    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("dane logowania", value).apply {
        description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
        if (current == value) clipboard.clearPrimaryClip()
    }, 60_000)
}
