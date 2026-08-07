package com.tridev.callsecurepro.calls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class CallReminderActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DONE =
            "com.tridev.callsecurepro.action.FOLLOW_UP_DONE";
    public static final String ACTION_SNOOZE =
            "com.tridev.callsecurepro.action.FOLLOW_UP_SNOOZE";
    public static final String EXTRA_CALL_LOG_ID =
            "com.tridev.callsecurepro.extra.FOLLOW_UP_CALL_LOG_ID";

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        long callLogId = intent.getLongExtra(EXTRA_CALL_LOG_ID, -1L);
        if (callLogId < 0L
                || (!ACTION_DONE.equals(action) && !ACTION_SNOOZE.equals(action))) {
            return;
        }

        Data input = new Data.Builder()
                .putLong(CallReminderScheduler.INPUT_CALL_LOG_ID, callLogId)
                .putString(CallReminderScheduler.INPUT_ACTION, action)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueue(
                new OneTimeWorkRequest.Builder(CallReminderActionWorker.class)
                        .setInputData(input)
                        .build()
        );
    }
}
