package com.tridev.callsecurepro.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Device-local visual preferences for Call Secure Pro.
 *
 * These settings affect appearance only. They do not change call routing,
 * caller-screening rules, permissions, or stored call/contact data.
 */
public final class ThemePreferences {

    private static final String PREFS = "call_secure_visual_theme";
    private static final String KEY_BACKGROUND = "background_theme";
    private static final String KEY_ACCENT = "accent_palette";
    private static final String KEY_DIALER = "dialer_theme";
    private static final String KEY_CALL_BUTTON = "call_button_style";

    private ThemePreferences() {
    }

    public enum BackgroundTheme {
        ABSTRACT_BLUE,
        DARK_ABSTRACT,
        GREEN_NATURE,
        SUNSET,
        PURPLE_GRADIENT,
        MINIMAL_WHITE,
        NIGHT_SKY,
        OCEAN,
        GEOMETRIC
    }

    public enum AccentPalette {
        FRESH_GREEN,
        OCEAN_BLUE,
        SUNSET_ORANGE,
        ROYAL_PURPLE,
        CHERRY_RED,
        DARK_MODE,
        PASTEL_MIX,
        MATERIAL_YOU,
        MONOCHROME
    }

    public enum DialerTheme {
        LIGHT,
        DARK,
        BLUE,
        GRADIENT,
        MINIMAL,
        NEUMORPHIC,
        BLACK_GREEN
    }

    public enum CallButtonStyle {
        CLASSIC_GREEN,
        GRADIENT_GREEN,
        BLUE_CIRCLE,
        BLUE_ROUNDED,
        PURPLE_CIRCLE,
        ORANGE_CIRCLE,
        RED_CIRCLE,
        DARK_CIRCLE,
        DARK_ROUNDED,
        LIGHT_OUTLINE
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static BackgroundTheme getBackgroundTheme(@NonNull Context context) {
        return readEnum(
                preferences(context).getString(KEY_BACKGROUND, BackgroundTheme.MINIMAL_WHITE.name()),
                BackgroundTheme.MINIMAL_WHITE
        );
    }

    public static void setBackgroundTheme(
            @NonNull Context context,
            @NonNull BackgroundTheme value
    ) {
        preferences(context).edit().putString(KEY_BACKGROUND, value.name()).apply();
    }

    @NonNull
    public static AccentPalette getAccentPalette(@NonNull Context context) {
        return readEnum(
                preferences(context).getString(KEY_ACCENT, AccentPalette.OCEAN_BLUE.name()),
                AccentPalette.OCEAN_BLUE
        );
    }

    public static void setAccentPalette(
            @NonNull Context context,
            @NonNull AccentPalette value
    ) {
        preferences(context).edit().putString(KEY_ACCENT, value.name()).apply();
    }

    @NonNull
    public static DialerTheme getDialerTheme(@NonNull Context context) {
        return readEnum(
                preferences(context).getString(KEY_DIALER, DialerTheme.LIGHT.name()),
                DialerTheme.LIGHT
        );
    }

    public static void setDialerTheme(
            @NonNull Context context,
            @NonNull DialerTheme value
    ) {
        preferences(context).edit().putString(KEY_DIALER, value.name()).apply();
    }

    @NonNull
    public static CallButtonStyle getCallButtonStyle(@NonNull Context context) {
        return readEnum(
                preferences(context).getString(
                        KEY_CALL_BUTTON,
                        CallButtonStyle.CLASSIC_GREEN.name()
                ),
                CallButtonStyle.CLASSIC_GREEN
        );
    }

    public static void setCallButtonStyle(
            @NonNull Context context,
            @NonNull CallButtonStyle value
    ) {
        preferences(context).edit().putString(KEY_CALL_BUTTON, value.name()).apply();
    }

    public static void reset(@NonNull Context context) {
        preferences(context).edit().clear().apply();
    }

    @NonNull
    public static String signature(@NonNull Context context) {
        return getBackgroundTheme(context).name()
                + "|" + getAccentPalette(context).name()
                + "|" + getDialerTheme(context).name()
                + "|" + getCallButtonStyle(context).name();
    }

    @NonNull
    private static <T extends Enum<T>> T readEnum(
            String raw,
            @NonNull T fallback
    ) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<T> enumClass = (Class<T>) fallback.getDeclaringClass();
            return Enum.valueOf(enumClass, raw);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
