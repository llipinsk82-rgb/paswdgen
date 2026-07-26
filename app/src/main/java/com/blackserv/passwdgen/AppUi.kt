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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PasswdGenApp(viewModel: MainViewModel, onUnlockRequest: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("PasswdGen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                NavigationBarItem(
                    selected = state.section == AppSection.GENERATOR,
                    onClick = { viewModel.selectSection(AppSection.GENERATOR) },
                    icon = { Text("✦") }, label = { Text("Generator") },
                )
                NavigationBarItem(
                    selected = state.section == AppSection.VAULT,
                    onClick = { viewModel.selectSection(AppSection.VAULT) },
                    icon = { Text("▣") }, label = { Text("Sejf") },
                )
            }
        },
    ) { padding ->
        when (state.section) {
            AppSection.GENERATOR -> GeneratorScreen(
                state, viewModel::updateOptions, viewModel::generatePassword,
                viewModel::showMessage, Modifier.padding(padding),
            )
            AppSection.VAULT -> VaultScreen(
                state, onUnlockRequest, viewModel::setSearchQuery, viewModel::saveEntry,
                viewModel::deleteEntry, viewModel::useGeneratedPassword,
                viewModel::showMessage, Modifier.padding(padding),
            )
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
    state: AppUiState,
    onUnlockRequest: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSave: (VaultEntry) -> Unit,
    onDelete: (String) -> Unit,
    onUseGenerated: () -> String,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.vaultUnlocked) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(24.dp)) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Sejf jest zablokowany", style = MaterialTheme.typography.headlineSmall)
                    Text("AES-256-GCM · Android Keystore · lokalnie na urządzeniu")
                    Button(onClick = onUnlockRequest, enabled = !state.vaultBusy) {
                        if (state.vaultBusy) CircularProgressIndicator(Modifier.width(20.dp)) else Text("Odblokuj sejf")
                    }
                }
            }
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
        val q = state.searchQuery.trim().lowercase()
        if (q.isBlank()) state.entries else state.entries.filter {
            it.service.lowercase().contains(q) || it.website.lowercase().contains(q) ||
                it.username.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Szukaj w sejfie") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { creating = true }) { Text("+") }
        }
        Text(
            "${filtered.size} z ${state.entries.size} wpisów",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
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
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (creating) EntryDialog(null, onUseGenerated, { creating = false }) { creating = false; onSave(it) }
    editing?.let { entry -> EntryDialog(entry, onUseGenerated, { editing = null }) { editing = null; onSave(it) } }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Usunąć wpis?") },
            text = { Text("Wpis „${entry.service}” zostanie trwale usunięty.") },
            confirmButton = { Button(onClick = { deleting = null; onDelete(entry.id) }) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Anuluj") } },
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
        title = { Text(if (initial == null) "Nowy wpis" else "Edytuj wpis") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(service, { service = it }, label = { Text("Nazwa usługi*") }, singleLine = true)
                OutlinedTextField(website, { website = it }, label = { Text("Adres witryny") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Login / e-mail*") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, label = { Text("Hasło*") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokaż")
                    Switch(visible, { visible = it })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { password = generatedPassword() }) { Text("Użyj wygenerowanego") }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notatki") }, minLines = 2, maxLines = 5)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when {
                    service.isBlank() -> "Podaj nazwę usługi."
                    username.isBlank() -> "Podaj login."
                    password.isBlank() -> "Podaj hasło."
                    else -> null
                }
                if (error == null) onSave(
                    (initial ?: VaultEntry(service = service, username = username, password = password)).copy(
                        service = service, website = website, username = username, password = password, notes = notes,
                    ),
                )
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

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
        val current = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(appContext)?.toString()
        if (current == value) clipboard.clearPrimaryClip()
    }, 60_000)
}
