package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MissedCallAssistantRepository {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long LOOKBACK_30_DAYS = 30L * DAY;
    private static final long LOOKBACK_7_DAYS = 7L * DAY;
    private static final int MAX_QUERY_ROWS = 500;

    private final Context appContext;

    public MissedCallAssistantRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public Snapshot load() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            return new Snapshot(false, Collections.emptyList(), 0, 0);
        }

        long now = System.currentTimeMillis();
        long cutoff30 = now - LOOKBACK_30_DAYS;
        long cutoff7 = now - LOOKBACK_7_DAYS;
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        int totalEvents = 0;

        String[] projection = new String[]{
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
        };
        String selection = CallLog.Calls.DATE + ">=? AND ("
                + CallLog.Calls.TYPE + "=? OR "
                + CallLog.Calls.TYPE + "=?)";
        String[] args = new String[]{
                String.valueOf(cutoff30),
                String.valueOf(CallLog.Calls.MISSED_TYPE),
                String.valueOf(CallLog.Calls.REJECTED_TYPE)
        };

        try (Cursor cursor = appContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                CallLog.Calls.DATE + " DESC"
        )) {
            if (cursor == null) {
                return new Snapshot(true, Collections.emptyList(), 0, 0);
            }

            int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);

            int rows = 0;
            while (cursor.moveToNext() && rows < MAX_QUERY_ROWS) {
                rows++;
                if (idIndex < 0 || dateIndex < 0) {
                    continue;
                }
                totalEvents++;
                long id = cursor.getLong(idIndex);
                long timestamp = cursor.getLong(dateIndex);
                String rawNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                String readable = readableNumber(rawNumber);
                String key = groupingKey(rawNumber, readable, id);
                String cachedName = nameIndex >= 0 ? cursor.getString(nameIndex) : null;

                MutableGroup group = groups.get(key);
                if (group == null) {
                    group = new MutableGroup(
                            id,
                            readable,
                            displayName(cachedName, readable),
                            timestamp,
                            isDialable(rawNumber)
                    );
                    groups.put(key, group);
                }
                group.count30++;
                if (timestamp >= cutoff7) {
                    group.count7++;
                }
            }
        } catch (SecurityException ignored) {
            return new Snapshot(false, Collections.emptyList(), 0, 0);
        }

        List<MissedCallerItem> items = new ArrayList<>();
        int repeatCallers = 0;
        for (MutableGroup group : groups.values()) {
            int score = priorityScore(group, now);
            MissedCallerItem item = new MissedCallerItem(
                    group.latestCallLogId,
                    group.number,
                    group.displayName,
                    group.latestTimestamp,
                    group.count30,
                    group.count7,
                    score,
                    group.dialable
            );
            if (item.isRepeatCaller()) {
                repeatCallers++;
            }
            items.add(item);
        }

        items.sort(Comparator
                .comparingInt((MissedCallerItem item) -> item.priorityScore)
                .reversed()
                .thenComparingLong(item -> -item.latestTimestamp));

        return new Snapshot(true, items, repeatCallers, totalEvents);
    }

    private int priorityScore(@NonNull MutableGroup group, long now) {
        int score = 20;
        if (group.count7 > 1) {
            score += Math.min(40, (group.count7 - 1) * 12);
        }
        long age = Math.max(0L, now - group.latestTimestamp);
        if (age <= 6L * 60L * 60L * 1000L) {
            score += 30;
        } else if (age <= DAY) {
            score += 20;
        } else if (age <= 3L * DAY) {
            score += 10;
        }
        return Math.max(0, Math.min(100, score));
    }

    @NonNull
    private String groupingKey(@Nullable String raw, @NonNull String readable, long id) {
        if (!isDialable(raw)) {
            return "hidden:" + readable + ":" + id;
        }
        String normalized = PhoneNumberUtils.normalizeNumber(raw);
        return normalized == null || normalized.isEmpty()
                ? "raw:" + readable
                : "number:" + normalized;
    }

    @NonNull
    private String readableNumber(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty() || "-1".equals(raw.trim())) {
            return "Unknown number";
        }
        if ("-2".equals(raw.trim())) {
            return "Private number";
        }
        if ("-3".equals(raw.trim())) {
            return "Restricted number";
        }
        return raw.trim();
    }

    @NonNull
    private String displayName(@Nullable String cachedName, @NonNull String readable) {
        if (cachedName != null && !cachedName.trim().isEmpty()) {
            return cachedName.trim();
        }
        return readable;
    }

    private boolean isDialable(@Nullable String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return !trimmed.isEmpty()
                && !"-1".equals(trimmed)
                && !"-2".equals(trimmed)
                && !"-3".equals(trimmed);
    }

    private static final class MutableGroup {
        final long latestCallLogId;
        @NonNull final String number;
        @NonNull final String displayName;
        final long latestTimestamp;
        final boolean dialable;
        int count30;
        int count7;

        MutableGroup(
                long latestCallLogId,
                @NonNull String number,
                @NonNull String displayName,
                long latestTimestamp,
                boolean dialable
        ) {
            this.latestCallLogId = latestCallLogId;
            this.number = number;
            this.displayName = displayName;
            this.latestTimestamp = latestTimestamp;
            this.dialable = dialable;
        }
    }

    public static final class Snapshot {
        public final boolean permissionGranted;
        @NonNull public final List<MissedCallerItem> items;
        public final int repeatCallers;
        public final int totalEvents;

        Snapshot(
                boolean permissionGranted,
                @NonNull List<MissedCallerItem> items,
                int repeatCallers,
                int totalEvents
        ) {
            this.permissionGranted = permissionGranted;
            this.items = items;
            this.repeatCallers = Math.max(0, repeatCallers);
            this.totalEvents = Math.max(0, totalEvents);
        }
    }
}
