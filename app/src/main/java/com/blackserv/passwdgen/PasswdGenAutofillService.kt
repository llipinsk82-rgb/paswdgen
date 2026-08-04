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
        val form = structure?.let(AssistStructureParser::parse)
        val domain = form?.webDomain
        val ids = listOfNotNull(form?.usernameId, form?.passwordId).distinct()

        // Pierwsza bezpieczna wersja obsługuje formularze WWW z domeną przekazaną przez Androida.
        // Dla natywnych aplikacji potrzebne będzie jawne, przetestowane mapowanie pakiet -> domena.
        if (form == null || domain == null || ids.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val authenticationIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, form.usernameId)
            putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, form.passwordId)
            putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, domain)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE.incrementAndGet(),
            authenticationIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val presentation = RemoteViews(packageName, R.layout.autofill_presentation).apply {
            setTextViewText(R.id.autofill_primary, "Odblokuj PasswdGen")
            setTextViewText(R.id.autofill_secondary, domain)
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
        // Zapisywanie nowych haseł przez system włączymy po fizycznym teście bezpiecznego wypełniania.
        callback.onSuccess()
    }

    private companion object {
        val REQUEST_CODE = AtomicInteger(10_000)
    }
}
