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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
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
        topBar = { TopAppBar(title = { Text("PasswdGen · Secure Vault") }) },
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bezpieczne hasło w kilka sekund", style = MaterialTheme.typography.headlineSmall)
        Text("Generator działa lokalnie, używa SecureRandom i niczego nie wysyła do sieci.")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Text(state.generated.value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                }
                Text(strengthLabel(state.generated.entropyBits))
                Text("Szacowana entropia: ${state.generated.entropyBits} bitów")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onGenerate) { Text("Generuj ponownie") }
                    OutlinedButton(onClick = {
                        copySensitive(context, state.generated.value)
                        onMessage("Hasło skopiowane. Schowek wyczyści się po 60 sekundach.")
                    }) { Text("Kopiuj") }
                }
            }
        }
        Text("Długość hasła", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(12, 16, 20, 24, 32).forEach { preset ->
                if (state.options.length == preset) {
                    Button(onClick = { onOptionsChange { it.copy(length = preset) } }) { Text("$preset") }
                } else {
                    OutlinedButton(onClick = { onOptionsChange { it.copy(length = preset) } }) { Text("$preset") }
                }
            }
        }
        Text("${state.options.length} znaków · zalecane 16–20")
        Slider(
            value = state.options.length.toFloat(),
            onValueChange = { value -> onOptionsChange {
                it.copy(length = value.toInt().coerceIn(PasswordGenerator.MIN_LENGTH, PasswordGenerator.MAX_LENGTH))
            } },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zestawy znaków", style = MaterialTheme.typography.titleMedium)
                OptionRow("Małe litery (a–z)", state.options.lowerCase) { value -> onOptionsChange { it.copy(lowerCase = value) } }
                OptionRow("Wielkie litery (A–Z)", state.options.upperCase) { value -> onOptionsChange { it.copy(upperCase = value) } }
                OptionRow("Cyfry (0–9)", state.options.digits) { value -> onOptionsChange { it.copy(digits = value) } }
                OptionRow("Znaki specjalne", state.options.special) { value -> onOptionsChange { it.copy(special = value) } }
                OptionRow("Pomijaj znaki podobne", state.options.avoidAmbiguous) { value -> onOptionsChange { it.copy(avoidAmbiguous = value) } }
            }
        }
    }
}

private fun strengthLabel(bits: Int): String = when {
    bits >= 120 -> "Bardzo mocne"
    bits >= 80 -> "Mocne"
    bits >= 60 -> "Dobre"
    else -> "Podstawowe"
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked, onCheckedChange)
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
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val filtered = remember(state.entries, state.searchQuery) {
        val q = state.searchQuery.trim().lowercase()
        if (q.isBlank()) state.entries else state.entries.filter {
            it.service.lowercase().contains(q) || it.website.lowercase().contains(q) ||
                it.username.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("${state.entries.size} zapisanych wpisów", modifier = Modifier.padding(top = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                state.searchQuery, onSearchChange,
                label = { Text("Szukaj po nazwie, domenie, loginie lub notatce") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(onClick = { creating = true }) { Text("Dodaj") }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = VaultEntry::id) { entry ->
                    VaultEntryCard(
                        entry, revealed[entry.id] == true, { revealed[entry.id] = it },
                        {
                            copySensitive(context, entry.username)
                            onMessage("Login skopiowany. Schowek wyczyści się po 60 sekundach.")
                        },
                        {
                            copySensitive(context, entry.password)
                            onMessage("Hasło skopiowane. Schowek wyczyści się po 60 sekundach.")
                        },
                        { editing = entry }, { deleting = entry },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
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
private fun VaultEntryCard(
    entry: VaultEntry,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onCopyLogin: () -> Unit,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.service, style = MaterialTheme.typography.titleLarge)
            if (entry.website.isNotBlank()) Text(entry.website, color = MaterialTheme.colorScheme.secondary)
            Text(entry.username, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (revealed) entry.password else "•".repeat(entry.password.length.coerceAtMost(24)),
                fontFamily = FontFamily.Monospace,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pokaż hasło")
                Spacer(Modifier.width(8.dp))
                Switch(revealed, onRevealChange)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopyLogin) { Text("Kopiuj login") }
                Button(onClick = onCopyPassword) { Text("Kopiuj hasło") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edytuj") }
                TextButton(onClick = onDelete) { Text("Usuń") }
            }
        }
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
