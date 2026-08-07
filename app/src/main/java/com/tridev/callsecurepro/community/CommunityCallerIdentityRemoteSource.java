package com.tridev.callsecurepro.community;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;

/** Bridges the existing caller-identity repository to the community cloud contract. */
public final class CommunityCallerIdentityRemoteSource implements CallerIdentityRemoteSource {

    private final CommunityNetworkGateway gateway;

    public CommunityCallerIdentityRemoteSource(@NonNull CommunityNetworkGateway gateway) {
        this.gateway = gateway;
    }

    @Nullable
    @Override
    public RemoteIdentity lookup(@NonNull String normalizedNumber) {
        if (!gateway.isAvailable()) {
            return null;
        }
        return gateway.lookup(normalizedNumber);
    }
}
