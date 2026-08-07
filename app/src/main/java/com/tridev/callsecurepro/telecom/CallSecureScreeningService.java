package com.tridev.callsecurepro.telecom;

import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.NonNull;

/**
 * Initial safe screening implementation.
 *
 * Step 7 intentionally allows every call. Later spam-protection steps will connect
 * local rules, cache and backend intelligence before making any block/silence decision.
 */
public class CallSecureScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();

        respondToCall(callDetails, response);
    }
}
