package com.tridev.callsecurepro.identity;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Short-lived negative cache for remote caller identity misses.
 *
 * It prevents repeated network/database hits for the same unknown number. Raw phone numbers are
 * never stored: SharedPreferences keys are SHA-256 hashes of the canonical lookup key.
 */
public final class RemoteLookupMissCache {

    private static final String PREFS = "caller_identity_remote_misses_v1";
    private static final String PREFIX = "miss_";
    private static final long USER_INITIATED_TTL_MILLIS = 5L * 60L * 1000L;
    private static final long PASSIVE_TTL_MILLIS = 30L * 60L * 1000L;

    private final SharedPreferences preferences;

    public RemoteLookupMissCache(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean shouldSkipRemote(
            @NonNull String canonicalNumber,
            @NonNull CallerIdentityLookupMode mode,
            long now
    ) {
        String key = preferenceKey(canonicalNumber);
        long recordedAt = preferences.getLong(key, 0L);
        if (recordedAt <= 0L) {
            return false;
        }

        long ttl = mode == CallerIdentityLookupMode.PASSIVE_CALL_SCREENING
                ? PASSIVE_TTL_MILLIS
                : USER_INITIATED_TTL_MILLIS;
        if (now - recordedAt < ttl) {
            return true;
        }

        preferences.edit().remove(key).apply();
        return false;
    }

    public void recordMiss(@NonNull String canonicalNumber, long now) {
        preferences.edit().putLong(preferenceKey(canonicalNumber), now).apply();
    }

    public void clear(@NonNull String canonicalNumber) {
        preferences.edit().remove(preferenceKey(canonicalNumber)).apply();
    }

    @NonNull
    private String preferenceKey(@NonNull String canonicalNumber) {
        return PREFIX + sha256(canonicalNumber.trim());
    }

    @NonNull
    private String sha256(@NonNull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
