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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds privacy-preserving, on-device analytics from the Android call log. */
public final class CallAnalyticsRepository {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long LOOKBACK_30_DAYS = 30L * DAY;
    private static final long LOOKBACK_14_DAYS = 14L * DAY;
    private static final long LOOKBACK_7_DAYS = 7L * DAY;
    private static final int MAX_ROWS = 1500;
    private static final int TOP_CONTACT_LIMIT = 5;

    public static final int TIME_MORNING = 0;
    public static final int TIME_AFTERNOON = 1;
    public static final int TIME_EVENING = 2;
    public static final int TIME_NIGHT = 3;

    private final Context appContext;

    public CallAnalyticsRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public Snapshot load() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            return Snapshot.permissionRequired();
        }

        long now = System.currentTimeMillis();
        long cutoff30 = now - LOOKBACK_30_DAYS;
        long cutoff14 = now - LOOKBACK_14_DAYS;
        long cutoff7 = now - LOOKBACK_7_DAYS;

        int total30 = 0;
        int incoming30 = 0;
        int outgoing30 = 0;
        int missed30 = 0;
        int blockedRejected30 = 0;
        long talkSeconds30 = 0L;
        long answeredTalkSeconds = 0L;
        int answeredCalls = 0;
        int current7 = 0;
        int previous7 = 0;
        int[] last7DayCounts = new int[7];
        int[] weekdayCounts = new int[7];
        int[] timeWindowCounts = new int[4];

        Map<String, MutableContact> contacts = new HashMap<>();
        Map<String, Integer> missedLast7ByNumber = new HashMap<>();

        String[] projection = new String[]{
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
        };

        String selection = CallLog.Calls.DATE + ">=?";
        String[] args = new String[]{String.valueOf(cutoff30)};

        try (Cursor cursor = appContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                CallLog.Calls.DATE + " DESC"
        )) {
            if (cursor == null) {
                return Snapshot.empty(true);
            }

            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

            int rows = 0;
            while (cursor.moveToNext() && rows < MAX_ROWS) {
                rows++;
                if (typeIndex < 0 || dateIndex < 0 || durationIndex < 0) {
                    continue;
                }

                int type = cursor.getInt(typeIndex);
                long timestamp = cursor.getLong(dateIndex);
                long duration = Math.max(0L, cursor.getLong(durationIndex));
                String rawNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                String cachedName = nameIndex >= 0 ? cursor.getString(nameIndex) : null;

                total30++;
                if (type == CallLog.Calls.INCOMING_TYPE) {
                    incoming30++;
                } else if (type == CallLog.Calls.OUTGOING_TYPE) {
                    outgoing30++;
                } else if (type == CallLog.Calls.MISSED_TYPE) {
                    missed30++;
                } else if (type == CallLog.Calls.BLOCKED_TYPE
                        || type == CallLog.Calls.REJECTED_TYPE) {
                    blockedRejected30++;
                }

                if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE) {
                    talkSeconds30 += duration;
                    if (duration > 0L) {
                        answeredCalls++;
                        answeredTalkSeconds += duration;
                    }
                }

                if (timestamp >= cutoff7) {
                    current7++;
                    int dayIndex = dayIndexWithinLast7(timestamp, now);
                    if (dayIndex >= 0 && dayIndex < last7DayCounts.length) {
                        last7DayCounts[dayIndex]++;
                    }
                } else if (timestamp >= cutoff14) {
                    previous7++;
                }

                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(timestamp);
                int weekday = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
                if (weekday >= 0 && weekday < weekdayCounts.length) {
                    weekdayCounts[weekday]++;
                }
                timeWindowCounts[timeWindowForHour(calendar.get(Calendar.HOUR_OF_DAY))]++;

                if (timestamp >= cutoff7 && type == CallLog.Calls.MISSED_TYPE && isDialable(rawNumber)) {
                    String key = normalizedKey(rawNumber);
                    missedLast7ByNumber.put(key, missedLast7ByNumber.getOrDefault(key, 0) + 1);
                }

                if (isDialable(rawNumber)) {
                    String key = normalizedKey(rawNumber);
                    MutableContact contact = contacts.get(key);
                    if (contact == null) {
                        contact = new MutableContact(readableNumber(rawNumber));
                        contacts.put(key, contact);
                    }
                    if (cachedName != null && !cachedName.trim().isEmpty()) {
                        contact.displayName = cachedName.trim();
                    }
                    contact.interactionCount++;
                    contact.talkSeconds += duration;
                }
            }
        } catch (SecurityException ignored) {
            return Snapshot.permissionRequired();
        }

        int repeatMissedCallers7 = 0;
        for (int count : missedLast7ByNumber.values()) {
            if (count >= 2) {
                repeatMissedCallers7++;
            }
        }

        List<TopContact> topContacts = new ArrayList<>();
        for (MutableContact contact : contacts.values()) {
            topContacts.add(new TopContact(
                    contact.displayName == null || contact.displayName.trim().isEmpty()
                            ? contact.number
                            : contact.displayName,
                    contact.number,
                    contact.interactionCount,
                    contact.talkSeconds
            ));
        }
        topContacts.sort(Comparator
                .comparingInt((TopContact contact) -> contact.interactionCount)
                .reversed()
                .thenComparingLong(contact -> -contact.talkSeconds)
                .thenComparing(contact -> contact.displayName.toLowerCase(Locale.getDefault())));
        if (topContacts.size() > TOP_CONTACT_LIMIT) {
            topContacts = new ArrayList<>(topContacts.subList(0, TOP_CONTACT_LIMIT));
        }

        long averageAnsweredSeconds = answeredCalls == 0
                ? 0L
                : answeredTalkSeconds / answeredCalls;

        return new Snapshot(
                true,
                total30,
                incoming30,
                outgoing30,
                missed30,
                blockedRejected30,
                talkSeconds30,
                averageAnsweredSeconds,
                current7,
                previous7,
                last7DayCounts,
                busiestIndex(weekdayCounts),
                busiestIndex(timeWindowCounts),
                contacts.size(),
                repeatMissedCallers7,
                topContacts
        );
    }

    private int dayIndexWithinLast7(long timestamp, long now) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        clearTime(today);

        Calendar callDay = Calendar.getInstance();
        callDay.setTimeInMillis(timestamp);
        clearTime(callDay);

        long diffDays = (today.getTimeInMillis() - callDay.getTimeInMillis()) / DAY;
        if (diffDays < 0L || diffDays > 6L) {
            return -1;
        }
        return 6 - (int) diffDays;
    }

    private void clearTime(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int timeWindowForHour(int hour) {
        if (hour >= 5 && hour < 12) {
            return TIME_MORNING;
        }
        if (hour >= 12 && hour < 17) {
            return TIME_AFTERNOON;
        }
        if (hour >= 17 && hour < 22) {
            return TIME_EVENING;
        }
        return TIME_NIGHT;
    }

    private int busiestIndex(@NonNull int[] values) {
        int bestIndex = -1;
        int bestValue = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] > bestValue) {
                bestValue = values[index];
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private boolean isDialable(@Nullable String rawNumber) {
        if (rawNumber == null) {
            return false;
        }
        String value = rawNumber.trim();
        return !value.isEmpty()
                && !"-1".equals(value)
                && !"-2".equals(value)
                && !"-3".equals(value);
    }

    @NonNull
    private String normalizedKey(@NonNull String rawNumber) {
        String normalized = PhoneNumberUtils.normalizeNumber(rawNumber);
        return normalized == null || normalized.isEmpty() ? rawNumber.trim() : normalized;
    }

    @NonNull
    private String readableNumber(@NonNull String rawNumber) {
        return rawNumber.trim();
    }

    private static final class MutableContact {
        @NonNull final String number;
        @Nullable String displayName;
        int interactionCount;
        long talkSeconds;

        MutableContact(@NonNull String number) {
            this.number = number;
        }
    }

    public static final class TopContact {
        @NonNull public final String displayName;
        @NonNull public final String number;
        public final int interactionCount;
        public final long talkSeconds;

        TopContact(
                @NonNull String displayName,
                @NonNull String number,
                int interactionCount,
                long talkSeconds
        ) {
            this.displayName = displayName;
            this.number = number;
            this.interactionCount = Math.max(0, interactionCount);
            this.talkSeconds = Math.max(0L, talkSeconds);
        }
    }

    public static final class Snapshot {
        public final boolean permissionGranted;
        public final int total30;
        public final int incoming30;
        public final int outgoing30;
        public final int missed30;
        public final int blockedRejected30;
        public final long talkSeconds30;
        public final long averageAnsweredSeconds;
        public final int current7;
        public final int previous7;
        @NonNull public final int[] last7DayCounts;
        public final int busiestWeekdayIndex;
        public final int busiestTimeWindow;
        public final int uniqueCallers30;
        public final int repeatMissedCallers7;
        @NonNull public final List<TopContact> topContacts;

        Snapshot(
                boolean permissionGranted,
                int total30,
                int incoming30,
                int outgoing30,
                int missed30,
                int blockedRejected30,
                long talkSeconds30,
                long averageAnsweredSeconds,
                int current7,
                int previous7,
                @NonNull int[] last7DayCounts,
                int busiestWeekdayIndex,
                int busiestTimeWindow,
                int uniqueCallers30,
                int repeatMissedCallers7,
                @NonNull List<TopContact> topContacts
        ) {
            this.permissionGranted = permissionGranted;
            this.total30 = Math.max(0, total30);
            this.incoming30 = Math.max(0, incoming30);
            this.outgoing30 = Math.max(0, outgoing30);
            this.missed30 = Math.max(0, missed30);
            this.blockedRejected30 = Math.max(0, blockedRejected30);
            this.talkSeconds30 = Math.max(0L, talkSeconds30);
            this.averageAnsweredSeconds = Math.max(0L, averageAnsweredSeconds);
            this.current7 = Math.max(0, current7);
            this.previous7 = Math.max(0, previous7);
            this.last7DayCounts = last7DayCounts.clone();
            this.busiestWeekdayIndex = busiestWeekdayIndex;
            this.busiestTimeWindow = busiestTimeWindow;
            this.uniqueCallers30 = Math.max(0, uniqueCallers30);
            this.repeatMissedCallers7 = Math.max(0, repeatMissedCallers7);
            this.topContacts = Collections.unmodifiableList(new ArrayList<>(topContacts));
        }

        @NonNull
        static Snapshot permissionRequired() {
            return empty(false);
        }

        @NonNull
        static Snapshot empty(boolean permissionGranted) {
            return new Snapshot(
                    permissionGranted,
                    0, 0, 0, 0, 0,
                    0L, 0L,
                    0, 0,
                    new int[7],
                    -1, -1,
                    0, 0,
                    Collections.emptyList()
            );
        }
    }
}
