package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.calls.CallNoteRepository;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FollowUpCenterRepository {

    private final Context appContext;
    private final CallNoteRepository noteRepository;

    public FollowUpCenterRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        noteRepository = new CallNoteRepository(appContext);
    }

    @NonNull
    public Snapshot load() {
        List<CallNoteEntity> notes = noteRepository.getFollowUps();
        CallNoteRepository.FollowUpStats stats = noteRepository.getFollowUpStats();
        Map<Long, CallRecord> callRecords = queryCallRecords(notes);
        long now = System.currentTimeMillis();

        List<FollowUpCenterItem> items = new ArrayList<>(notes.size());
        for (CallNoteEntity note : notes) {
            CallRecord record = callRecords.get(note.callLogId);
            boolean callAvailable = record != null;
            String number = callAvailable && !record.number.isEmpty()
                    ? record.number
                    : safeNumber(note.normalizedNumber);
            String displayName = callAvailable && !record.name.isEmpty()
                    ? record.name
                    : number;
            boolean missed = callAvailable && record.type == CallLog.Calls.MISSED_TYPE;

            int priority = calculatePriority(note, missed, now);
            String reason = buildPriorityReason(note, missed, now);

            items.add(new FollowUpCenterItem(
                    note.callLogId,
                    displayName,
                    number,
                    note.noteText,
                    note.callTimestamp,
                    note.followUpAt,
                    note.followUpDone,
                    callAvailable,
                    missed,
                    priority,
                    reason
            ));
        }

        items.sort((left, right) -> compareItems(left, right, now));
        return new Snapshot(items, stats);
    }

    public void reconcilePending(@NonNull Context context) {
        noteRepository.reconcilePendingFollowUps(context);
    }

    public void markDone(@NonNull Context context, long callLogId) {
        noteRepository.markFollowUpDone(callLogId);
        com.tridev.callsecurepro.calls.CallReminderScheduler.cancelFollowUp(context, callLogId);
    }

    public void snoozeOneDay(@NonNull Context context, long callLogId) {
        long target = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        noteRepository.snoozeFollowUp(callLogId, target);
        CallNoteEntity updated = noteRepository.find(callLogId);
        com.tridev.callsecurepro.calls.CallReminderScheduler.syncFollowUp(context, updated);
    }

    @NonNull
    private Map<Long, CallRecord> queryCallRecords(@NonNull List<CallNoteEntity> notes) {
        Map<Long, CallRecord> result = new HashMap<>();
        if (notes.isEmpty() || ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED) {
            return result;
        }

        StringBuilder selection = new StringBuilder(CallLog.Calls._ID).append(" IN (");
        String[] args = new String[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) {
                selection.append(',');
            }
            selection.append('?');
            args[i] = String.valueOf(notes.get(i).callLogId);
        }
        selection.append(')');

        String[] projection = new String[]{
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE
        };

        try (Cursor cursor = appContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection.toString(),
                args,
                null
        )) {
            if (cursor == null) {
                return result;
            }

            int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);

            while (cursor.moveToNext()) {
                if (idIndex < 0 || typeIndex < 0) {
                    continue;
                }
                long id = cursor.getLong(idIndex);
                String number = numberIndex >= 0 ? safe(cursor.getString(numberIndex)) : "";
                String name = nameIndex >= 0 ? safe(cursor.getString(nameIndex)) : "";
                int type = cursor.getInt(typeIndex);
                result.put(id, new CallRecord(number, name, type));
            }
        } catch (SecurityException ignored) {
            return new HashMap<>();
        }

        return result;
    }

    private int calculatePriority(
            @NonNull CallNoteEntity note,
            boolean missed,
            long now
    ) {
        if (note.followUpDone) {
            return 0;
        }

        long delta = note.followUpAt - now;
        int score;
        if (delta < 0L) {
            long overdueHours = Math.max(1L, (-delta) / (60L * 60L * 1000L));
            score = 60 + (int) Math.min(20L, overdueHours / 6L);
        } else if (delta <= 24L * 60L * 60L * 1000L) {
            score = 45;
        } else if (delta <= 3L * 24L * 60L * 60L * 1000L) {
            score = 30;
        } else {
            score = 15;
        }

        if (missed) {
            score += 15;
        }
        if (!note.noteText.trim().isEmpty()) {
            score += 5;
        }
        return Math.min(100, score);
    }

    @NonNull
    private String buildPriorityReason(
            @NonNull CallNoteEntity note,
            boolean missed,
            long now
    ) {
        if (note.followUpDone) {
            return appContext.getString(R.string.follow_up_center_done);
        }

        List<String> reasons = new ArrayList<>();
        long delta = note.followUpAt - now;
        if (delta < 0L) {
            reasons.add(appContext.getString(R.string.follow_up_center_priority_overdue));
        } else if (delta <= 24L * 60L * 60L * 1000L) {
            reasons.add(appContext.getString(R.string.follow_up_center_priority_due_soon));
        } else {
            reasons.add(appContext.getString(R.string.follow_up_center_priority_upcoming));
        }
        if (missed) {
            reasons.add(appContext.getString(R.string.follow_up_center_priority_missed));
        }
        if (!note.noteText.trim().isEmpty()) {
            reasons.add(appContext.getString(R.string.follow_up_center_priority_note));
        }
        return android.text.TextUtils.join(" • ", reasons);
    }

    private int compareItems(
            @NonNull FollowUpCenterItem left,
            @NonNull FollowUpCenterItem right,
            long now
    ) {
        FollowUpCenterItem.Bucket leftBucket = left.bucket(now);
        FollowUpCenterItem.Bucket rightBucket = right.bucket(now);
        int leftRank = bucketRank(leftBucket);
        int rightRank = bucketRank(rightBucket);
        if (leftRank != rightRank) {
            return Integer.compare(leftRank, rightRank);
        }
        if (leftBucket != FollowUpCenterItem.Bucket.DONE
                && left.priorityScore != right.priorityScore) {
            return Integer.compare(right.priorityScore, left.priorityScore);
        }
        if (leftBucket == FollowUpCenterItem.Bucket.DONE) {
            return Long.compare(right.followUpAt, left.followUpAt);
        }
        return Long.compare(left.followUpAt, right.followUpAt);
    }

    private int bucketRank(@NonNull FollowUpCenterItem.Bucket bucket) {
        switch (bucket) {
            case OVERDUE:
                return 0;
            case UPCOMING:
                return 1;
            case DONE:
            default:
                return 2;
        }
    }

    @NonNull
    private String safeNumber(@Nullable String value) {
        String safe = safe(value);
        return safe.isEmpty()
                ? appContext.getString(R.string.follow_up_center_unknown_number)
                : safe;
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Snapshot {
        @NonNull
        public final List<FollowUpCenterItem> items;
        @NonNull
        public final CallNoteRepository.FollowUpStats stats;

        Snapshot(
                @NonNull List<FollowUpCenterItem> items,
                @NonNull CallNoteRepository.FollowUpStats stats
        ) {
            this.items = items;
            this.stats = stats;
        }
    }

    private static final class CallRecord {
        @NonNull
        final String number;
        @NonNull
        final String name;
        final int type;

        CallRecord(@NonNull String number, @NonNull String name, int type) {
            this.number = number;
            this.name = name;
            this.type = type;
        }
    }
}
