package com.tridev.callsecurepro.protection;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Small, explicit user-controlled screening preferences.
 */
public final class ProtectionPreferences {

    private static final String PREFS = "caller_protection_settings";
    private static final String KEY_AUTO_BLOCK_HIGH_RISK = "auto_block_high_risk";
    private static final String KEY_SILENCE_SUSPICIOUS = "silence_suspicious";
    private static final String KEY_BLOCK_HIDDEN_CALLS = "block_hidden_calls";
    private static final String KEY_BLOCK_UNKNOWN_CALLERS = "block_unknown_callers";
    private static final String KEY_SILENCE_UNKNOWN_CALLERS = "silence_unknown_callers";

    private ProtectionPreferences() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isAutoBlockHighRiskEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_AUTO_BLOCK_HIGH_RISK, false);
    }

    public static void setAutoBlockHighRiskEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        prefs(context).edit().putBoolean(KEY_AUTO_BLOCK_HIGH_RISK, enabled).apply();
    }

    public static boolean isSilenceSuspiciousEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SILENCE_SUSPICIOUS, false);
    }

    public static void setSilenceSuspiciousEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        prefs(context).edit().putBoolean(KEY_SILENCE_SUSPICIOUS, enabled).apply();
    }

    public static boolean isBlockHiddenCallsEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_BLOCK_HIDDEN_CALLS, false);
    }

    public static void setBlockHiddenCallsEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        prefs(context).edit().putBoolean(KEY_BLOCK_HIDDEN_CALLS, enabled).apply();
    }

    public static boolean isBlockUnknownCallersEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_BLOCK_UNKNOWN_CALLERS, false);
    }

    public static void setBlockUnknownCallersEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        prefs(context).edit().putBoolean(KEY_BLOCK_UNKNOWN_CALLERS, enabled).apply();
    }

    public static boolean isSilenceUnknownCallersEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SILENCE_UNKNOWN_CALLERS, false);
    }

    public static void setSilenceUnknownCallersEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        prefs(context).edit().putBoolean(KEY_SILENCE_UNKNOWN_CALLERS, enabled).apply();
    }
}
