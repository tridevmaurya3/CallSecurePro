package com.tridev.callsecurepro.telecom;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline-first call screening powered by explicit local protection evidence.
 *
 * Unknown numbers are allowed by default. A call is rejected only when the user explicitly
 * blocked that number, or when the user enabled high-risk auto-blocking and local evidence
 * classifies the number as spam. Any engine failure falls back to allowing the call.
 */
public class CallSecureScreeningService extends CallScreeningService {

    private ExecutorService screeningExecutor;
    private CallerIntelligenceEngine intelligenceEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        screeningExecutor = Executors.newSingleThreadExecutor();
        intelligenceEngine = new CallerIntelligenceEngine(this);
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
                CallerAssessment assessment = engine.assess(extractNumber(callDetails));

                CallResponse response = new CallResponse.Builder()
                        .setDisallowCall(assessment.shouldBlock())
                        .setRejectCall(assessment.shouldBlock())
                        .setSilenceCall(assessment.shouldSilence())
                        .setSkipCallLog(false)
                        .setSkipNotification(false)
                        .build();

                respondToCall(callDetails, response);
            } catch (RuntimeException ignored) {
                respondAllow(callDetails);
            }
        });
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
        super.onDestroy();
    }
}
