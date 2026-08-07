package com.tridev.callsecurepro.calls;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.calls.CallNoteDao;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallNoteRepository {

    private static final int MAX_FOLLOW_UP_CENTER_ITEMS = 300;
    private static final String RECONCILE_PREFS = "call_follow_up_reconciliation";
    private static final String KEY_RECONCILED_V1 = "pending_follow_ups_reconciled_v1";
    private static final String KEY_RECONCILED_WITH_NOTIFICATIONS =
            "pending_follow_ups_reconciled_with_notifications";

    private final CallNoteDao callNoteDao;

    public CallNoteRepository(@NonNull Context context) {
        callNoteDao = CallSecureDatabase.getInstance(context.getApplicationContext()).callNoteDao();
    }

    @Nullable
    public CallNoteEntity find(long callLogId) {
        return callNoteDao.findByCallLogId(callLogId);
    }

    @NonNull
    public Map<Long, CallNoteEntity> findForCallIds(@NonNull List<Long> callLogIds) {
        if (callLogIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CallNoteEntity> entities = callNoteDao.findByCallLogIds(callLogIds);
        Map<Long, CallNoteEntity> result = new HashMap<>();
        for (CallNoteEntity entity : entities) {
            result.put(entity.callLogId, entity);
        }
        return result;
    }

    @NonNull
    public List<CallNoteEntity> getFollowUps() {
        return callNoteDao.getFollowUps(MAX_FOLLOW_UP_CENTER_ITEMS);
    }

    @NonNull
    public List<CallNoteEntity> getPendingFollowUps() {
        return callNoteDao.getPendingFollowUps(MAX_FOLLOW_UP_CENTER_ITEMS);
    }

    @NonNull
    public FollowUpStats getFollowUpStats() {
        long now = System.currentTimeMillis();
        return new FollowUpStats(
                callNoteDao.countOverdueFollowUps(now),
                callNoteDao.countUpcomingFollowUps(now),
                callNoteDao.countCompletedFollowUps()
        );
    }

    public void reconcilePendingFollowUps(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(
                RECONCILE_PREFS,
                Context.MODE_PRIVATE
        );

        boolean notificationsReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
        boolean reconciled = preferences.getBoolean(KEY_RECONCILED_V1, false);
        boolean previouslyReady = preferences.getBoolean(
                KEY_RECONCILED_WITH_NOTIFICATIONS,
                false
        );

        if (reconciled && (!notificationsReady || previouslyReady)) {
            return;
        }

        for (CallNoteEntity note : getPendingFollowUps()) {
            try {
                CallReminderScheduler.syncFollowUp(appContext, note);
            } catch (RuntimeException ignored) {
                // A scheduling failure must not block access to saved follow-up metadata.
            }
        }
        preferences.edit()
                .putBoolean(KEY_RECONCILED_V1, true)
                .putBoolean(KEY_RECONCILED_WITH_NOTIFICATIONS, notificationsReady)
                .apply();
    }

    public void save(
            long callLogId,
            @NonNull String number,
            long callTimestamp,
            @NonNull String noteText,
            long followUpAt,
            boolean followUpDone
    ) {
        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized == null) {
            normalized = "";
        }

        String trimmedNote = noteText.trim();
        if (trimmedNote.isEmpty() && followUpAt <= 0L) {
            callNoteDao.deleteByCallLogId(callLogId);
            return;
        }

        callNoteDao.upsert(new CallNoteEntity(
                callLogId,
                normalized,
                callTimestamp,
                trimmedNote,
                Math.max(0L, followUpAt),
                followUpDone,
                System.currentTimeMillis()
        ));
    }

    public void markFollowUpDone(long callLogId) {
        callNoteDao.markFollowUpDone(callLogId, System.currentTimeMillis());
    }

    public void snoozeFollowUp(long callLogId, long followUpAt) {
        callNoteDao.snoozeFollowUp(
                callLogId,
                Math.max(System.currentTimeMillis(), followUpAt),
                System.currentTimeMillis()
        );
    }

    public static final class FollowUpStats {
        public final int overdue;
        public final int upcoming;
        public final int completed;

        public FollowUpStats(int overdue, int upcoming, int completed) {
            this.overdue = Math.max(0, overdue);
            this.upcoming = Math.max(0, upcoming);
            this.completed = Math.max(0, completed);
        }

        public int pending() {
            return overdue + upcoming;
        }
    }
}
