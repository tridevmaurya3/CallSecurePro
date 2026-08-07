package com.tridev.callsecurepro.calls;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.R;

public final class CallNotificationChannels {

    public static final String FOLLOW_UP_CHANNEL_ID = "call_follow_up";
    public static final String POST_CALL_CHANNEL_ID = "post_call_actions";

    private CallNotificationChannels() {
        // Utility class.
    }

    public static void ensureCreated(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = ContextCompat.getSystemService(
                context.getApplicationContext(),
                NotificationManager.class
        );
        if (manager == null) {
            return;
        }

        NotificationChannel followUp = new NotificationChannel(
                FOLLOW_UP_CHANNEL_ID,
                context.getString(R.string.call_reminder_channel_follow_up),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        followUp.setDescription(context.getString(R.string.call_reminder_channel_follow_up_body));

        NotificationChannel postCall = new NotificationChannel(
                POST_CALL_CHANNEL_ID,
                context.getString(R.string.call_reminder_channel_post_call),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        postCall.setDescription(context.getString(R.string.call_reminder_channel_post_call_body));

        manager.createNotificationChannel(followUp);
        manager.createNotificationChannel(postCall);
    }
}
