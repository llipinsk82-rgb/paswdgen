package com.blackserv.passwdgen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.blackserv.passwdgen.ui.theme.PasswdGenTheme

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val lockHandler = Handler(Looper.getMainLooper())
    private var externalFlowActive = false

    private val externalFlowTimeout = Runnable {
        externalFlowActive = false
        viewModel.lockVault()
    }

    private val externalFlowReturnLock = Runnable {
        if (!externalFlowActive && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.lockVault()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            PasswdGenTheme {
                Box {
                    PasswdGenApp(
                        viewModel = viewModel,
                        onUnlockRequest = ::requestVaultUnlock,
                        onSensitiveActionRequest = ::requestSensitiveAction,
                        onExternalFlowChanged = ::setExternalFlowActive,
                    )
                    IconButton(
                        onClick = { GitHubUpdater.checkNow(this@MainActivity) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 12.dp, end = 16.dp)
                            .size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = "Sprawdź aktualizacje",
                            tint = PgCyan,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }

        GitHubUpdater.checkOnLaunch(this)
    }

    override fun onStart() {
        super.onStart()
        lockHandler.removeCallbacks(externalFlowTimeout)
        lockHandler.removeCallbacks(externalFlowReturnLock)
    }

    override fun onStop() {
        super.onStop()
        if (externalFlowActive) {
            lockHandler.removeCallbacks(externalFlowTimeout)
            lockHandler.postDelayed(externalFlowTimeout, EXTERNAL_FLOW_TIMEOUT_MILLIS)
        } else {
            viewModel.lockVault()
        }
    }

    override fun onDestroy() {
        lockHandler.removeCallbacks(externalFlowTimeout)
        lockHandler.removeCallbacks(externalFlowReturnLock)
        super.onDestroy()
    }

    private fun requestVaultUnlock() {
        requestAuthentication(
            title = "Odblokuj sejf",
            subtitle = "Potwierdź tożsamość biometrią lub kodem urządzenia",
            onSuccess = viewModel::unlockVault,
        )
    }

    private fun requestSensitiveAction(title: String, action: () -> Unit) {
        requestAuthentication(
            title = title,
            subtitle = "Potwierdź tożsamość przed operacją na hasłach",
            onSuccess = action,
        )
    }

    private fun setExternalFlowActive(active: Boolean) {
        externalFlowActive = active
        lockHandler.removeCallbacks(externalFlowTimeout)
        lockHandler.removeCallbacks(externalFlowReturnLock)

        if (!active && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // ActivityResult może zostać dostarczony chwilę przed onStart().
            // Nie blokujemy sejfu natychmiast, ponieważ usunęłoby to wybrany URI
            // i dialog hasła kopii. Jeśli aplikacja faktycznie nie wróci na ekran,
            // krótki bezpiecznik nadal zamknie sejf.
            lockHandler.postDelayed(
                externalFlowReturnLock,
                EXTERNAL_FLOW_RETURN_GRACE_MILLIS,
            )
        }
    }

    private fun requestAuthentication(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
    ) {
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
                    onSuccess()
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
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build(),
        )
    }

    private companion object {
        const val EXTERNAL_FLOW_TIMEOUT_MILLIS = 5 * 60 * 1_000L
        const val EXTERNAL_FLOW_RETURN_GRACE_MILLIS = 15_000L
    }
}
