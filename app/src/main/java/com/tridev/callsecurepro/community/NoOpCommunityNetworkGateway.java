package com.tridev.callsecurepro.community;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;

/** Safe default until a real authenticated Call Secure backend is configured. */
public final class NoOpCommunityNetworkGateway implements CommunityNetworkGateway {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @NonNull
    @Override
    public String getStatusLabel() {
        return "Cloud backend not configured";
    }

    @Nullable
    @Override
    public CallerIdentityRemoteSource.RemoteIdentity lookup(@NonNull String normalizedNumber) {
        return null;
    }

    @NonNull
    @Override
    public ReportSubmissionResult submitReport(
            @NonNull String normalizedNumber,
            @NonNull String category
    ) {
        return ReportSubmissionResult.unavailable();
    }
}
