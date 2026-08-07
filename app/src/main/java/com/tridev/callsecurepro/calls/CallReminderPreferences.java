package com.tridev.callsecurepro.calls;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Local-only user preferences for reminder-related call experiences.
 */
public final class CallReminderPreferences {

    private static final String PREFS_NAME = "call_reminder_preferences";
    private static final String KEY_POST_CALL_PROMPT = "post_call_prompt";

    private CallReminderPreferences() {
        // Utility class.
    }

    public static boolean isPostCallPromptEnabled(@NonNull Context context) {
        return preferences(context).getBoolean(KEY_POST_CALL_PROMPT, false);
    }

    public static void setPostCallPromptEnabled(@NonNull Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_POST_CALL_PROMPT, enabled).apply();
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }
}
