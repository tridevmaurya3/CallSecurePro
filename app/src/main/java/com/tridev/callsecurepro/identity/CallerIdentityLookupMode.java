package com.tridev.callsecurepro.identity;

/**
 * Describes why a caller-identity lookup is being performed.
 *
 * User-initiated searches may use slower remote providers, while passive incoming-call
 * resolution must stay latency-sensitive and fail open to local/cache intelligence.
 */
public enum CallerIdentityLookupMode {
    USER_INITIATED(true),
    PASSIVE_CALL_SCREENING(false);

    private final boolean slowRemoteProvidersAllowed;

    CallerIdentityLookupMode(boolean slowRemoteProvidersAllowed) {
        this.slowRemoteProvidersAllowed = slowRemoteProvidersAllowed;
    }

    public boolean areSlowRemoteProvidersAllowed() {
        return slowRemoteProvidersAllowed;
    }
}
