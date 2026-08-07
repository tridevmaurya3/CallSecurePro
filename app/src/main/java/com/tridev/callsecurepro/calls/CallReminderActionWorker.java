package com.tridev.callsecurepro.calls;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.callsecurepro.data.calls.CallNoteEntity;

public class CallReminderActionWorker extends Worker {

    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    public CallReminderActionWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long callLogId = getInputData().getLong(CallReminderScheduler.INPUT_CALL_LOG_ID, -1L);
        String action = getInputData().getString(CallReminderScheduler.INPUT_ACTION);
        if (callLogId < 0L || action == null) {
            return Result.success();
        }

        CallNoteRepository repository = new CallNoteRepository(getApplicationContext());
        CallNoteEntity note = repository.find(callLogId);
        if (note == null) {
            clearNotification(callLogId);
            return Result.success();
        }

        if (CallReminderActionReceiver.ACTION_DONE.equals(action)) {
            repository.markFollowUpDone(callLogId);
            CallReminderScheduler.cancelFollowUp(getApplicationContext(), callLogId);
            clearNotification(callLogId);
            return Result.success();
        }

        if (CallReminderActionReceiver.ACTION_SNOOZE.equals(action)) {
            long next = System.currentTimeMillis() + ONE_DAY_MS;
            repository.snoozeFollowUp(callLogId, next);
            CallReminderScheduler.scheduleFollowUp(getApplicationContext(), callLogId, next);
            clearNotification(callLogId);
        }

        return Result.success();
    }

    private void clearNotification(long callLogId) {
        NotificationManagerCompat.from(getApplicationContext())
                .cancel(CallFollowUpWorker.notificationId(callLogId));
    }
}
