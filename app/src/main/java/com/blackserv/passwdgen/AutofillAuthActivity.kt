package com.blackserv.passwdgen

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.autofill.Dataset
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.Locale
import java.util.concurrent.Executors

class AutofillAuthActivity : FragmentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val domain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        if (domain.isNullOrBlank()) {
            showError("Brak zweryfikowanej domeny formularza.")
            return
        }

        showLoading(domain, "Potwierdź dostęp biometrią lub kodem urządzenia.")
        requestAuthentication(domain)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestAuthentication(domain: String) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

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
                    showLoading(domain, "Odblokowuję konta dla $domain…")
                    loadMatchingEntries(domain)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    returnCanceled()
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Odblokuj PasswdGen")
                .setSubtitle("Dostęp do kont dla $domain")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private fun loadMatchingEntries(domain: String) {
        executor.execute {
            val result = runCatching {
                VaultRepository(applicationContext)
                    .loadAll()
                    .filter { AutofillDomainPolicy.matches(it.website, domain) }
                    .sortedWith(
                        compareBy<VaultEntry> { it.username.lowercase(Locale.ROOT) }
                            .thenByDescending(VaultEntry::updatedAt),
                    )
            }

            runOnUiThread {
                result.fold(
                    onSuccess = { entries ->
                        when (entries.size) {
                            0 -> showError(
                                "Nie znaleziono konta przypisanego dokładnie do domeny $domain.",
                            )
                            1 -> returnDataset(entries.single(), domain)
                            else -> showAccountChooser(entries, domain)
                        }
                    },
                    onFailure = { error ->
                        showError(error.message ?: "Nie udało się odblokować sejfu.")
                    },
                )
            }
        }
    }

    private fun returnDataset(entry: VaultEntry, domain: String) {
        val usernameId = parcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = parcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        val presentation = accountPresentation(entry.username, domain)
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

    private fun showAccountChooser(entries: List<VaultEntry>, domain: String) {
        val content = verticalContainer(Gravity.TOP).apply {
            setPadding(dp(24), dp(40), dp(24), dp(28))
            addView(titleText("Wybierz konto"))
            addView(bodyText("Znaleziono ${entries.size} kont dla $domain").withMargins(top = 8, bottom = 20))

            entries.forEach { entry ->
                addView(
                    Button(this@AutofillAuthActivity).apply {
                        text = entry.username
                        isAllCaps = false
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 33, 45))
                        setPadding(dp(18), dp(8), dp(18), dp(8))
                        minHeight = dp(58)
                        setOnClickListener { returnDataset(entry, domain) }
                    }.withMargins(bottom = 12),
                )
            }

            addView(
                Button(this@AutofillAuthActivity).apply {
                    text = "Anuluj"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.rgb(55, 65, 76))
                    setOnClickListener { returnCanceled() }
                }.withMargins(top = 8),
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND_COLOR)
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

    private fun showLoading(domain: String, message: String) {
        val container = verticalContainer(Gravity.CENTER).apply {
            addView(titleText("PasswdGen"))
            addView(
                ProgressBar(this@AutofillAuthActivity).apply {
                    indeterminateTintList = ColorStateList.valueOf(Color.CYAN)
                }.withMargins(top = 28, bottom = 22),
            )
            addView(bodyText(message))
            addView(bodyText(domain).withMargins(top = 8))
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

    private fun accountPresentation(username: String, domain: String): RemoteViews =
        RemoteViews(packageName, R.layout.autofill_presentation).apply {
            setTextViewText(R.id.autofill_primary, username)
            setTextViewText(R.id.autofill_secondary, domain)
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

    internal companion object {
        const val EXTRA_USERNAME_ID = "autofill_username_id"
        const val EXTRA_PASSWORD_ID = "autofill_password_id"
        const val EXTRA_WEB_DOMAIN = "autofill_web_domain"
        private val BACKGROUND_COLOR = Color.rgb(6, 14, 23)
    }
}
