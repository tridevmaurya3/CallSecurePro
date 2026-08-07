package com.tridev.callsecurepro.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.theme.ThemeStudioActivity;

/**
 * Runtime renderer for the user-selectable visual themes.
 *
 * The theme engine changes appearance only. It does not alter call routing,
 * permissions, caller screening, database state, or telecom behavior.
 */
public final class AppVisualThemeManager {

    private static final int DARK_SURFACE = Color.rgb(25, 29, 36);
    private static final int DARK_SURFACE_ALT = Color.rgb(31, 36, 45);
    private static final int DARK_OUTLINE = Color.rgb(66, 75, 88);
    private static final int DARK_TEXT = Color.rgb(245, 247, 250);
    private static final int DARK_TEXT_SECONDARY = Color.rgb(198, 205, 216);
    private static final int LIGHT_NAV_SURFACE = Color.argb(246, 255, 255, 255);
    private static final int DARK_NAV_SURFACE = Color.argb(246, 20, 24, 31);

    private AppVisualThemeManager() {
    }

    public static void applyActivity(@NonNull Activity activity) {
        if (activity instanceof ThemeStudioActivity) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            if (group.getChildCount() > 0) {
                applyRoot(activity, group.getChildAt(0));
            } else {
                applyRoot(activity, content);
            }
        } else if (content != null) {
            applyRoot(activity, content);
        }

        boolean dark = isDarkBackground(activity);
        @ColorInt int barColor = dark ? Color.rgb(13, 17, 24) : Color.TRANSPARENT;
        activity.getWindow().setStatusBarColor(barColor);
        activity.getWindow().setNavigationBarColor(
                dark ? Color.rgb(13, 17, 24) : Color.rgb(247, 249, 252)
        );
    }

    public static void applyRoot(@NonNull Context context, @NonNull View root) {
        root.setBackground(createBackground(context));
        if (isDarkBackground(context)) {
            styleDarkTree(context, root);
        }
    }

    public static void applyMainNavigation(
            @NonNull Context context,
            @NonNull View surface,
            @NonNull BottomNavigationView navigation
    ) {
        boolean dark = isDarkBackground(context);
        int surfaceColor = dark ? DARK_NAV_SURFACE : LIGHT_NAV_SURFACE;
        int outlineColor = dark ? DARK_OUTLINE : ContextCompat.getColor(context, R.color.csp_outline);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(surfaceColor);
        background.setCornerRadius(dp(context, 26));
        background.setStroke(Math.max(1, Math.round(dp(context, 1))), outlineColor);
        surface.setBackground(background);

        int accent = accentColor(context);
        int inactive = dark
                ? Color.rgb(174, 184, 198)
                : ContextCompat.getColor(context, R.color.csp_text_muted);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{accent, inactive};
        ColorStateList itemColors = new ColorStateList(states, colors);
        navigation.setItemIconTintList(itemColors);
        navigation.setItemTextColor(itemColors);
        navigation.setItemRippleColor(ColorStateList.valueOf(withAlpha(accent, 28)));
        navigation.setBackgroundColor(Color.TRANSPARENT);
    }

    @NonNull
    public static Drawable createBackground(@NonNull Context context) {
        ThemePreferences.BackgroundTheme theme = ThemePreferences.getBackgroundTheme(context);
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TL_BR;
        int[] colors;

        switch (theme) {
            case ABSTRACT_BLUE:
                colors = new int[]{
                        Color.rgb(230, 241, 255),
                        Color.rgb(213, 232, 255),
                        Color.rgb(244, 248, 255)
                };
                break;
            case DARK_ABSTRACT:
                colors = new int[]{
                        Color.rgb(8, 11, 16),
                        Color.rgb(25, 29, 37),
                        Color.rgb(10, 13, 19)
                };
                break;
            case GREEN_NATURE:
                colors = new int[]{
                        Color.rgb(235, 249, 234),
                        Color.rgb(218, 243, 220),
                        Color.rgb(246, 252, 241)
                };
                break;
            case SUNSET:
                colors = new int[]{
                        Color.rgb(255, 239, 222),
                        Color.rgb(255, 220, 219),
                        Color.rgb(255, 242, 232)
                };
                break;
            case PURPLE_GRADIENT:
                colors = new int[]{
                        Color.rgb(240, 232, 255),
                        Color.rgb(225, 215, 255),
                        Color.rgb(248, 241, 255)
                };
                break;
            case NIGHT_SKY:
                colors = new int[]{
                        Color.rgb(5, 19, 46),
                        Color.rgb(12, 35, 72),
                        Color.rgb(9, 16, 35)
                };
                orientation = GradientDrawable.Orientation.TOP_BOTTOM;
                break;
            case OCEAN:
                colors = new int[]{
                        Color.rgb(224, 247, 252),
                        Color.rgb(210, 239, 248),
                        Color.rgb(239, 250, 251)
                };
                break;
            case GEOMETRIC:
                colors = new int[]{
                        Color.rgb(244, 238, 222),
                        Color.rgb(225, 241, 239),
                        Color.rgb(255, 226, 207)
                };
                orientation = GradientDrawable.Orientation.LEFT_RIGHT;
                break;
            case MINIMAL_WHITE:
            default:
                colors = new int[]{
                        Color.rgb(250, 251, 253),
                        Color.rgb(245, 248, 252),
                        Color.rgb(255, 255, 255)
                };
                break;
        }

        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setShape(GradientDrawable.RECTANGLE);
        return drawable;
    }

    public static boolean isDarkBackground(@NonNull Context context) {
        ThemePreferences.BackgroundTheme theme = ThemePreferences.getBackgroundTheme(context);
        return theme == ThemePreferences.BackgroundTheme.DARK_ABSTRACT
                || theme == ThemePreferences.BackgroundTheme.NIGHT_SKY;
    }

    @ColorInt
    public static int accentColor(@NonNull Context context) {
        switch (ThemePreferences.getAccentPalette(context)) {
            case FRESH_GREEN:
                return Color.rgb(39, 174, 96);
            case SUNSET_ORANGE:
                return Color.rgb(245, 125, 35);
            case ROYAL_PURPLE:
                return Color.rgb(126, 67, 191);
            case CHERRY_RED:
                return Color.rgb(222, 54, 72);
            case DARK_MODE:
                return Color.rgb(79, 88, 101);
            case PASTEL_MIX:
                return Color.rgb(170, 103, 200);
            case MATERIAL_YOU:
                return Color.rgb(91, 106, 191);
            case MONOCHROME:
                return Color.rgb(55, 60, 68);
            case OCEAN_BLUE:
            default:
                return Color.rgb(26, 115, 232);
        }
    }

    @ColorInt
    public static int contrastOn(@ColorInt int color) {
        return Color.luminance(color) > 0.55f ? Color.rgb(20, 24, 31) : Color.WHITE;
    }

    private static void styleDarkTree(@NonNull Context context, @NonNull View view) {
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setCardBackgroundColor(DARK_SURFACE_ALT);
            card.setStrokeColor(DARK_OUTLINE);
        }

        if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;
            inputLayout.setBoxBackgroundColor(DARK_SURFACE);
            inputLayout.setBoxStrokeColor(DARK_OUTLINE);
            inputLayout.setDefaultHintTextColor(ColorStateList.valueOf(DARK_TEXT_SECONDARY));
        }

        if (view instanceof TextInputEditText) {
            TextInputEditText editText = (TextInputEditText) view;
            editText.setTextColor(DARK_TEXT);
            editText.setHintTextColor(DARK_TEXT_SECONDARY);
        } else if (view instanceof TextView && !(view instanceof Chip)) {
            TextView textView = (TextView) view;
            int current = textView.getCurrentTextColor();
            if (!isSemanticColor(context, current)) {
                textView.setTextColor(
                        isPrimaryTextColor(context, current) ? DARK_TEXT : DARK_TEXT_SECONDARY
                );
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleDarkTree(context, group.getChildAt(index));
            }
        }
    }

    private static boolean isPrimaryTextColor(@NonNull Context context, @ColorInt int color) {
        return color == ContextCompat.getColor(context, R.color.csp_text_primary)
                || color == ContextCompat.getColor(context, R.color.csp_on_surface)
                || color == ContextCompat.getColor(context, R.color.csp_on_background);
    }

    private static boolean isSemanticColor(@NonNull Context context, @ColorInt int color) {
        return color == ContextCompat.getColor(context, R.color.csp_safe)
                || color == ContextCompat.getColor(context, R.color.csp_verified)
                || color == ContextCompat.getColor(context, R.color.csp_business)
                || color == ContextCompat.getColor(context, R.color.csp_unknown)
                || color == ContextCompat.getColor(context, R.color.csp_spam)
                || color == ContextCompat.getColor(context, R.color.csp_warning)
                || color == ContextCompat.getColor(context, R.color.csp_call_incoming)
                || color == ContextCompat.getColor(context, R.color.csp_call_outgoing)
                || color == ContextCompat.getColor(context, R.color.csp_call_missed)
                || color == ContextCompat.getColor(context, R.color.csp_call_blocked);
    }

    @ColorInt
    private static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static float dp(@NonNull Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
