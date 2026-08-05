package com.blackserv.passwdgen

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.security.keystore.UserNotAuthenticatedException
import android.service.autofill.Dataset
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import java.util.Locale
import java.util.concurrent.Executors

class AutofillAuthActivity : FragmentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val target = resolveTarget()
        if (target == null) {
            showError("Android nie przekazał zweryfikowanej witryny ani aplikacji.")
            return
        }

        showLoading(target, "Potwierdź dostęp biometrią lub kodem urządzenia.")
        requestAuthentication(target)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun resolveTarget(): AuthTarget? {
        val domain = AutofillDomainPolicy.normalizeHost(intent.getStringExtra(EXTRA_WEB_DOMAIN))
        if (domain != null) return AuthTarget.Web(domain)

        val requestedPackage = intent.getStringExtra(EXTRA_NATIVE_PACKAGE).orEmpty()
        val identity = NativeAppIdentityResolver.resolve(this, requestedPackage) ?: return null
        return AuthTarget.Native(identity)
    }

    private fun requestAuthentication(
        target: AuthTarget,
        authenticators: Int = ALL_AUTHENTICATORS,
        allowCredentialRetry: Boolean = true,
    ) {
        when (BiometricManager.from(this).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                showError("Skonfiguruj blokadę ekranu lub biometrię w Androidzie.")
                return
            }
            else -> {
                showError("Urządzenie nie udostępnia wymaganego uwierzytelniania.")
                return
            }
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showLoading(target, "Odblokowuję pasujące konta…")
                    loadMatchingEntries(target, allowCredentialRetry)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    returnCanceled()
                }
            },
        )

        val subtitle = if (authenticators == BiometricManager.Authenticators.DEVICE_CREDENTIAL) {
            "Potwierdź kodem urządzenia dostęp do kont dla ${target.displayName}"
        } else {
            "Dostęp do kont dla ${target.displayName}"
        }

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Odblokuj PasswdGen")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private fun loadMatchingEntries(
        target: AuthTarget,
        allowCredentialRetry: Boolean,
    ) {
        executor.execute {
            val result = runCatching {
                val repository = VaultRepository(applicationContext)
                repository.unlockProbe()
                val allEntries = repository.loadAll()
                val matchingEntries = allEntries.filter { entry ->
                    when (target) {
                        is AuthTarget.Web -> AutofillDomainPolicy.matches(entry.website, target.domain)
                        is AuthTarget.Native -> entry.androidApps.any { binding ->
                            NativeAppBindingPolicy.matches(binding, target.identity)
                        }
                    }
                }.sortedWith(entryComparator)
                EntryLookup(allEntries.sortedWith(entryComparator), matchingEntries)
            }

            runOnUiThread {
                result.fold(
                    onSuccess = { lookup -> handleLookupResult(target, lookup) },
                    onFailure = { error ->
                        when {
                            allowCredentialRetry && error.hasCause<UserNotAuthenticatedException>() -> {
                                showLoading(
                                    target,
                                    "Biometria została przyjęta, ale Android Keystore wymaga kodu urządzenia.",
                                )
                                requestAuthentication(
                                    target = target,
                                    authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                                    allowCredentialRetry = false,
                                )
                            }

                            error.hasCause<UserNotAuthenticatedException>() -> showError(
                                "Android Keystore nie zaakceptował uwierzytelnienia. " +
                                    "Zablokuj i odblokuj ekran urządzenia, a następnie spróbuj ponownie.",
                            )

                            else -> showError(error.message ?: "Nie udało się odblokować sejfu.")
                        }
                    },
                )
            }
        }
    }

    private fun handleLookupResult(target: AuthTarget, lookup: EntryLookup) {
        if (target is AuthTarget.Native && lookup.matches.isEmpty()) {
            if (lookup.all.isEmpty()) {
                showError("Sejf nie zawiera żadnego konta do połączenia z aplikacją.")
            } else {
                showNativeLinkChooser(lookup.all, target)
            }
            return
        }

        when (lookup.matches.size) {
            0 -> showError("Nie znaleziono konta przypisanego dokładnie do ${target.displayName}.")
            1 -> returnDataset(lookup.matches.single(), target)
            else -> showAccountChooser(lookup.matches, target)
        }
    }

    private fun showNativeLinkChooser(entries: List<VaultEntry>, target: AuthTarget.Native) {
        val identity = target.identity
        showSearchableChooser(
            title = "Połącz konto z aplikacją",
            subtitleLines = listOf(
                identity.appLabel,
                identity.packageName,
                "Wybierz konto świadomie. PasswdGen zapamięta pakiet oraz certyfikat tej aplikacji.",
            ),
            entries = entries,
            emptyMessage = "Brak kont pasujących do wyszukiwania.",
            onSelect = { entry -> bindNativeAppAndReturn(entry, target) },
        )
    }

    private fun showAccountChooser(entries: List<VaultEntry>, target: AuthTarget) {
        showSearchableChooser(
            title = "Wybierz konto",
            subtitleLines = listOf("Znaleziono ${entries.size} kont dla ${target.displayName}"),
            entries = entries,
            emptyMessage = "Brak kont pasujących do wyszukiwania.",
            onSelect = { entry -> returnDataset(entry, target) },
        )
    }

    private fun showSearchableChooser(
        title: String,
        subtitleLines: List<String>,
        entries: List<VaultEntry>,
        emptyMessage: String,
        onSelect: (VaultEntry) -> Unit,
    ) {
        val content = verticalContainer(Gravity.TOP).apply {
            setPadding(dp(24), dp(32), dp(24), dp(28))
            addView(titleText(title))
            subtitleLines.forEachIndexed { index, line ->
                addView(
                    bodyText(line).apply {
                        textSize = if (index == 1 && subtitleLines.size > 1) 13f else 15f
                    }.withMargins(top = if (index == 0) 10 else 4),
                )
            }
        }

        val search = EditText(this).apply {
            hint = "Szukaj konta"
            setHintTextColor(Color.rgb(139, 156, 170))
            setTextColor(Color.WHITE)
            textSize = 16f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            backgroundTintList = ColorStateList.valueOf(Color.rgb(36, 181, 212))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        content.addView(search.withMargins(top = 18, bottom = 14))

        val resultCount = bodyText("").apply {
            textSize = 13f
            gravity = Gravity.START
        }
        content.addView(resultCount.withMargins(bottom = 10))

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        content.addView(results)
        content.addView(cancelButton().withMargins(top = 12))

        fun render(query: String) {
            val normalizedQuery = query.trim().lowercase(Locale.ROOT)
            val filtered = if (normalizedQuery.isBlank()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.service.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        entry.username.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        entry.website.lowercase(Locale.ROOT).contains(normalizedQuery)
                }
            }

            resultCount.text = "Kont: ${filtered.size} z ${entries.size}"
            results.removeAllViews()
            if (filtered.isEmpty()) {
                results.addView(bodyText(emptyMessage).withMargins(top = 10, bottom = 10))
            } else {
                filtered.forEach { entry ->
                    results.addView(
                        accountButton(entry) { onSelect(entry) }
                            .withMargins(bottom = 10),
                    )
                }
            }
        }

        search.doAfterTextChanged { value -> render(value?.toString().orEmpty()) }
        render("")
        showScrollable(content)
        search.requestFocus()
    }

    private fun bindNativeAppAndReturn(entry: VaultEntry, target: AuthTarget.Native) {
        showLoading(target, "Zapisuję bezpieczne powiązanie z aplikacją…")
        executor.execute {
            val result = runCatching {
                val binding = AndroidAppBinding(
                    packageName = target.identity.packageName,
                    signerSha256 = target.identity.signerSha256,
                )
                val updated = entry.copy(
                    androidApps = entry.androidApps
                        .filterNot { it.packageName == binding.packageName }
                        .plus(binding),
                )
                VaultRepository(applicationContext).save(updated)
                updated
            }
            runOnUiThread {
                result.fold(
                    onSuccess = { updated -> returnDataset(updated, target) },
                    onFailure = { error ->
                        showError(error.message ?: "Nie udało się połączyć konta z aplikacją.")
                    },
                )
            }
        }
    }

    private fun returnDataset(entry: VaultEntry, target: AuthTarget) {
        val usernameId = parcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = parcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        val presentation = accountPresentation(entry.username, target.displayName)
        val builder = Dataset.Builder(presentation)
        var containsField = false

        usernameId?.let {
            builder.setValue(it, AutofillValue.forText(entry.username), presentation)
            containsField = true
        }
        passwordId?.let {
            builder.setValue(it, AutofillValue.forText(entry.password), presentation)
            containsField = true
        }

        if (!containsField) {
            showError("Android nie przekazał pól logowania do uzupełnienia.")
            return
        }

        val reply = Intent().putExtra(
            AutofillManager.EXTRA_AUTHENTICATION_RESULT,
            builder.build(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            reply.putExtra(
                AutofillManager.EXTRA_AUTHENTICATION_RESULT_EPHEMERAL_DATASET,
                true,
            )
        }
        setResult(Activity.RESULT_OK, reply)
        finish()
    }

    private fun accountButton(entry: VaultEntry, onClick: () -> Unit): Button =
        Button(this).apply {
            text = buildString {
                append(entry.service)
                append('\n')
                append(entry.username)
                if (entry.website.isNotBlank()) {
                    append('\n')
                    append(entry.website)
                }
            }
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 33, 45))
            setPadding(dp(18), dp(10), dp(18), dp(10))
            minHeight = dp(72)
            setOnClickListener { onClick() }
        }

    private fun cancelButton(): Button = Button(this).apply {
        text = "Anuluj"
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(55, 65, 76))
        setOnClickListener { returnCanceled() }
    }

    private fun showScrollable(content: LinearLayout) {
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND_COLOR)
                isFillViewport = true
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    private fun showLoading(target: AuthTarget, message: String) {
        val container = verticalContainer(Gravity.CENTER).apply {
            addView(titleText("PasswdGen"))
            addView(
                ProgressBar(this@AutofillAuthActivity).apply {
                    indeterminateTintList = ColorStateList.valueOf(Color.CYAN)
                }.withMargins(top = 28, bottom = 22),
            )
            addView(bodyText(message))
            addView(bodyText(target.displayName).withMargins(top = 8))
            if (target is AuthTarget.Native) {
                addView(
                    bodyText(target.identity.packageName).apply { textSize = 13f }
                        .withMargins(top = 4),
                )
            }
        }
        setContentView(container)
    }

    private fun showError(message: String) {
        val container = verticalContainer(Gravity.CENTER).apply {
            addView(titleText("Nie udało się użyć autouzupełniania"))
            addView(bodyText(message).withMargins(top = 14, bottom = 24))
            addView(
                Button(this@AutofillAuthActivity).apply {
                    text = "Zamknij"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 112, 142))
                    setOnClickListener { returnCanceled() }
                },
            )
        }
        setContentView(container)
    }

    private fun verticalContainer(gravityValue: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = gravityValue
        setPadding(dp(28), dp(32), dp(28), dp(32))
        setBackgroundColor(BACKGROUND_COLOR)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun titleText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 23f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.rgb(205, 216, 226))
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun <T : android.view.View> T.withMargins(
        top: Int = 0,
        bottom: Int = 0,
    ): T = apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun accountPresentation(username: String, target: String): RemoteViews =
        RemoteViews(packageName, R.layout.autofill_presentation).apply {
            setTextViewText(R.id.autofill_primary, username)
            setTextViewText(R.id.autofill_secondary, target)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun returnCanceled() {
        setResult(Activity.RESULT_CANCELED, Intent().putExtras(Bundle.EMPTY))
        finish()
    }

    private fun <T : Parcelable> parcelableExtra(name: String, type: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, type)
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getParcelableExtra<Parcelable>(name) as? T
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

    private sealed interface AuthTarget {
        val displayName: String

        data class Web(val domain: String) : AuthTarget {
            override val displayName: String = domain
        }

        data class Native(val identity: NativeAppIdentity) : AuthTarget {
            override val displayName: String = identity.appLabel
        }
    }

    private data class EntryLookup(
        val all: List<VaultEntry>,
        val matches: List<VaultEntry>,
    )

    internal companion object {
        const val EXTRA_USERNAME_ID = "autofill_username_id"
        const val EXTRA_PASSWORD_ID = "autofill_password_id"
        const val EXTRA_WEB_DOMAIN = "autofill_web_domain"
        const val EXTRA_NATIVE_PACKAGE = "autofill_native_package"
        private val BACKGROUND_COLOR = Color.rgb(6, 14, 23)
        private const val ALL_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        private val entryComparator =
            compareBy<VaultEntry> { it.service.lowercase(Locale.ROOT) }
                .thenBy { it.username.lowercase(Locale.ROOT) }
                .thenByDescending(VaultEntry::updatedAt)
    }
}
