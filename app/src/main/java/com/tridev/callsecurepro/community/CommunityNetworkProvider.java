package com.tridev.callsecurepro.community;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Single integration point for the Call Secure cloud backend.
 *
 * Step 28 intentionally returns the safe no-op gateway. A later backend step can replace only
 * this provider wiring with an authenticated implementation without changing UI, Room, or caller
 * identity resolution code.
 */
public final class CommunityNetworkProvider {

    private static volatile CommunityNetworkGateway gateway;

    private CommunityNetworkProvider() {
    }

    @NonNull
    public static CommunityNetworkGateway get(@NonNull Context context) {
        if (gateway == null) {
            synchronized (CommunityNetworkProvider.class) {
                if (gateway == null) {
                    gateway = new NoOpCommunityNetworkGateway();
                }
            }
        }
        return gateway;
    }
}
