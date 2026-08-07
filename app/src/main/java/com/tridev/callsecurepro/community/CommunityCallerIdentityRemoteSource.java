package com.tridev.callsecurepro.community;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.identity.CallerIdentityLookupMode;
import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;

/** Bridges the caller-identity repository to the Spark-compatible community cloud contract. */
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

    @Nullable
    @Override
    public RemoteIdentity lookup(
            @NonNull String normalizedNumber,
            @NonNull CallerIdentityLookupMode mode
    ) {
        if (!gateway.isAvailable()) {
            return null;
        }
        return gateway.lookup(normalizedNumber, mode);
    }
}
