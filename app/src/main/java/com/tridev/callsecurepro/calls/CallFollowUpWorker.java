package com.tridev.callsecurepro.calls;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;
import com.tridev.callsecurepro.ui.calls.CallDetailActivity;

public class CallFollowUpWorker extends Worker {

    private static final long EARLY_TOLERANCE_MS = 60_000L;

    public CallFollowUpWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long callLogId = getInputData().getLong(CallReminderScheduler.INPUT_CALL_LOG_ID, -1L);
        if (callLogId < 0L) {
            return Result.success();
        }

        CallNoteRepository repository = new CallNoteRepository(getApplicationContext());
        CallNoteEntity note = repository.find(callLogId);
        if (note == null || note.followUpAt <= 0L || note.followUpDone) {
            return Result.success();
        }

        long remaining = note.followUpAt - System.currentTimeMillis();
        if (remaining > EARLY_TOLERANCE_MS) {
            CallReminderScheduler.scheduleFollowUp(
                    getApplicationContext(),
                    callLogId,
                    note.followUpAt
            );
            return Result.success();
        }

        if (!canPostNotifications()) {
            return Result.success();
        }

        CallNotificationChannels.ensureCreated(getApplicationContext());
        CallDisplayInfo displayInfo = loadCallDisplayInfo(callLogId, note.normalizedNumber);

        Intent openIntent = new Intent(getApplicationContext(), CallDetailActivity.class);
        openIntent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_ID, callLogId);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(),
                requestCode(callLogId, 0),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent doneIntent = actionIntent(
                callLogId,
                CallReminderActionReceiver.ACTION_DONE,
                1
        );
        PendingIntent snoozeIntent = actionIntent(
                callLogId,
                CallReminderActionReceiver.ACTION_SNOOZE,
                2
        );

        String title = getApplicationContext().getString(
                R.string.call_reminder_follow_up_title,
                displayInfo.title
        );
        String body = note.noteText == null || note.noteText.trim().isEmpty()
                ? getApplicationContext().getString(R.string.call_reminder_follow_up_body)
                : note.noteText.trim();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(),
                CallNotificationChannels.FOLLOW_UP_CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, getApplicationContext().getString(R.string.call_reminder_done), doneIntent)
                .addAction(0, getApplicationContext().getString(R.string.call_reminder_snooze), snoozeIntent);

        try {
            NotificationManagerCompat.from(getApplicationContext()).notify(
                    notificationId(callLogId),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // Notification permission can be changed by the user at any time.
        }

        return Result.success();
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private PendingIntent actionIntent(long callLogId, @NonNull String action, int offset) {
        Intent intent = new Intent(getApplicationContext(), CallReminderActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(CallReminderActionReceiver.EXTRA_CALL_LOG_ID, callLogId);
        return PendingIntent.getBroadcast(
                getApplicationContext(),
                requestCode(callLogId, offset),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @NonNull
    private CallDisplayInfo loadCallDisplayInfo(long callLogId, @Nullable String fallbackNumber) {
        if (ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED) {
            return new CallDisplayInfo(readableFallback(fallbackNumber));
        }

        try (Cursor cursor = getApplicationContext().getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER},
                CallLog.Calls._ID + " = ?",
                new String[]{String.valueOf(callLogId)},
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                String number = numberIndex >= 0 ? cursor.getString(numberIndex) : fallbackNumber;
                if (name != null && !name.trim().isEmpty()) {
                    return new CallDisplayInfo(name.trim());
                }
                return new CallDisplayInfo(readableFallback(number));
            }
        } catch (SecurityException ignored) {
            // Fall through to local note data.
        }

        return new CallDisplayInfo(readableFallback(fallbackNumber));
    }

    @NonNull
    private String readableFallback(@Nullable String number) {
        if (number == null || number.trim().isEmpty()) {
            return getApplicationContext().getString(R.string.calls_unknown_number);
        }
        return number.trim();
    }

    static int notificationId(long callLogId) {
        return 0x46000000 | (int) (Math.abs(callLogId) & 0x00FFFFFF);
    }

    private int requestCode(long callLogId, int offset) {
        return (int) ((callLogId ^ (callLogId >>> 32)) & 0x7FFFFFFF) + offset;
    }

    private static final class CallDisplayInfo {
        @NonNull
        private final String title;

        private CallDisplayInfo(@NonNull String title) {
            this.title = title;
        }
    }
}
