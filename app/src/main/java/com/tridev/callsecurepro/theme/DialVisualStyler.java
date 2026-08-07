package com.tridev.callsecurepro.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.tridev.callsecurepro.databinding.FragmentDialBinding;

import java.util.Arrays;
import java.util.List;

/** Renders the reference-inspired keypad and call-button styles. */
public final class DialVisualStyler {

    private DialVisualStyler() {
    }

    public static void apply(
            @NonNull Context context,
            @NonNull FragmentDialBinding binding
    ) {
        ThemePreferences.DialerTheme dialerTheme = ThemePreferences.getDialerTheme(context);
        DialColors colors = colorsFor(dialerTheme);

        binding.dialRoot.setBackground(createDialBackground(dialerTheme));
        binding.dialNumberInputLayout.setBoxBackgroundColor(colors.inputBackground);
        binding.dialNumberInputLayout.setBoxStrokeColor(colors.outline);
        binding.dialNumberInput.setTextColor(colors.numberText);
        binding.dialNumberInput.setHintTextColor(colors.secondaryText);

        List<MaterialButton> keys = Arrays.asList(
                binding.key1,
                binding.key2,
                binding.key3,
                binding.key4,
                binding.key5,
                binding.key6,
                binding.key7,
                binding.key8,
                binding.key9,
                binding.keyStar,
                binding.key0,
                binding.keyHash
        );

        for (MaterialButton key : keys) {
            styleKey(context, key, colors, dialerTheme);
        }

        styleUtilityButton(context, binding.moreOptionsButton, colors, dialerTheme);
        styleUtilityButton(context, binding.backspaceButton, colors, dialerTheme);
        styleCallButton(
                context,
                binding.callButton,
                ThemePreferences.getCallButtonStyle(context)
        );
    }

    public static void styleCallButton(
            @NonNull Context context,
            @NonNull MaterialButton button,
            @NonNull ThemePreferences.CallButtonStyle style
    ) {
        boolean rounded = style == ThemePreferences.CallButtonStyle.BLUE_ROUNDED
                || style == ThemePreferences.CallButtonStyle.DARK_ROUNDED;

        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params != null) {
            params.width = Math.round(dp(context, rounded ? 88 : 70));
            params.height = Math.round(dp(context, rounded ? 64 : 70));
            button.setLayoutParams(params);
        }

        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setText("");
        button.setIconPadding(0);
        button.setElevation(dp(context, 3));

        int iconColor = Color.WHITE;
        int[] fillColors;
        int outlineColor = Color.TRANSPARENT;
        int outlineWidth = 0;

        switch (style) {
            case GRADIENT_GREEN:
                fillColors = new int[]{
                        Color.rgb(62, 207, 107),
                        Color.rgb(28, 173, 137)
                };
                break;
            case BLUE_CIRCLE:
            case BLUE_ROUNDED:
                fillColors = new int[]{Color.rgb(32, 118, 232), Color.rgb(32, 118, 232)};
                break;
            case PURPLE_CIRCLE:
                fillColors = new int[]{Color.rgb(161, 42, 214), Color.rgb(161, 42, 214)};
                break;
            case ORANGE_CIRCLE:
                fillColors = new int[]{Color.rgb(255, 149, 0), Color.rgb(255, 149, 0)};
                break;
            case RED_CIRCLE:
                fillColors = new int[]{Color.rgb(239, 56, 54), Color.rgb(239, 56, 54)};
                break;
            case DARK_CIRCLE:
            case DARK_ROUNDED:
                fillColors = new int[]{Color.rgb(28, 31, 36), Color.rgb(28, 31, 36)};
                break;
            case LIGHT_OUTLINE:
                fillColors = new int[]{Color.WHITE, Color.WHITE};
                iconColor = Color.rgb(52, 199, 89);
                outlineColor = Color.rgb(225, 230, 236);
                outlineWidth = Math.max(1, Math.round(dp(context, 1)));
                button.setElevation(dp(context, 2));
                break;
            case CLASSIC_GREEN:
            default:
                fillColors = new int[]{Color.rgb(52, 199, 89), Color.rgb(52, 199, 89)};
                break;
        }

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                fillColors
        );
        if (rounded) {
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(dp(context, 20));
        } else {
            background.setShape(GradientDrawable.OVAL);
        }
        if (outlineWidth > 0) {
            background.setStroke(outlineWidth, outlineColor);
        }

        button.setBackground(background);
        button.setIconTint(ColorStateList.valueOf(iconColor));
    }

    @NonNull
    public static GradientDrawable createDialBackground(
            @NonNull ThemePreferences.DialerTheme theme
    ) {
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        int[] colors;
        switch (theme) {
            case DARK:
                colors = new int[]{Color.rgb(7, 8, 10), Color.rgb(17, 19, 23)};
                break;
            case BLUE:
                colors = new int[]{Color.rgb(239, 247, 255), Color.rgb(222, 237, 255)};
                break;
            case GRADIENT:
                colors = new int[]{
                        Color.rgb(120, 59, 205),
                        Color.rgb(195, 55, 197),
                        Color.rgb(248, 77, 127)
                };
                orientation = GradientDrawable.Orientation.TL_BR;
                break;
            case NEUMORPHIC:
                colors = new int[]{Color.rgb(238, 242, 246), Color.rgb(231, 236, 241)};
                break;
            case BLACK_GREEN:
                colors = new int[]{Color.BLACK, Color.rgb(5, 8, 5)};
                break;
            case MINIMAL:
                colors = new int[]{Color.WHITE, Color.rgb(252, 252, 252)};
                break;
            case LIGHT:
            default:
                colors = new int[]{Color.rgb(250, 251, 253), Color.rgb(245, 247, 250)};
                break;
        }
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setShape(GradientDrawable.RECTANGLE);
        return drawable;
    }

    private static void styleKey(
            @NonNull Context context,
            @NonNull MaterialButton key,
            @NonNull DialColors colors,
            @NonNull ThemePreferences.DialerTheme theme
    ) {
        ViewGroup.LayoutParams params = key.getLayoutParams();
        if (params != null) {
            params.width = Math.round(dp(context, 68));
            params.height = Math.round(dp(context, 68));
            key.setLayoutParams(params);
        }

        key.setMinWidth(0);
        key.setMinHeight(0);
        key.setCornerRadius(Math.round(dp(context, 34)));
        key.setTextColor(colors.keyText);
        key.setBackgroundTintList(ColorStateList.valueOf(colors.keyBackground));
        key.setStrokeColor(ColorStateList.valueOf(colors.outline));
        key.setStrokeWidth(theme == ThemePreferences.DialerTheme.MINIMAL ? 0 : 1);
        key.setElevation(theme == ThemePreferences.DialerTheme.NEUMORPHIC ? dp(context, 7) : 0f);
    }

    private static void styleUtilityButton(
            @NonNull Context context,
            @NonNull MaterialButton button,
            @NonNull DialColors colors,
            @NonNull ThemePreferences.DialerTheme theme
    ) {
        button.setBackgroundTintList(ColorStateList.valueOf(colors.utilityBackground));
        button.setIconTint(ColorStateList.valueOf(colors.keyText));
        button.setStrokeColor(ColorStateList.valueOf(colors.outline));
        button.setStrokeWidth(theme == ThemePreferences.DialerTheme.MINIMAL ? 0 : 1);
        button.setElevation(theme == ThemePreferences.DialerTheme.NEUMORPHIC ? dp(context, 5) : 0f);
    }

    @NonNull
    private static DialColors colorsFor(@NonNull ThemePreferences.DialerTheme theme) {
        switch (theme) {
            case DARK:
                return new DialColors(
                        Color.rgb(28, 30, 34),
                        Color.WHITE,
                        Color.rgb(43, 47, 54),
                        Color.rgb(20, 22, 26),
                        Color.WHITE,
                        Color.rgb(183, 190, 201),
                        Color.rgb(56, 61, 70)
                );
            case BLUE:
                return new DialColors(
                        Color.rgb(226, 239, 255),
                        Color.rgb(21, 102, 190),
                        Color.rgb(203, 225, 250),
                        Color.rgb(247, 251, 255),
                        Color.rgb(18, 76, 135),
                        Color.rgb(84, 121, 158),
                        Color.rgb(184, 211, 242)
                );
            case GRADIENT:
                return new DialColors(
                        Color.argb(55, 255, 255, 255),
                        Color.WHITE,
                        Color.argb(70, 255, 255, 255),
                        Color.argb(42, 255, 255, 255),
                        Color.WHITE,
                        Color.argb(210, 255, 255, 255),
                        Color.argb(95, 255, 255, 255)
                );
            case MINIMAL:
                return new DialColors(
                        Color.TRANSPARENT,
                        Color.rgb(18, 20, 24),
                        Color.TRANSPARENT,
                        Color.WHITE,
                        Color.rgb(18, 20, 24),
                        Color.rgb(120, 126, 137),
                        Color.TRANSPARENT
                );
            case NEUMORPHIC:
                return new DialColors(
                        Color.rgb(238, 242, 246),
                        Color.rgb(26, 30, 36),
                        Color.rgb(238, 242, 246),
                        Color.rgb(238, 242, 246),
                        Color.rgb(26, 30, 36),
                        Color.rgb(112, 121, 134),
                        Color.rgb(220, 226, 233)
                );
            case BLACK_GREEN:
                return new DialColors(
                        Color.rgb(9, 13, 9),
                        Color.rgb(80, 211, 101),
                        Color.rgb(11, 17, 11),
                        Color.rgb(5, 8, 5),
                        Color.rgb(80, 211, 101),
                        Color.rgb(121, 176, 130),
                        Color.rgb(28, 61, 33)
                );
            case LIGHT:
            default:
                return new DialColors(
                        Color.rgb(245, 246, 248),
                        Color.rgb(24, 27, 32),
                        Color.rgb(250, 250, 251),
                        Color.WHITE,
                        Color.rgb(24, 27, 32),
                        Color.rgb(121, 128, 139),
                        Color.rgb(229, 232, 237)
                );
        }
    }

    private static float dp(@NonNull Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    private static final class DialColors {
        @ColorInt final int keyBackground;
        @ColorInt final int keyText;
        @ColorInt final int utilityBackground;
        @ColorInt final int inputBackground;
        @ColorInt final int numberText;
        @ColorInt final int secondaryText;
        @ColorInt final int outline;

        private DialColors(
                int keyBackground,
                int keyText,
                int utilityBackground,
                int inputBackground,
                int numberText,
                int secondaryText,
                int outline
        ) {
            this.keyBackground = keyBackground;
            this.keyText = keyText;
            this.utilityBackground = utilityBackground;
            this.inputBackground = inputBackground;
            this.numberText = numberText;
            this.secondaryText = secondaryText;
            this.outline = outline;
        }
    }
}
