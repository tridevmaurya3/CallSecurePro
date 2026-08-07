package com.tridev.callsecurepro.telecom;

import android.telecom.Call;
import android.telecom.InCallService;

import androidx.annotation.NonNull;

/**
 * Telecom bridge for future Call Secure Pro in-call UI.
 *
 * The service currently observes active calls only. User-facing answer/reject/end controls,
 * audio routing, conference handling and full-screen incoming-call presentation will be
 * implemented before the app asks to become the default Phone app.
 */
public class CallSecureInCallService extends InCallService {

    private final CallSessionManager callSessionManager = CallSessionManager.getInstance();

    @Override
    public void onCallAdded(@NonNull Call call) {
        super.onCallAdded(call);
        callSessionManager.registerCall(call);
    }

    @Override
    public void onCallRemoved(@NonNull Call call) {
        callSessionManager.unregisterCall(call);
        super.onCallRemoved(call);
    }

    @Override
    public void onDestroy() {
        callSessionManager.clear();
        super.onDestroy();
    }
}
