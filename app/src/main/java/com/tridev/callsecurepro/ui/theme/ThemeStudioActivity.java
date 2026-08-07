package com.tridev.callsecurepro.ui.theme;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        setupDropdowns();
        updateSelections();
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

    private void setupDropdowns() {
        ThemePreferences.BackgroundTheme[] backgrounds = ThemePreferences.BackgroundTheme.values();
        String[] backgroundLabels = labelsForBackgrounds(backgrounds);
        binding.backgroundThemeDropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                backgroundLabels
        ));
        binding.backgroundThemeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            ThemePreferences.setBackgroundTheme(this, backgrounds[position]);
            onThemeChanged();
        });

        ThemePreferences.AccentPalette[] accents = ThemePreferences.AccentPalette.values();
        String[] accentLabels = labelsForAccents(accents);
        binding.accentPaletteDropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                accentLabels
        ));
        binding.accentPaletteDropdown.setOnItemClickListener((parent, view, position, id) -> {
            ThemePreferences.setAccentPalette(this, accents[position]);
            onThemeChanged();
        });

        ThemePreferences.DialerTheme[] dialers = ThemePreferences.DialerTheme.values();
        String[] dialerLabels = labelsForDialers(dialers);
        binding.dialerThemeDropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                dialerLabels
        ));
        binding.dialerThemeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            ThemePreferences.setDialerTheme(this, dialers[position]);
            onThemeChanged();
        });

        ThemePreferences.CallButtonStyle[] callButtons = ThemePreferences.CallButtonStyle.values();
        String[] callButtonLabels = labelsForCallButtons(callButtons);
        binding.callButtonStyleDropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                callButtonLabels
        ));
        binding.callButtonStyleDropdown.setOnItemClickListener((parent, view, position, id) -> {
            ThemePreferences.setCallButtonStyle(this, callButtons[position]);
            onThemeChanged();
        });
    }

    private void onThemeChanged() {
        updateSelections();
        updatePreview();
        Toast.makeText(this, R.string.theme_studio_saved, Toast.LENGTH_SHORT).show();
    }

    private void updateSelections() {
        binding.backgroundThemeDropdown.setText(
                getString(backgroundLabel(ThemePreferences.getBackgroundTheme(this))),
                false
        );
        binding.accentPaletteDropdown.setText(
                getString(accentLabel(ThemePreferences.getAccentPalette(this))),
                false
        );
        binding.dialerThemeDropdown.setText(
                getString(dialerLabel(ThemePreferences.getDialerTheme(this))),
                false
        );
        binding.callButtonStyleDropdown.setText(
                getString(callButtonLabel(ThemePreferences.getCallButtonStyle(this))),
                false
        );
    }

    private void updatePreview() {
        AppVisualThemeManager.applyRoot(this, binding.themeStudioRoot);
        AppVisualThemeManager.applyWindowChrome(this);
        binding.previewSurface.setBackground(AppVisualThemeManager.createBackground(this));

        int accent = AppVisualThemeManager.accentColor(this);
        int textColor = AppVisualThemeManager.isDarkBackground(this)
                ? Color.WHITE
                : Color.rgb(25, 30, 38);

        binding.previewTitle.setTextColor(textColor);
        binding.previewTitle.setText(
                getString(backgroundLabel(ThemePreferences.getBackgroundTheme(this)))
                        + " • "
                        + getString(dialerLabel(ThemePreferences.getDialerTheme(this)))
        );

        stylePreviewKey(binding.previewKey1, accent, textColor);
        stylePreviewKey(binding.previewKey2, accent, textColor);
        DialVisualStyler.styleCallButton(
                this,
                binding.previewCallButton,
                ThemePreferences.getCallButtonStyle(this)
        );
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

    private void resetTheme() {
        ThemePreferences.reset(this);
        updateSelections();
        updatePreview();
        Toast.makeText(this, R.string.theme_studio_reset_done, Toast.LENGTH_SHORT).show();
    }

    private String[] labelsForBackgrounds(ThemePreferences.BackgroundTheme[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(backgroundLabel(values[i]));
        }
        return labels;
    }

    private String[] labelsForAccents(ThemePreferences.AccentPalette[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(accentLabel(values[i]));
        }
        return labels;
    }

    private String[] labelsForDialers(ThemePreferences.DialerTheme[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(dialerLabel(values[i]));
        }
        return labels;
    }

    private String[] labelsForCallButtons(ThemePreferences.CallButtonStyle[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(callButtonLabel(values[i]));
        }
        return labels;
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
