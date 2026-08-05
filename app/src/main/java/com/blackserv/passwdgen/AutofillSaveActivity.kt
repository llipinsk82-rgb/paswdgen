package com.blackserv.passwdgen

import android.graphics.Color
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors

class AutofillSaveActivity : FragmentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var token: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val pending = PendingAutofillSaveStore.peek(token)
        if (pending == null) {
            showError("Żądanie zapisu wygasło. Zaloguj się ponownie i ponów zapis.")
            return
        }

        showLoading(pending, "Potwierdź zapis biometrią lub kodem urządzenia.")
        requestAuthentication(pending)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestAuthentication(
        pending: PendingAutofillSave,
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
                    showLoading(pending, "Szyfruję dane w sejfie…")
                    persist(pending, allowCredentialRetry)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    cancelAndFinish()
                }
            },
        )

        val subtitle = if (authenticators == BiometricManager.Authenticators.DEVICE_CREDENTIAL) {
            "Potwierdź kodem urządzenia zapis dla ${pending.target.label}"
        } else {
            "Zapis danych dla ${pending.target.label}"
        }

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Zapisz w PasswdGen")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private fun persist(
        pending: PendingAutofillSave,
        allowCredentialRetry: Boolean,
    ) {
        executor.execute {
            val result = runCatching {
                val repository = VaultRepository(applicationContext)
                repository.unlockProbe()
                val matches = AutofillSavePolicy.matchingEntries(repository.loadAll(), pending)
                require(matches.size <= 1) {
                    "Znaleziono kilka identycznych wpisów. Usuń duplikaty w sejfie i spróbuj ponownie."
                }

                val existing = matches.singleOrNull()
                val entry = if (existing == null) {
                    AutofillSavePolicy.createEntry(pending)
                } else {
                    AutofillSavePolicy.updateEntry(existing, pending)
                }
                repository.save(entry)
                if (existing == null) SaveOutcome.CREATED else SaveOutcome.UPDATED
            }

            runOnUiThread {
                result.fold(
                    onSuccess = { outcome ->
                        PendingAutofillSaveStore.remove(token)
                        val message = when (outcome) {
                            SaveOutcome.CREATED -> "Zapisano nowe konto w PasswdGen."
                            SaveOutcome.UPDATED -> "Zaktualizowano hasło w PasswdGen."
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onFailure = { error ->
                        when {
                            allowCredentialRetry && error.hasCause<UserNotAuthenticatedException>() -> {
                                showLoading(
                                    pending,
                                    "Android Keystore wymaga dodatkowo kodu urządzenia.",
                                )
                                requestAuthentication(
                                    pending = pending,
                                    authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                                    allowCredentialRetry = false,
                                )
                            }

                            error.hasCause<KeyPermanentlyInvalidatedException>() -> showError(
                                "Klucz sejfu został unieważniony przez zmianę zabezpieczeń urządzenia. " +
                                    "Nie zapisano danych.",
                            )

                            error.hasCause<UserNotAuthenticatedException>() -> showError(
                                "Android Keystore nie zaakceptował uwierzytelnienia. " +
                                    "Zablokuj i odblokuj ekran, a następnie spróbuj ponownie.",
                            )

                            else -> showError(
                                error.message ?: "Nie udało się zapisać danych w sejfie.",
                            )
                        }
                    },
                )
            }
        }
    }

    private fun showLoading(pending: PendingAutofillSave, message: String) {
        val container = verticalContainer().apply {
            addView(titleText("PasswdGen"))
            addView(
                ProgressBar(this@AutofillSaveActivity).apply {
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.CYAN)
                }.withMargins(top = 28, bottom = 22),
            )
            addView(bodyText(message))
            addView(bodyText(pending.target.label).withMargins(top = 10))
            addView(bodyText(pending.username).withMargins(top = 5))
        }
        setContentView(container)
    }

    private fun showError(message: String) {
        val container = verticalContainer().apply {
            addView(titleText("Nie udało się zapisać danych"))
            addView(bodyText(message).withMargins(top = 14, bottom = 24))
            addView(
                Button(this@AutofillSaveActivity).apply {
                    text = "Zamknij"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        Color.rgb(22, 112, 142),
                    )
                    setOnClickListener { cancelAndFinish() }
                },
            )
        }
        setContentView(container)
    }

    private fun verticalContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
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

    private fun cancelAndFinish() {
        PendingAutofillSaveStore.remove(token)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private enum class SaveOutcome { CREATED, UPDATED }

    internal companion object {
        const val EXTRA_TOKEN = "autofill_save_token"
        private const val ALL_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        private val BACKGROUND_COLOR = Color.rgb(6, 14, 23)
    }
}
