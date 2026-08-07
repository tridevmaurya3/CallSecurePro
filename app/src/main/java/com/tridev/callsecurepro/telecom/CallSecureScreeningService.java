package com.tridev.callsecurepro.telecom;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;
import com.tridev.callsecurepro.protection.ProtectionPreferences;
import com.tridev.callsecurepro.protection.ScreeningHistoryRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline-first call screening powered by explicit local protection evidence and user rules.
 *
 * Aggressive rules remain opt-in. Saved/trusted contacts are never treated as unknown callers.
 * Any engine failure falls back to allowing the call.
 */
public class CallSecureScreeningService extends CallScreeningService {

    private ExecutorService screeningExecutor;
    private CallerIntelligenceEngine intelligenceEngine;
    private ScreeningHistoryRepository historyRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        screeningExecutor = Executors.newSingleThreadExecutor();
        intelligenceEngine = new CallerIntelligenceEngine(this);
        historyRepository = new ScreeningHistoryRepository(this);
    }

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        ExecutorService executor = screeningExecutor;
        CallerIntelligenceEngine engine = intelligenceEngine;

        if (executor == null || executor.isShutdown() || engine == null) {
            respondAllow(callDetails);
            return;
        }

        executor.execute(() -> {
            try {
                String rawNumber = extractNumber(callDetails);
                boolean hiddenNumber = rawNumber == null || rawNumber.trim().isEmpty();
                CallerAssessment assessment = engine.assess(rawNumber);

                boolean shouldBlock = assessment.shouldBlock();
                boolean shouldSilence = assessment.shouldSilence();
                String reason = assessment.getReason();

                if (!shouldBlock && hiddenNumber
                        && ProtectionPreferences.isBlockHiddenCallsEnabled(this)) {
                    shouldBlock = true;
                    shouldSilence = false;
                    reason = getString(R.string.protection_reason_hidden_blocked);
                } else if (!hiddenNumber
                        && !assessment.isSavedContact()
                        && !assessment.isTrusted()
                        && assessment.getLevel() == CallerAssessment.Level.UNKNOWN) {
                    if (ProtectionPreferences.isBlockUnknownCallersEnabled(this)) {
                        shouldBlock = true;
                        shouldSilence = false;
                        reason = getString(R.string.protection_reason_unknown_blocked);
                    } else if (!shouldSilence
                            && ProtectionPreferences.isSilenceUnknownCallersEnabled(this)) {
                        shouldSilence = true;
                        reason = getString(R.string.protection_reason_unknown_silenced);
                    }
                }

                CallResponse response = new CallResponse.Builder()
                        .setDisallowCall(shouldBlock)
                        .setRejectCall(shouldBlock)
                        .setSilenceCall(!shouldBlock && shouldSilence)
                        .setSkipCallLog(false)
                        .setSkipNotification(false)
                        .build();

                respondToCall(callDetails, response);
                recordHistory(rawNumber, assessment, shouldBlock, shouldSilence, reason);
            } catch (RuntimeException ignored) {
                respondAllow(callDetails);
            }
        });
    }

    private void recordHistory(
            @Nullable String rawNumber,
            @NonNull CallerAssessment assessment,
            boolean blocked,
            boolean silenced,
            @NonNull String reason
    ) {
        ScreeningHistoryRepository repository = historyRepository;
        if (repository == null) {
            return;
        }

        String displayNumber = rawNumber == null || rawNumber.trim().isEmpty()
                ? getString(R.string.protection_history_unknown_number)
                : rawNumber.trim();
        String action = blocked
                ? ScreeningHistoryRepository.ACTION_BLOCKED
                : silenced
                ? ScreeningHistoryRepository.ACTION_SILENCED
                : ScreeningHistoryRepository.ACTION_ALLOWED;

        try {
            repository.record(
                    assessment.getNormalizedNumber(),
                    displayNumber,
                    action,
                    reason,
                    assessment.getLevel().name(),
                    assessment.getRiskScore()
            );
        } catch (RuntimeException ignored) {
            // Screening already completed; history failure must never affect call handling.
        }
    }

    @Nullable
    private String extractNumber(@NonNull Call.Details details) {
        Uri handle = details.getHandle();
        if (handle == null) {
            return null;
        }
        return handle.getSchemeSpecificPart();
    }

    private void respondAllow(@NonNull Call.Details callDetails) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(callDetails, response);
    }

    @Override
    public void onDestroy() {
        if (screeningExecutor != null) {
            screeningExecutor.shutdownNow();
            screeningExecutor = null;
        }
        intelligenceEngine = null;
        historyRepository = null;
        super.onDestroy();
    }
}
