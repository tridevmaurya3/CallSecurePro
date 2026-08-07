package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Current offline-only backend adapter. Never fabricates remote caller identity data. */
public final class NoOpCallerIdentityRemoteSource implements CallerIdentityRemoteSource {

    @Nullable
    @Override
    public RemoteIdentity lookup(@NonNull String normalizedNumber) {
        return null;
    }
}
