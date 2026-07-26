package com.blackserv.passwdgen

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.blackserv.passwdgen.ui.theme.PasswdGenTheme

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            PasswdGenTheme {
                PasswdGenApp(
                    viewModel = viewModel,
                    onUnlockRequest = ::requestVaultUnlock,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.lockVault()
    }

    private fun requestVaultUnlock() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (BiometricManager.from(this).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                viewModel.showMessage("Skonfiguruj blokadę ekranu lub biometrię w ustawieniach Androida.")
                return
            }
            else -> {
                viewModel.showMessage("To urządzenie nie udostępnia obsługiwanego uwierzytelniania.")
                return
            }
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockVault()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        viewModel.showMessage(errString.toString())
                    }
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Odblokuj sejf")
                .setSubtitle("Potwierdź tożsamość biometrią lub kodem urządzenia")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }
}
