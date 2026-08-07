package com.tridev.callsecurepro.telecom;

import android.content.Intent;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.calls.CallReminderScheduler;
import com.tridev.callsecurepro.ui.incall.InCallActivity;

/**
 * Android Telecom bridge for the Call Secure Pro in-call experience.
 */
public class CallSecureInCallService extends InCallService {

    private final CallSessionManager callSessionManager = CallSessionManager.getInstance();
    private final CallAudioController callAudioController = CallAudioController.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        callAudioController.attachService(this);
    }

    @Override
    public void onCallAdded(@NonNull Call call) {
        super.onCallAdded(call);
        callSessionManager.registerCall(call);
        launchInCallUi();
    }

    @Override
    public void onCallRemoved(@NonNull Call call) {
        String number = extractPhoneNumber(call.getDetails());
        long endedAt = System.currentTimeMillis();

        callSessionManager.unregisterCall(call);
        super.onCallRemoved(call);

        CallReminderScheduler.schedulePostCallPrompt(this, number, endedAt);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        callAudioController.updateAudioState(audioState);
    }

    private void launchInCallUi() {
        Intent intent = new Intent(this, InCallActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Telecom remains functional even if a device temporarily blocks background UI launch.
        }
    }

    @Nullable
    private String extractPhoneNumber(@Nullable Call.Details details) {
        if (details == null) {
            return null;
        }
        Uri handle = details.getHandle();
        if (handle == null || handle.getSchemeSpecificPart() == null) {
            return null;
        }
        String number = handle.getSchemeSpecificPart().trim();
        return number.isEmpty() ? null : number;
    }

    @Override
    public void onDestroy() {
        callAudioController.detachService(this);
        callSessionManager.clear();
        super.onDestroy();
    }
}
