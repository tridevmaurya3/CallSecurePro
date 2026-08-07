package com.tridev.callsecurepro.telecom;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

import androidx.annotation.NonNull;

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
        callSessionManager.unregisterCall(call);
        super.onCallRemoved(call);
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

    @Override
    public void onDestroy() {
        callAudioController.detachService(this);
        callSessionManager.clear();
        super.onDestroy();
    }
}
