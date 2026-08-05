package com.blackserv.passwdgen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillEventHistory
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicInteger

class PasswdGenAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        capturePreviousSaveUiOutcome()

        if (cancellationSignal.isCanceled) {
            callback.onSuccess(null)
            return
        }

        val contexts = request.fillContexts
        val structure = contexts.lastOrNull()?.structure
        if (structure == null) {
            saveEmptyDiagnostic("Android nie przekazał struktury formularza")
            callback.onSuccess(null)
            return
        }

        val analyses = contexts.map { context ->
            AssistStructureParser.analyze(context.structure)
        }
        val analysis = analyses.last()
        val form = analysis.form
        val ids = listOfNotNull(form?.usernameId, form?.passwordId).distinct()
        if (form == null || ids.isEmpty()) {
            saveDiagnostic(analysis.stats, "Brak rozpoznanych pól loginu lub hasła")
            callback.onSuccess(null)
            return
        }

        val sessionForm = AutofillSessionPolicy.merge(
            forms = analyses.mapNotNull(AutofillParseResult::form),
            current = form,
        )
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
            saveDiagnostic(
                analysis.stats,
                diagnosticOutcome("formularz WWW", form, sessionForm),
            )
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
            saveDiagnostic(
                analysis.stats,
                diagnosticOutcome("aplikacja natywna", form, sessionForm),
            )
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
            setId("passwdgen-locked")
        }.build()

        val response = FillResponse.Builder()
            .addDataset(lockedDataset)
        buildSaveInfo(sessionForm)?.let(response::setSaveInfo)
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        AutofillDiagnosticStore.saveSaveOutcome(
            this,
            "Android wywołał zapis po zaakceptowaniu systemowego monitu.",
        )
        val captured = AutofillSaveExtractor.extract(
            request.fillContexts.map { context -> context.structure },
        )
        if (captured == null) {
            AutofillDiagnosticStore.saveSaveOutcome(
                this,
                "Żądanie zapisu dotarło, ale nie udało się odczytać kompletnego loginu i hasła.",
            )
            callback.onFailure("Nie udało się bezpiecznie odczytać loginu i hasła z formularza.")
            return
        }

        val target = captured.webDomain?.let(AutofillSaveTarget::Web) ?: run {
            val identity = NativeAppIdentityResolver.resolve(this, captured.packageName)
            if (identity == null) {
                AutofillDiagnosticStore.saveSaveOutcome(
                    this,
                    "Żądanie zapisu dotarło, ale weryfikacja aplikacji nie powiodła się.",
                )
                callback.onFailure("Nie udało się zweryfikować aplikacji przed zapisem.")
                return
            }
            AutofillSaveTarget.Native(identity)
        }

        val token = PendingAutofillSaveStore.put(
            PendingAutofillSave(
                target = target,
                username = captured.username,
                password = captured.password,
            ),
        )
        val saveIntent = Intent(this, AutofillSaveActivity::class.java).apply {
            putExtra(AutofillSaveActivity.EXTRA_TOKEN, token)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }

        runCatching { startActivity(saveIntent) }
            .onSuccess {
                AutofillDiagnosticStore.saveSaveOutcome(
                    this,
                    "Systemowy monit zaakceptowany; otwarto bezpieczne potwierdzenie PasswdGen.",
                )
                callback.onSuccess()
            }
            .onFailure {
                PendingAutofillSaveStore.remove(token)
                AutofillDiagnosticStore.saveSaveOutcome(
                    this,
                    "Systemowy monit zaakceptowany, ale nie udało się otworzyć potwierdzenia PasswdGen.",
                )
                callback.onFailure("Nie udało się otworzyć bezpiecznego potwierdzenia zapisu.")
            }
    }

    private fun buildSaveInfo(form: AutofillSessionForm): SaveInfo? {
        val requiredIds = listOfNotNull(form.usernameId, form.passwordId).distinct()
        if (requiredIds.isEmpty()) return null

        var dataType = 0
        if (form.usernameId != null) dataType = dataType or SaveInfo.SAVE_DATA_TYPE_USERNAME
        if (form.passwordId != null) dataType = dataType or SaveInfo.SAVE_DATA_TYPE_PASSWORD

        val workflow = AutofillSessionPolicy.workflow(
            usernameDetected = form.usernameId != null,
            passwordDetected = form.passwordId != null,
        )
        val flags = when (workflow) {
            AutofillSaveWorkflow.DELAY -> SaveInfo.FLAG_DELAY_SAVE
            AutofillSaveWorkflow.COMPLETE -> SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
        }
        return SaveInfo.Builder(dataType, requiredIds.toTypedArray())
            .setDescription("Zapisz nowe dane lub zaktualizuj hasło w PasswdGen")
            .setFlags(flags)
            .apply {
                if (workflow == AutofillSaveWorkflow.COMPLETE) {
                    form.submitId?.let(::setTriggerId)
                }
            }
            .build()
    }

    @Suppress("DEPRECATION")
    private fun capturePreviousSaveUiOutcome() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val events = runCatching { fillEventHistory?.events.orEmpty() }
            .getOrElse { return }
        if (events.any { event -> event.type == FillEventHistory.Event.TYPE_SAVE_SHOWN }) {
            AutofillDiagnosticStore.saveSaveOutcome(
                this,
                "Android wyświetlił systemowy monit zapisu.",
            )
            return
        }

        val committed = events.lastOrNull { event ->
            event.type == FillEventHistory.Event.TYPE_CONTEXT_COMMITTED
        } ?: return
        val outcome = when (committed.noSaveUiReason) {
            FillEventHistory.Event.NO_SAVE_UI_REASON_NONE ->
                "Sesja formularza została zatwierdzona; Android nie podał powodu blokady monitu."

            FillEventHistory.Event.NO_SAVE_UI_REASON_NO_SAVE_INFO ->
                "Android nie znalazł SaveInfo w ostatniej odpowiedzi PasswdGen."

            FillEventHistory.Event.NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG ->
                "Zapis został odroczony, ponieważ formularz jest wieloetapowy."

            FillEventHistory.Event.NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED ->
                "Monit nie został pokazany: co najmniej jedno wymagane pole było puste."

            FillEventHistory.Event.NO_SAVE_UI_REASON_NO_VALUE_CHANGED ->
                "Monit nie został pokazany: Android nie wykrył zmiany wartości."

            FillEventHistory.Event.NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED ->
                "Monit nie został pokazany: walidacja pola nie powiodła się."

            FillEventHistory.Event.NO_SAVE_UI_REASON_DATASET_MATCH ->
                "Monit nie został pokazany: wartości odpowiadały istniejącemu zestawowi danych."

            NO_SAVE_UI_REASON_USING_CREDMAN ->
                "Monit nie został pokazany: Android użył Credential Manager zamiast klasycznego Autofill."

            else -> "Monit zapisu nie został pokazany; nieznany kod systemowy ${committed.noSaveUiReason}."
        }
        AutofillDiagnosticStore.saveSaveOutcome(this, outcome)
    }

    private fun diagnosticOutcome(
        targetType: String,
        current: AutofillForm,
        session: AutofillSessionForm,
    ): String = buildString {
        append("Gotowe: $targetType")
        append("; bieżący etap ${fieldModeLabel(current.usernameId != null, current.passwordId != null)}")
        append("; sesja ${fieldModeLabel(session.usernameId != null, session.passwordId != null)}")
        append("; konteksty ${session.contextCount}")
        append("; przycisk zatwierdzenia ${yesNo(session.submitId != null)}")
    }

    private fun fieldModeLabel(usernameDetected: Boolean, passwordDetected: Boolean): String = when {
        usernameDetected && passwordDetected -> "login + hasło"
        usernameDetected -> "login"
        passwordDetected -> "hasło"
        else -> "brak pól"
    }

    private fun yesNo(value: Boolean): String = if (value) "tak" else "nie"

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
        const val NO_SAVE_UI_REASON_USING_CREDMAN = 7
        val REQUEST_CODE = AtomicInteger(10_000)
    }
}
