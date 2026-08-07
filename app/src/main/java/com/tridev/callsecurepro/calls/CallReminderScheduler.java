package com.tridev.callsecurepro.calls;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.tridev.callsecurepro.data.calls.CallNoteEntity;

import java.util.concurrent.TimeUnit;

public final class CallReminderScheduler {

    static final String INPUT_CALL_LOG_ID = "call_log_id";
    static final String INPUT_NUMBER = "number";
    static final String INPUT_CALL_ENDED_AT = "call_ended_at";
    static final String INPUT_ACTION = "action";

    private static final String FOLLOW_UP_WORK_PREFIX = "call_follow_up_";
    private static final String POST_CALL_WORK_PREFIX = "post_call_prompt_";

    private CallReminderScheduler() {
        // Utility class.
    }

    public static void syncFollowUp(
            @NonNull Context context,
            @Nullable CallNoteEntity note
    ) {
        if (note == null || note.followUpAt <= 0L || note.followUpDone) {
            if (note != null) {
                cancelFollowUp(context, note.callLogId);
            }
            return;
        }
        scheduleFollowUp(context, note.callLogId, note.followUpAt);
    }

    public static void scheduleFollowUp(
            @NonNull Context context,
            long callLogId,
            long followUpAt
    ) {
        long delay = Math.max(0L, followUpAt - System.currentTimeMillis());
        Data input = new Data.Builder()
                .putLong(INPUT_CALL_LOG_ID, callLogId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CallFollowUpWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                followUpWorkName(callLogId),
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static void cancelFollowUp(@NonNull Context context, long callLogId) {
        Context appContext = context.getApplicationContext();
        WorkManager.getInstance(appContext).cancelUniqueWork(followUpWorkName(callLogId));
        NotificationManagerCompat.from(appContext).cancel(
                CallFollowUpWorker.notificationId(callLogId)
        );
    }

    public static void schedulePostCallPrompt(
            @NonNull Context context,
            @Nullable String number,
            long callEndedAt
    ) {
        if (!CallReminderPreferences.isPostCallPromptEnabled(context)) {
            return;
        }
        String safeNumber = number == null ? "" : number.trim();
        if (safeNumber.isEmpty()) {
            return;
        }

        Data input = new Data.Builder()
                .putString(INPUT_NUMBER, safeNumber)
                .putLong(INPUT_CALL_ENDED_AT, callEndedAt)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PostCallActionWorker.class)
                .setInitialDelay(4L, TimeUnit.SECONDS)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                POST_CALL_WORK_PREFIX + Math.abs((safeNumber + callEndedAt).hashCode()),
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    @NonNull
    private static String followUpWorkName(long callLogId) {
        return FOLLOW_UP_WORK_PREFIX + callLogId;
    }
}
