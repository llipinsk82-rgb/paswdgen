package com.blackserv.passwdgen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import java.util.UUID
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
            val session = resolveSessionState(request.clientState, "nieustalony")
            saveEmptyDiagnostic("Android nie przekazał struktury formularza", session)
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
            val targetKey = analysis.stats.packageName.ifBlank { "nieustalony" }
            val session = resolveSessionState(request.clientState, targetKey)
            saveDiagnostic(
                analysis.stats,
                "Brak rozpoznanych pól loginu lub hasła",
                session,
            )
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
        val targetKey: String
        val domain = form.webDomain
        if (domain != null) {
            authenticationIntent.putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, domain)
            targetLabel = "Odblokuj PasswdGen"
            targetDetail = domain
            targetKey = domain
        } else {
            val identity = NativeAppIdentityResolver.resolve(this, form.packageName)
            if (identity == null) {
                val unresolvedTarget = "app:${form.packageName.ifBlank { "nieustalona" }}"
                val session = resolveSessionState(request.clientState, unresolvedTarget)
                saveDiagnostic(
                    analysis.stats,
                    "Nie udało się zweryfikować aplikacji",
                    session,
                )
                callback.onSuccess(null)
                return
            }
            authenticationIntent.putExtra(
                AutofillAuthActivity.EXTRA_NATIVE_PACKAGE,
                identity.packageName,
            )
            targetLabel = "Odblokuj PasswdGen"
            targetDetail = identity.appLabel
            targetKey = "app:${identity.packageName}"
        }

        val session = resolveSessionState(request.clientState, targetKey)
        saveDiagnostic(
            analysis.stats,
            diagnosticOutcome(
                targetType = if (domain != null) "formularz WWW" else "aplikacja natywna",
                current = form,
                session = sessionForm,
            ),
            session,
        )

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
            .setClientState(session.toBundle())
        buildSaveInfo(sessionForm)?.let(response::setSaveInfo)
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val session = readSessionState(request.clientState)
        saveSessionOutcome(
            session,
            "Android wywołał zapis po zaakceptowaniu systemowego monitu.",
        )
        val captured = AutofillSaveExtractor.extract(
            request.fillContexts.map { context -> context.structure },
        )
        if (captured == null) {
            saveSessionOutcome(
                session,
                "Żądanie zapisu dotarło, ale nie znaleziono jednoznacznego loginu i hasła.",
            )
            callback.onFailure("Nie udało się bezpiecznie ustalić jednoznacznego loginu i hasła.")
            return
        }

        val target = captured.webDomain?.let(AutofillSaveTarget::Web) ?: run {
            val identity = NativeAppIdentityResolver.resolve(this, captured.packageName)
            if (identity == null) {
                saveSessionOutcome(
                    session,
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
                saveSessionOutcome(
                    session,
                    "Systemowy monit zaakceptowany; otwarto bezpieczne potwierdzenie PasswdGen.",
                )
                callback.onSuccess()
            }
            .onFailure {
                PendingAutofillSaveStore.remove(token)
                saveSessionOutcome(
                    session,
                    "Systemowy monit zaakceptowany, ale nie udało się otworzyć potwierdzenia PasswdGen.",
                )
                callback.onFailure("Nie udało się otworzyć bezpiecznego potwierdzenia zapisu.")
            }
    }

    private fun buildSaveInfo(form: AutofillSessionForm): SaveInfo? {
        val requiredIds = listOfNotNull(form.usernameId, form.passwordId).distinct()
        if (requiredIds.isEmpty()) return null

        var dataType = 0
        if (form.passwordId != null) {
            dataType = dataType or SaveInfo.SAVE_DATA_TYPE_USERNAME
            dataType = dataType or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        } else if (form.usernameId != null) {
            dataType = dataType or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }

        val workflow = if (form.passwordId != null) {
            AutofillSaveWorkflow.COMPLETE
        } else {
            AutofillSaveWorkflow.DELAY
        }
        val flags = when (workflow) {
            AutofillSaveWorkflow.DELAY -> SaveInfo.FLAG_DELAY_SAVE
            AutofillSaveWorkflow.COMPLETE -> SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
        }
        val builder = SaveInfo.Builder(dataType, requiredIds.toTypedArray())
            .setDescription("Zapisz nowe dane lub zaktualizuj hasło w PasswdGen")
            .setFlags(flags)

        val optionalIds = listOfNotNull(form.confirmationPasswordId)
            .filterNot(requiredIds::contains)
            .distinct()
        if (optionalIds.isNotEmpty()) builder.setOptionalIds(optionalIds.toTypedArray())
        if (workflow == AutofillSaveWorkflow.COMPLETE) {
            form.submitId?.let(builder::setTriggerId)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun capturePreviousSaveUiOutcome() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val history = runCatching { fillEventHistory }.getOrNull() ?: return
        val session = readSessionState(history.clientState) ?: return
        val events = history.events.orEmpty()
        if (events.any { event -> event.type == FillEventHistory.Event.TYPE_SAVE_SHOWN }) {
            saveSessionOutcome(
                session,
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
        saveSessionOutcome(session, outcome)
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
        append("; powtórzenie hasła ${yesNo(session.confirmationPasswordId != null)}")
        append("; odzyskanie loginu przy zapisie ${yesNo(session.passwordId != null && session.usernameId == null)}")
        append("; przycisk zatwierdzenia ${yesNo(session.submitId != null)}")
    }

    private fun fieldModeLabel(usernameDetected: Boolean, passwordDetected: Boolean): String = when {
        usernameDetected && passwordDetected -> "login + hasło"
        usernameDetected -> "login"
        passwordDetected -> "hasło"
        else -> "brak pól"
    }

    private fun resolveSessionState(clientState: Bundle?, targetKey: String): SessionState {
        val existing = readSessionState(clientState)
        return if (existing?.targetKey == targetKey) {
            existing
        } else {
            SessionState(UUID.randomUUID().toString(), targetKey)
        }
    }

    private fun readSessionState(clientState: Bundle?): SessionState? {
        val token = clientState?.getString(CLIENT_STATE_SESSION_TOKEN)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val targetKey = clientState.getString(CLIENT_STATE_TARGET_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return SessionState(token, targetKey)
    }

    private fun saveSessionOutcome(session: SessionState?, outcome: String) {
        AutofillDiagnosticStore.saveSaveOutcome(
            context = this,
            sessionToken = session?.token,
            targetKey = session?.targetKey,
            outcome = outcome,
        )
    }

    private fun yesNo(value: Boolean): String = if (value) "tak" else "nie"

    private fun saveDiagnostic(
        stats: AutofillParseStats,
        outcome: String,
        session: SessionState,
    ) {
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
                sessionToken = session.token,
                targetKey = session.targetKey,
            ),
        )
    }

    private fun saveEmptyDiagnostic(outcome: String, session: SessionState) {
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
                sessionToken = session.token,
                targetKey = session.targetKey,
            ),
        )
    }

    private data class SessionState(
        val token: String,
        val targetKey: String,
    ) {
        fun toBundle(): Bundle = Bundle().apply {
            putString(CLIENT_STATE_SESSION_TOKEN, token)
            putString(CLIENT_STATE_TARGET_KEY, targetKey)
        }
    }

    private companion object {
        const val NO_SAVE_UI_REASON_USING_CREDMAN = 7
        const val CLIENT_STATE_SESSION_TOKEN = "passwdgen.autofill.session_token"
        const val CLIENT_STATE_TARGET_KEY = "passwdgen.autofill.target_key"
        val REQUEST_CODE = AtomicInteger(10_000)
    }
}
