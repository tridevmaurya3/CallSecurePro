package com.tridev.callsecurepro.calls;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.calls.CallDetailActivity;

public class PostCallActionWorker extends Worker {

    private static final long MATCH_WINDOW_MS = 2L * 60L * 1000L;
    private static final int MAX_ROWS_TO_SCAN = 20;

    public PostCallActionWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!CallReminderPreferences.isPostCallPromptEnabled(context)
                || !canPostNotifications()
                || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        String number = getInputData().getString(CallReminderScheduler.INPUT_NUMBER);
        long endedAt = getInputData().getLong(
                CallReminderScheduler.INPUT_CALL_ENDED_AT,
                System.currentTimeMillis()
        );
        if (number == null || number.trim().isEmpty()) {
            return Result.success();
        }

        RecentCall recentCall = findRecentCall(number, endedAt);
        if (recentCall == null) {
            return Result.success();
        }

        CallNotificationChannels.ensureCreated(context);

        Intent detailIntent = new Intent(context, CallDetailActivity.class);
        detailIntent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_ID, recentCall.callLogId);
        detailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                requestCode(recentCall.callLogId),
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                CallNotificationChannels.POST_CALL_CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle(context.getString(
                        R.string.call_reminder_post_call_title,
                        recentCall.displayTitle
                ))
                .setContentText(context.getString(R.string.call_reminder_post_call_body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(
                        0,
                        context.getString(R.string.call_reminder_open_details),
                        contentIntent
                );

        try {
            NotificationManagerCompat.from(context).notify(
                    postCallNotificationId(recentCall.callLogId),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // The user can revoke notification permission at any time.
        }

        return Result.success();
    }

    @Nullable
    private RecentCall findRecentCall(@NonNull String rawNumber, long endedAt) {
        String target = PhoneNumberUtils.normalizeNumber(rawNumber);
        if (target == null || target.isEmpty()) {
            return null;
        }

        try (Cursor cursor = getApplicationContext().getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                },
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        )) {
            if (cursor == null) {
                return null;
            }

            int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);
            if (idIndex < 0 || numberIndex < 0 || dateIndex < 0 || durationIndex < 0) {
                return null;
            }

            int scanned = 0;
            while (cursor.moveToNext() && scanned++ < MAX_ROWS_TO_SCAN) {
                String rowNumber = cursor.getString(numberIndex);
                String normalized = rowNumber == null ? "" : PhoneNumberUtils.normalizeNumber(rowNumber);
                if (!target.equals(normalized)) {
                    continue;
                }

                long callDate = cursor.getLong(dateIndex);
                long durationMs = Math.max(0L, cursor.getLong(durationIndex)) * 1000L;
                long estimatedEnd = callDate + durationMs;
                if (Math.abs(endedAt - estimatedEnd) > MATCH_WINDOW_MS) {
                    continue;
                }

                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                String title = name != null && !name.trim().isEmpty()
                        ? name.trim()
                        : rowNumber == null || rowNumber.trim().isEmpty()
                        ? rawNumber
                        : rowNumber.trim();
                return new RecentCall(cursor.getLong(idIndex), title);
            }
        } catch (SecurityException ignored) {
            return null;
        }

        return null;
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private int requestCode(long callLogId) {
        return (int) ((callLogId ^ (callLogId >>> 32)) & 0x7FFFFFFF);
    }

    private int postCallNotificationId(long callLogId) {
        return 0x47000000 | (int) (Math.abs(callLogId) & 0x00FFFFFF);
    }

    private static final class RecentCall {
        private final long callLogId;
        @NonNull
        private final String displayTitle;

        private RecentCall(long callLogId, @NonNull String displayTitle) {
            this.callLogId = callLogId;
            this.displayTitle = displayTitle;
        }
    }
}
