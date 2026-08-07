package com.tridev.callsecurepro.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tridev.callsecurepro.R;

/** Runtime renderer for the user-selectable whole-app visual system. */
public final class AppVisualThemeManager {

    private static final int DARK_NAV_SURFACE = Color.argb(248, 18, 23, 31);
    private static final int LIGHT_NAV_SURFACE = Color.argb(248, 255, 255, 255);

    private AppVisualThemeManager() {
    }

    public static void applyActivity(@NonNull Activity activity) {
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
        applyWindowChrome(activity);
    }

    public static void applyWindowChrome(@NonNull Activity activity) {
        boolean dark = isDarkBackground(activity);
        activity.getWindow().setStatusBarColor(
                dark ? Color.rgb(10, 15, 23) : Color.TRANSPARENT
        );
        activity.getWindow().setNavigationBarColor(
                dark ? Color.rgb(10, 15, 23) : Color.rgb(247, 249, 252)
        );

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(
                activity.getWindow(),
                activity.getWindow().getDecorView()
        );
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    public static void applyRoot(@NonNull Context context, @NonNull View root) {
        root.setBackground(createBackground(context));
        SurfacePalette palette = paletteFor(context);
        styleTree(context, root, palette);
    }

    public static void applyMainNavigation(
            @NonNull Context context,
            @NonNull View surface,
            @NonNull BottomNavigationView navigation
    ) {
        boolean dark = isDarkBackground(context);
        SurfacePalette palette = paletteFor(context);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(dark ? DARK_NAV_SURFACE : LIGHT_NAV_SURFACE);
        background.setCornerRadius(dp(context, 26));
        background.setStroke(
                Math.max(1, Math.round(dp(context, 1))),
                palette.outline
        );
        surface.setBackground(background);

        int accent = accentColor(context);
        int inactive = dark ? Color.rgb(174, 184, 198) : palette.secondaryText;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        ColorStateList itemColors = new ColorStateList(
                states,
                new int[]{accent, inactive}
        );
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
                        Color.rgb(229, 241, 255),
                        Color.rgb(213, 232, 255),
                        Color.rgb(244, 249, 255)
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
                        Color.rgb(233, 249, 233),
                        Color.rgb(216, 241, 219),
                        Color.rgb(247, 252, 242)
                };
                break;
            case SUNSET:
                colors = new int[]{
                        Color.rgb(255, 239, 220),
                        Color.rgb(255, 220, 218),
                        Color.rgb(255, 244, 233)
                };
                break;
            case PURPLE_GRADIENT:
                colors = new int[]{
                        Color.rgb(239, 231, 255),
                        Color.rgb(224, 214, 255),
                        Color.rgb(249, 242, 255)
                };
                break;
            case NIGHT_SKY:
                colors = new int[]{
                        Color.rgb(5, 18, 44),
                        Color.rgb(12, 34, 69),
                        Color.rgb(8, 15, 33)
                };
                orientation = GradientDrawable.Orientation.TOP_BOTTOM;
                break;
            case OCEAN:
                colors = new int[]{
                        Color.rgb(222, 247, 252),
                        Color.rgb(207, 239, 248),
                        Color.rgb(239, 251, 252)
                };
                break;
            case GEOMETRIC:
                colors = new int[]{
                        Color.rgb(245, 239, 223),
                        Color.rgb(224, 241, 239),
                        Color.rgb(255, 226, 207)
                };
                orientation = GradientDrawable.Orientation.LEFT_RIGHT;
                break;
            case MINIMAL_WHITE:
            default:
                colors = new int[]{
                        Color.rgb(250, 251, 253),
                        Color.rgb(245, 248, 252),
                        Color.WHITE
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
        return Color.luminance(color) > 0.55f
                ? Color.rgb(20, 24, 31)
                : Color.WHITE;
    }

    private static void styleTree(
            @NonNull Context context,
            @NonNull View view,
            @NonNull SurfacePalette palette
    ) {
        int accent = accentColor(context);
        int defaultPrimary = ContextCompat.getColor(context, R.color.csp_primary);

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setCardBackgroundColor(palette.surface);
            card.setStrokeColor(palette.outline);
        }

        if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;
            inputLayout.setBoxBackgroundColor(palette.inputSurface);
            inputLayout.setBoxStrokeColor(palette.outline);
            inputLayout.setDefaultHintTextColor(ColorStateList.valueOf(palette.secondaryText));
        }

        if (view instanceof TextInputEditText) {
            TextInputEditText editText = (TextInputEditText) view;
            editText.setTextColor(palette.primaryText);
            editText.setHintTextColor(palette.secondaryText);
        } else if (view instanceof TextView
                && !(view instanceof Chip)
                && !(view instanceof MaterialButton)) {
            TextView textView = (TextView) view;
            int current = textView.getCurrentTextColor();
            if (!isSemanticColor(context, current)) {
                textView.setTextColor(
                        isPrimaryTextColor(context, current)
                                ? palette.primaryText
                                : palette.secondaryText
                );
            }
        }

        if (view instanceof FloatingActionButton) {
            FloatingActionButton fab = (FloatingActionButton) view;
            fab.setBackgroundTintList(ColorStateList.valueOf(accent));
            fab.setImageTintList(ColorStateList.valueOf(contrastOn(accent)));
        }

        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            ColorStateList tint = button.getBackgroundTintList();
            boolean primaryFill = tint != null && tint.getDefaultColor() == defaultPrimary;
            if (primaryFill) {
                button.setBackgroundTintList(ColorStateList.valueOf(accent));
                button.setTextColor(contrastOn(accent));
                if (button.getIcon() != null) {
                    button.setIconTint(ColorStateList.valueOf(contrastOn(accent)));
                }
            } else if (button.getCurrentTextColor() == defaultPrimary) {
                button.setTextColor(accent);
                if (button.getIcon() != null) {
                    button.setIconTint(ColorStateList.valueOf(accent));
                }
            }
        }

        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            ColorStateList tint = imageView.getImageTintList();
            if (tint != null && tint.getDefaultColor() == defaultPrimary) {
                imageView.setImageTintList(ColorStateList.valueOf(accent));
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTree(context, group.getChildAt(index), palette);
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

    @NonNull
    private static SurfacePalette paletteFor(@NonNull Context context) {
        switch (ThemePreferences.getBackgroundTheme(context)) {
            case ABSTRACT_BLUE:
                return new SurfacePalette(
                        Color.rgb(244, 249, 255),
                        Color.rgb(240, 247, 255),
                        Color.rgb(193, 216, 241),
                        Color.rgb(22, 38, 58),
                        Color.rgb(78, 96, 119)
                );
            case DARK_ABSTRACT:
                return new SurfacePalette(
                        Color.rgb(27, 32, 41),
                        Color.rgb(22, 27, 35),
                        Color.rgb(69, 79, 94),
                        Color.rgb(246, 248, 251),
                        Color.rgb(190, 200, 214)
                );
            case GREEN_NATURE:
                return new SurfacePalette(
                        Color.rgb(246, 252, 245),
                        Color.rgb(242, 250, 241),
                        Color.rgb(197, 224, 199),
                        Color.rgb(24, 47, 31),
                        Color.rgb(80, 105, 86)
                );
            case SUNSET:
                return new SurfacePalette(
                        Color.rgb(255, 249, 244),
                        Color.rgb(255, 246, 239),
                        Color.rgb(238, 205, 187),
                        Color.rgb(55, 36, 31),
                        Color.rgb(112, 83, 73)
                );
            case PURPLE_GRADIENT:
                return new SurfacePalette(
                        Color.rgb(250, 247, 255),
                        Color.rgb(247, 243, 255),
                        Color.rgb(215, 201, 238),
                        Color.rgb(43, 31, 62),
                        Color.rgb(96, 80, 119)
                );
            case NIGHT_SKY:
                return new SurfacePalette(
                        Color.rgb(17, 29, 49),
                        Color.rgb(14, 25, 43),
                        Color.rgb(56, 78, 111),
                        Color.rgb(245, 248, 253),
                        Color.rgb(184, 199, 220)
                );
            case OCEAN:
                return new SurfacePalette(
                        Color.rgb(245, 252, 253),
                        Color.rgb(240, 250, 252),
                        Color.rgb(190, 222, 230),
                        Color.rgb(22, 46, 53),
                        Color.rgb(75, 103, 111)
                );
            case GEOMETRIC:
                return new SurfacePalette(
                        Color.rgb(255, 252, 246),
                        Color.rgb(251, 249, 242),
                        Color.rgb(219, 208, 187),
                        Color.rgb(48, 43, 34),
                        Color.rgb(103, 94, 77)
                );
            case MINIMAL_WHITE:
            default:
                return new SurfacePalette(
                        Color.WHITE,
                        Color.rgb(252, 253, 255),
                        ContextCompat.getColor(context, R.color.csp_outline),
                        ContextCompat.getColor(context, R.color.csp_text_primary),
                        ContextCompat.getColor(context, R.color.csp_text_secondary)
                );
        }
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

    private static final class SurfacePalette {
        final int surface;
        final int inputSurface;
        final int outline;
        final int primaryText;
        final int secondaryText;

        SurfacePalette(
                int surface,
                int inputSurface,
                int outline,
                int primaryText,
                int secondaryText
        ) {
            this.surface = surface;
            this.inputSurface = inputSurface;
            this.outline = outline;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
        }
    }
}
