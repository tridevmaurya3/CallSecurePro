package com.tridev.callsecurepro.theme;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tridev.callsecurepro.MainActivity;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.incall.InCallActivity;

/** Adds the same dial shortcut to secondary app activities without overlay permission. */
public final class GlobalDialButtonManager {

    private static final String TAG = "call_secure_global_dial_fab";
    private static final float BOTTOM_CLEARANCE_DP = 72f;

    private GlobalDialButtonManager() {
    }

    public static void ensure(@NonNull Activity activity) {
        if (activity instanceof MainActivity || activity instanceof InCallActivity) {
            return;
        }

        View contentView = activity.findViewById(android.R.id.content);
        if (!(contentView instanceof FrameLayout)) {
            return;
        }
        FrameLayout content = (FrameLayout) contentView;

        View existing = content.findViewWithTag(TAG);
        FloatingActionButton button;
        if (existing instanceof FloatingActionButton) {
            button = (FloatingActionButton) existing;
        } else {
            button = new FloatingActionButton(activity);
            button.setTag(TAG);
            button.setImageResource(R.drawable.ic_nav_dial);
            button.setContentDescription(activity.getString(R.string.home_action_dial_title));
            button.setSize(FloatingActionButton.SIZE_NORMAL);
            button.setCompatElevation(dp(activity, 6));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Math.round(dp(activity, 56)),
                    Math.round(dp(activity, 56)),
                    Gravity.END | Gravity.BOTTOM
            );
            params.setMarginEnd(Math.round(dp(activity, 20)));
            params.bottomMargin = Math.round(dp(activity, BOTTOM_CLEARANCE_DP));
            content.addView(button, params);

            button.setOnClickListener(view -> {
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setAction(Intent.ACTION_DIAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            });

            ViewCompat.setOnApplyWindowInsetsListener(button, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin = bars.bottom
                        + Math.round(dp(activity, BOTTOM_CLEARANCE_DP));
                view.setLayoutParams(layoutParams);
                return insets;
            });
            ViewCompat.requestApplyInsets(button);
        }

        int accent = AppVisualThemeManager.accentColor(activity);
        button.setBackgroundTintList(ColorStateList.valueOf(accent));
        button.setImageTintList(ColorStateList.valueOf(AppVisualThemeManager.contrastOn(accent)));
        button.setRippleColor(Color.argb(40, 255, 255, 255));
        button.bringToFront();
    }

    private static float dp(@NonNull Activity activity, float value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }
}
