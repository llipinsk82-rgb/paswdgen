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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
internal fun PasswdGenApp(
    viewModel: MainViewModel,
    onUnlockRequest: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("PasswdGen") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                NavigationBarItem(
                    selected = state.section == AppSection.GENERATOR,
                    onClick = { viewModel.selectSection(AppSection.GENERATOR) },
                    icon = { Text("Aa") },
                    label = { Text("Generator") },
                )
                NavigationBarItem(
                    selected = state.section == AppSection.VAULT,
                    onClick = { viewModel.selectSection(AppSection.VAULT) },
                    icon = { Text("▣") },
                    label = { Text("Sejf") },
                )
            }
        },
    ) { innerPadding ->
        when (state.section) {
            AppSection.GENERATOR -> GeneratorScreen(
                state = state,
                onOptionsChange = viewModel::updateOptions,
                onGenerate = viewModel::generatePassword,
                onMessage = viewModel::showMessage,
                modifier = Modifier.padding(innerPadding),
            )
            AppSection.VAULT -> VaultScreen(
                state = state,
                onUnlockRequest = onUnlockRequest,
                onSearchChange = viewModel::setSearchQuery,
                onSave = viewModel::saveEntry,
                onDelete = viewModel::deleteEntry,
                onUseGenerated = viewModel::useGeneratedPassword,
                onMessage = viewModel::showMessage,
                modifier = Modifier.padding(innerPadding),
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Generator kryptograficzny", style = MaterialTheme.typography.headlineSmall)
        Text("Losowanie używa SecureRandom i gwarantuje co najmniej jeden znak z każdego wybranego zestawu.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = state.generated.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text("Szacowana entropia: ${state.generated.entropyBits} bitów")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGenerate) { Text("Generuj") }
                    OutlinedButton(
                        onClick = {
                            copySensitive(context, state.generated.value)
                            onMessage("Hasło skopiowane. Schowek zostanie wyczyszczony po 60 sekundach.")
                        },
                    ) { Text("Kopiuj") }
                }
            }
        }

        Text("Długość: ${state.options.length}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.options.length.toFloat(),
            onValueChange = { value ->
                onOptionsChange { it.copy(length = value.toInt().coerceIn(8, 128)) }
            },
            valueRange = 8f..128f,
            steps = 119,
        )

        OptionRow("Małe litery (a–z)", state.options.lowerCase) {
            onOptionsChange { options -> options.copy(lowerCase = it) }
        }
        OptionRow("Wielkie litery (A–Z)", state.options.upperCase) {
            onOptionsChange { options -> options.copy(upperCase = it) }
        }
        OptionRow("Cyfry (0–9)", state.options.digits) {
            onOptionsChange { options -> options.copy(digits = it) }
        }
        OptionRow("Znaki specjalne", state.options.special) {
            onOptionsChange { options -> options.copy(special = it) }
        }
        OptionRow("Pomijaj znaki podobne, np. I/l/1/O/0", state.options.avoidAmbiguous) {
            onOptionsChange { options -> options.copy(avoidAmbiguous = it) }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
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
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Sejf jest zablokowany", style = MaterialTheme.typography.headlineSmall)
                Text("Dane są szyfrowane lokalnie kluczem chronionym przez Android Keystore.")
                Button(onClick = onUnlockRequest, enabled = !state.vaultBusy) {
                    if (state.vaultBusy) CircularProgressIndicator(modifier = Modifier.width(20.dp))
                    else Text("Odblokuj")
                }
            }
        }
        return
    }

    val context = LocalContext.current
    var editingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var creatingEntry by remember { mutableStateOf(false) }
    var deletingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }

    val filteredEntries = remember(state.entries, state.searchQuery) {
        val query = state.searchQuery.trim().lowercase()
        if (query.isBlank()) state.entries
        else state.entries.filter {
            it.service.lowercase().contains(query) ||
                it.website.lowercase().contains(query) ||
                it.username.lowercase().contains(query)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                label = { Text("Szukaj lokalnie") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { creatingEntry = true }) { Text("Dodaj") }
        }

        if (filteredEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.entries.isEmpty()) "Sejf jest pusty." else "Brak pasujących wpisów.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredEntries, key = VaultEntry::id) { entry ->
                    VaultEntryCard(
                        entry = entry,
                        revealed = revealed[entry.id] == true,
                        onRevealChange = { revealed[entry.id] = it },
                        onCopyLogin = {
                            copySensitive(context, entry.username)
                            onMessage("Login skopiowany.")
                        },
                        onCopyPassword = {
                            copySensitive(context, entry.password)
                            onMessage("Hasło skopiowane. Schowek zostanie wyczyszczony po 60 sekundach.")
                        },
                        onEdit = { editingEntry = entry },
                        onDelete = { deletingEntry = entry },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (creatingEntry) {
        EntryDialog(
            initial = null,
            generatedPassword = onUseGenerated,
            onDismiss = { creatingEntry = false },
            onSave = {
                creatingEntry = false
                onSave(it)
            },
        )
    }

    editingEntry?.let { entry ->
        EntryDialog(
            initial = entry,
            generatedPassword = onUseGenerated,
            onDismiss = { editingEntry = null },
            onSave = {
                editingEntry = null
                onSave(it)
            },
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Usunąć wpis?") },
            text = { Text("Wpis „${entry.service}” zostanie trwale usunięty z urządzenia.") },
            confirmButton = {
                Button(
                    onClick = {
                        deletingEntry = null
                        onDelete(entry.id)
                    },
                ) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Anuluj") }
            },
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.service, style = MaterialTheme.typography.titleLarge)
            if (entry.website.isNotBlank()) Text(entry.website)
            Text("Login: ${entry.username}")
            Text(
                text = if (revealed) entry.password else "•".repeat(entry.password.length.coerceAtMost(24)),
                fontFamily = FontFamily.Monospace,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pokaż hasło")
                Spacer(Modifier.width(8.dp))
                Switch(checked = revealed, onCheckedChange = onRevealChange)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopyLogin) { Text("Kopiuj login") }
                TextButton(onClick = onCopyPassword) { Text("Kopiuj hasło") }
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
    var passwordVisible by remember(initial?.id) { mutableStateOf(false) }
    var validationMessage by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowy wpis" else "Edytuj wpis") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(service, { service = it }, label = { Text("Usługa / witryna*") }, singleLine = true)
                OutlinedTextField(website, { website = it }, label = { Text("Adres strony") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Login / e-mail*") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło*") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokaż")
                    Switch(checked = passwordVisible, onCheckedChange = { passwordVisible = it })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { password = generatedPassword() }) { Text("Użyj wygenerowanego") }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notatki") },
                    minLines = 2,
                    maxLines = 5,
                )
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    validationMessage = when {
                        service.isBlank() -> "Podaj nazwę usługi."
                        username.isBlank() -> "Podaj login."
                        password.isBlank() -> "Podaj hasło."
                        else -> null
                    }
                    if (validationMessage == null) {
                        onSave(
                            (initial ?: VaultEntry(service = service, username = username, password = password)).copy(
                                service = service,
                                website = website,
                                username = username,
                                password = password,
                                notes = notes,
                            ),
                        )
                    }
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

private fun copySensitive(
    context: Context,
    value: String,
) {
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
