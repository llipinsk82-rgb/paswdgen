package com.blackserv.passwdgen

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicInteger

class PasswdGenAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onSuccess(null)
            return
        }

        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            saveEmptyDiagnostic("Android nie przekazał struktury formularza")
            callback.onSuccess(null)
            return
        }

        val analysis = AssistStructureParser.analyze(structure)
        val form = analysis.form
        val ids = listOfNotNull(form?.usernameId, form?.passwordId).distinct()
        if (form == null || ids.isEmpty()) {
            saveDiagnostic(analysis.stats, "Brak rozpoznanych pól loginu lub hasła")
            callback.onSuccess(null)
            return
        }

        val authenticationIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, form.usernameId)
            putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, form.passwordId)
        }

        val targetLabel: String
        val targetDetail: String
        val domain = form.webDomain
        if (domain != null) {
            authenticationIntent.putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, domain)
            targetLabel = "Odblokuj PasswdGen"
            targetDetail = domain
            saveDiagnostic(analysis.stats, "Gotowe: formularz WWW")
        } else {
            val identity = NativeAppIdentityResolver.resolve(this, form.packageName)
            if (identity == null) {
                saveDiagnostic(analysis.stats, "Nie udało się zweryfikować aplikacji")
                callback.onSuccess(null)
                return
            }
            authenticationIntent.putExtra(
                AutofillAuthActivity.EXTRA_NATIVE_PACKAGE,
                identity.packageName,
            )
            targetLabel = "Odblokuj PasswdGen"
            targetDetail = identity.appLabel
            saveDiagnostic(analysis.stats, "Gotowe: aplikacja natywna")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE.incrementAndGet(),
            authenticationIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val presentation = RemoteViews(packageName, R.layout.autofill_presentation).apply {
            setTextViewText(R.id.autofill_primary, targetLabel)
            setTextViewText(R.id.autofill_secondary, targetDetail)
        }

        val lockedDataset = Dataset.Builder(presentation).apply {
            form.usernameId?.let { setValue(it, null, presentation) }
            form.passwordId?.let { setValue(it, null, presentation) }
            setAuthentication(pendingIntent.intentSender)
        }.build()

        callback.onSuccess(
            FillResponse.Builder()
                .addDataset(lockedDataset)
                .build(),
        )
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Zapisywanie nowych haseł przez system zostanie włączone po fizycznym teście
        // bezpiecznego wypełniania w aplikacjach natywnych.
        callback.onSuccess()
    }

    private fun saveDiagnostic(stats: AutofillParseStats, outcome: String) {
        AutofillDiagnosticStore.save(
            this,
            AutofillDiagnostic(
                timestampMillis = System.currentTimeMillis(),
                packageName = stats.packageName,
                windowCount = stats.windowCount,
                nodeCount = stats.nodeCount,
                autofillIdCount = stats.autofillIdCount,
                textCandidateCount = stats.textCandidateCount,
                usernameDetected = stats.usernameDetected,
                passwordDetected = stats.passwordDetected,
                webDomainDetected = stats.webDomainDetected,
                outcome = outcome,
            ),
        )
    }

    private fun saveEmptyDiagnostic(outcome: String) {
        AutofillDiagnosticStore.save(
            this,
            AutofillDiagnostic(
                timestampMillis = System.currentTimeMillis(),
                packageName = "",
                windowCount = 0,
                nodeCount = 0,
                autofillIdCount = 0,
                textCandidateCount = 0,
                usernameDetected = false,
                passwordDetected = false,
                webDomainDetected = false,
                outcome = outcome,
            ),
        )
    }

    private companion object {
        val REQUEST_CODE = AtomicInteger(10_000)
    }
}
