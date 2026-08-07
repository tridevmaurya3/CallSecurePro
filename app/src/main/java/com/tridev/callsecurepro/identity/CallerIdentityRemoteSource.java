package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Contract for a real caller-identity source.
 *
 * Implementations must return only identities backed by a real source. Returning null means
 * no trustworthy remote identity is available. Multi-source orchestration can pass a lookup mode
 * so slower providers are used only where their latency is appropriate.
 */
public interface CallerIdentityRemoteSource {

    @Nullable
    RemoteIdentity lookup(@NonNull String normalizedNumber);

    /**
     * Mode-aware lookup. Existing providers remain compatible through the default implementation.
     * Providers that need different manual/passive latency policies can override this method.
     */
    @Nullable
    default RemoteIdentity lookup(
            @NonNull String normalizedNumber,
            @NonNull CallerIdentityLookupMode mode
    ) {
        return lookup(normalizedNumber);
    }

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
