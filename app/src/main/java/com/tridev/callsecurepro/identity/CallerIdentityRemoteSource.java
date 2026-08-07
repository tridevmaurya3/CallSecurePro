package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Contract for a future authenticated caller-identity backend.
 *
 * Implementations must return only identities backed by a real source. Returning null means
 * no verified remote identity is available. The current app intentionally uses the no-op
 * implementation until a secure backend is configured.
 */
public interface CallerIdentityRemoteSource {

    @Nullable
    RemoteIdentity lookup(@NonNull String normalizedNumber);

    final class RemoteIdentity {
        @NonNull
        public final String displayNumber;
        @Nullable
        public final String displayName;
        @Nullable
        public final String category;
        @NonNull
        public final CallerIdentityResult.IdentityType identityType;
        @NonNull
        public final CallerIdentityResult.VerificationLevel verificationLevel;
        @NonNull
        public final String source;
        public final int confidence;
        public final long expiresAt;

        public RemoteIdentity(
                @NonNull String displayNumber,
                @Nullable String displayName,
                @Nullable String category,
                @NonNull CallerIdentityResult.IdentityType identityType,
                @NonNull CallerIdentityResult.VerificationLevel verificationLevel,
                @NonNull String source,
                int confidence,
                long expiresAt
        ) {
            this.displayNumber = displayNumber;
            this.displayName = displayName;
            this.category = category;
            this.identityType = identityType;
            this.verificationLevel = verificationLevel;
            this.source = source;
            this.confidence = confidence;
            this.expiresAt = expiresAt;
        }
    }
}
