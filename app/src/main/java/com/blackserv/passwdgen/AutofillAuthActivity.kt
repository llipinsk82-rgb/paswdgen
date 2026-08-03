package com.blackserv.passwdgen

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import java.util.concurrent.Executors

class AutofillAuthActivity : FragmentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (intent.getStringExtra(EXTRA_WEB_DOMAIN).isNullOrBlank()) {
            cancelAuthentication("Brak zweryfikowanej domeny formularza.")
            return
        }
        requestAuthentication()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestAuthentication() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (BiometricManager.from(this).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                cancelAuthentication("Skonfiguruj blokadę ekranu lub biometrię w Androidzie.")
                return
            }
            else -> {
                cancelAuthentication("Urządzenie nie udostępnia wymaganego uwierzytelniania.")
                return
            }
        }

        val domain = intent.getStringExtra(EXTRA_WEB_DOMAIN).orEmpty()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    prepareFillResponse()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Odblokuj PasswdGen")
                .setSubtitle("Potwierdź dostęp do kont dla $domain")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private fun prepareFillResponse() {
        executor.execute {
            val result = runCatching { buildFillResponse() }
            runOnUiThread {
                result.fold(
                    onSuccess = { response ->
                        if (response == null) {
                            cancelAuthentication("Brak zapisanych kont dla tej domeny.")
                        } else {
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(
                                    AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                                    response,
                                ),
                            )
                            finish()
                        }
                    },
                    onFailure = {
                        cancelAuthentication(
                            it.message ?: "Nie udało się odblokować sejfu.",
                        )
                    },
                )
            }
        }
    }

    private fun buildFillResponse(): FillResponse? {
        val usernameId = parcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = parcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        val domain = requireNotNull(intent.getStringExtra(EXTRA_WEB_DOMAIN))

        val entries = VaultRepository(applicationContext)
            .loadAll()
            .filter { AutofillDomainPolicy.matches(it.website, domain) }
            .sortedBy { it.username.lowercase() }

        if (entries.isEmpty()) return null

        val response = FillResponse.Builder()
        entries.forEach { entry ->
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, entry.username)
                setTextViewText(android.R.id.text2, domain)
            }
            val dataset = Dataset.Builder(presentation)
            usernameId?.let {
                dataset.setValue(it, AutofillValue.forText(entry.username), presentation)
            }
            passwordId?.let {
                dataset.setValue(it, AutofillValue.forText(entry.password), presentation)
            }
            response.addDataset(dataset.build())
        }
        return response.build()
    }

    private fun <T> parcelableExtra(name: String, type: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, type)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name) as? T
        }
    }

    private fun cancelAuthentication(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    internal companion object {
        const val EXTRA_USERNAME_ID = "autofill_username_id"
        const val EXTRA_PASSWORD_ID = "autofill_password_id"
        const val EXTRA_WEB_DOMAIN = "autofill_web_domain"
    }
}
