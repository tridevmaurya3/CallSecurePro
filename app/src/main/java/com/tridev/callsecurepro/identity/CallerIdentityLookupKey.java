package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;

import com.tridev.callsecurepro.protection.ProtectionRepository;

/**
 * Produces a stable lookup/cache key for caller identity resolution.
 *
 * Valid parseable numbers prefer E.164 so national and international representations share the
 * same local cache entry. The legacy normalized form remains available for backward-compatible
 * cache reads created before canonical keys were introduced.
 */
public final class CallerIdentityLookupKey {

    private final NumberIntelligenceAnalyzer numberAnalyzer;

    public CallerIdentityLookupKey() {
        numberAnalyzer = new NumberIntelligenceAnalyzer();
    }

    @NonNull
    public Key resolve(@NonNull String rawNumber) {
        String legacy = ProtectionRepository.normalize(rawNumber);
        if (legacy.isEmpty()) {
            throw new IllegalArgumentException("A valid phone number is required");
        }

        NumberIntelligenceAnalyzer.Result analysis = numberAnalyzer.analyze(rawNumber);
        String e164 = analysis.getE164Format() == null ? "" : analysis.getE164Format().trim();
        String canonical = analysis.isParsed() && !e164.isEmpty() ? e164 : legacy;
        return new Key(canonical, legacy);
    }

    public static final class Key {
        @NonNull
        public final String canonical;
        @NonNull
        public final String legacy;

        private Key(@NonNull String canonical, @NonNull String legacy) {
            this.canonical = canonical;
            this.legacy = legacy;
        }

        public boolean hasDistinctLegacyAlias() {
            return !canonical.equals(legacy);
        }
    }
}
