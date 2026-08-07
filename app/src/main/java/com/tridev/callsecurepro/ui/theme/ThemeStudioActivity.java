package com.tridev.callsecurepro.ui.theme;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityThemeStudioBinding;
import com.tridev.callsecurepro.theme.AppVisualThemeManager;
import com.tridev.callsecurepro.theme.DialVisualStyler;
import com.tridev.callsecurepro.theme.ThemePreferences;

public class ThemeStudioActivity extends AppCompatActivity {

    private ActivityThemeStudioBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityThemeStudioBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyInsets();
        binding.backButton.setOnClickListener(view -> finish());
        binding.resetButton.setOnClickListener(view -> resetTheme());
        rebuildSelectors();
        updatePreview();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.themeStudioRoot, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    Math.max(view.getPaddingLeft(), bars.left),
                    Math.max(view.getPaddingTop(), bars.top),
                    Math.max(view.getPaddingRight(), bars.right),
                    Math.max(view.getPaddingBottom(), bars.bottom)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.themeStudioRoot);
    }

    private void rebuildSelectors() {
        binding.backgroundThemeGroup.removeAllViews();
        binding.accentPaletteGroup.removeAllViews();
        binding.dialerThemeGroup.removeAllViews();
        binding.callButtonStyleGroup.removeAllViews();

        for (ThemePreferences.BackgroundTheme value : ThemePreferences.BackgroundTheme.values()) {
            addChip(
                    binding.backgroundThemeGroup,
                    getString(backgroundLabel(value)),
                    value == ThemePreferences.getBackgroundTheme(this),
                    () -> {
                        ThemePreferences.setBackgroundTheme(this, value);
                        updatePreview();
                    }
            );
        }

        for (ThemePreferences.AccentPalette value : ThemePreferences.AccentPalette.values()) {
            addChip(
                    binding.accentPaletteGroup,
                    getString(accentLabel(value)),
                    value == ThemePreferences.getAccentPalette(this),
                    () -> {
                        ThemePreferences.setAccentPalette(this, value);
                        updatePreview();
                    }
            );
        }

        for (ThemePreferences.DialerTheme value : ThemePreferences.DialerTheme.values()) {
            addChip(
                    binding.dialerThemeGroup,
                    getString(dialerLabel(value)),
                    value == ThemePreferences.getDialerTheme(this),
                    () -> {
                        ThemePreferences.setDialerTheme(this, value);
                        updatePreview();
                    }
            );
        }

        for (ThemePreferences.CallButtonStyle value : ThemePreferences.CallButtonStyle.values()) {
            addChip(
                    binding.callButtonStyleGroup,
                    getString(callButtonLabel(value)),
                    value == ThemePreferences.getCallButtonStyle(this),
                    () -> {
                        ThemePreferences.setCallButtonStyle(this, value);
                        updatePreview();
                    }
            );
        }
    }

    private void addChip(
            @NonNull ChipGroup group,
            @NonNull String label,
            boolean checked,
            @NonNull Runnable onSelected
    ) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setTextSize(13f);
        chip.setOnClickListener(view -> {
            if (chip.isChecked()) {
                onSelected.run();
                Toast.makeText(this, R.string.theme_studio_saved, Toast.LENGTH_SHORT).show();
            }
        });
        group.addView(chip);
    }

    private void updatePreview() {
        binding.previewSurface.setBackground(AppVisualThemeManager.createBackground(this));

        int accent = AppVisualThemeManager.accentColor(this);
        int textColor = AppVisualThemeManager.isDarkBackground(this)
                ? Color.WHITE
                : Color.rgb(25, 30, 38);
        int secondary = AppVisualThemeManager.isDarkBackground(this)
                ? Color.rgb(210, 216, 226)
                : Color.rgb(92, 103, 118);

        binding.previewTitle.setTextColor(textColor);
        stylePreviewKey(binding.previewKey1, accent, textColor);
        stylePreviewKey(binding.previewKey2, accent, textColor);
        DialVisualStyler.styleCallButton(
                this,
                binding.previewCallButton,
                ThemePreferences.getCallButtonStyle(this)
        );

        binding.previewTitle.setText(
                getString(backgroundLabel(ThemePreferences.getBackgroundTheme(this)))
                        + " • "
                        + getString(dialerLabel(ThemePreferences.getDialerTheme(this)))
        );
        binding.previewKey1.setTextColor(textColor);
        binding.previewKey2.setTextColor(textColor);
        binding.previewKey1.setAlpha(0.95f);
        binding.previewKey2.setAlpha(0.95f);

        if (binding.previewSurface instanceof View) {
            binding.previewSurface.setContentDescription(
                    getString(themePreviewDescriptionResource(), secondary)
            );
        }
    }

    private void stylePreviewKey(
            @NonNull TextView view,
            int accent,
            int textColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent)));
        drawable.setStroke(Math.max(1, Math.round(dp(1))), Color.argb(
                80,
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent)
        ));
        view.setBackground(drawable);
        view.setTextColor(textColor);
    }

    private int themePreviewDescriptionResource() {
        return R.string.theme_studio_preview_body;
    }

    private void resetTheme() {
        ThemePreferences.reset(this);
        rebuildSelectors();
        updatePreview();
        Toast.makeText(this, R.string.theme_studio_reset_done, Toast.LENGTH_SHORT).show();
    }

    private int backgroundLabel(@NonNull ThemePreferences.BackgroundTheme value) {
        switch (value) {
            case ABSTRACT_BLUE:
                return R.string.theme_bg_abstract_blue;
            case DARK_ABSTRACT:
                return R.string.theme_bg_dark_abstract;
            case GREEN_NATURE:
                return R.string.theme_bg_green_nature;
            case SUNSET:
                return R.string.theme_bg_sunset;
            case PURPLE_GRADIENT:
                return R.string.theme_bg_purple_gradient;
            case NIGHT_SKY:
                return R.string.theme_bg_night_sky;
            case OCEAN:
                return R.string.theme_bg_ocean;
            case GEOMETRIC:
                return R.string.theme_bg_geometric;
            case MINIMAL_WHITE:
            default:
                return R.string.theme_bg_minimal_white;
        }
    }

    private int accentLabel(@NonNull ThemePreferences.AccentPalette value) {
        switch (value) {
            case FRESH_GREEN:
                return R.string.theme_accent_fresh_green;
            case SUNSET_ORANGE:
                return R.string.theme_accent_sunset_orange;
            case ROYAL_PURPLE:
                return R.string.theme_accent_royal_purple;
            case CHERRY_RED:
                return R.string.theme_accent_cherry_red;
            case DARK_MODE:
                return R.string.theme_accent_dark_mode;
            case PASTEL_MIX:
                return R.string.theme_accent_pastel_mix;
            case MATERIAL_YOU:
                return R.string.theme_accent_material_you;
            case MONOCHROME:
                return R.string.theme_accent_monochrome;
            case OCEAN_BLUE:
            default:
                return R.string.theme_accent_ocean_blue;
        }
    }

    private int dialerLabel(@NonNull ThemePreferences.DialerTheme value) {
        switch (value) {
            case DARK:
                return R.string.theme_dial_dark;
            case BLUE:
                return R.string.theme_dial_blue;
            case GRADIENT:
                return R.string.theme_dial_gradient;
            case MINIMAL:
                return R.string.theme_dial_minimal;
            case NEUMORPHIC:
                return R.string.theme_dial_neumorphic;
            case BLACK_GREEN:
                return R.string.theme_dial_black_green;
            case LIGHT:
            default:
                return R.string.theme_dial_light;
        }
    }

    private int callButtonLabel(@NonNull ThemePreferences.CallButtonStyle value) {
        switch (value) {
            case GRADIENT_GREEN:
                return R.string.theme_call_gradient_green;
            case BLUE_CIRCLE:
                return R.string.theme_call_blue_circle;
            case BLUE_ROUNDED:
                return R.string.theme_call_blue_rounded;
            case PURPLE_CIRCLE:
                return R.string.theme_call_purple_circle;
            case ORANGE_CIRCLE:
                return R.string.theme_call_orange_circle;
            case RED_CIRCLE:
                return R.string.theme_call_red_circle;
            case DARK_CIRCLE:
                return R.string.theme_call_dark_circle;
            case DARK_ROUNDED:
                return R.string.theme_call_dark_rounded;
            case LIGHT_OUTLINE:
                return R.string.theme_call_light_outline;
            case CLASSIC_GREEN:
            default:
                return R.string.theme_call_classic_green;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
