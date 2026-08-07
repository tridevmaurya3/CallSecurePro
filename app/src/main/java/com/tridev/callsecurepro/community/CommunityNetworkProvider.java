package com.tridev.callsecurepro.community;

import android.content.Context;

import androidx.annotation.NonNull;

/** Selects the real Firebase gateway when local project configuration exists. */
public final class CommunityNetworkProvider {

    private static volatile CommunityNetworkGateway gateway;
    private static volatile boolean configuredState;

    private CommunityNetworkProvider() {
    }

    @NonNull
    public static CommunityNetworkGateway get(@NonNull Context context) {
        boolean configured = FirebaseCommunityConfig.isConfigured();
        if (gateway == null || configuredState != configured) {
            synchronized (CommunityNetworkProvider.class) {
                if (gateway == null || configuredState != configured) {
                    configuredState = configured;
                    gateway = configured
                            ? new FirebaseCommunityNetworkGateway(context.getApplicationContext())
                            : new NoOpCommunityNetworkGateway();
                }
            }
        }
        return gateway;
    }
}
