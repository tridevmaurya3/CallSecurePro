package com.tridev.callsecurepro.ui.home;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.protection.ProtectionRepository;
import com.tridev.callsecurepro.protection.ScreeningHistoryRepository;

import java.util.Calendar;

/**
 * Reads the real on-device state used by the compact Home dashboard.
 *
 * All methods are synchronous by design and must be called from a background executor.
 * Missing permissions never trigger permission requests here; unavailable data is simply marked
 * unavailable until the user grants access from the relevant feature/setup screen.
 */
public final class HomeDashboardRepository {

    public static final class Snapshot {
        public final boolean callLogAvailable;
        public final int missedToday;
        public final int contactCount;
        public final boolean contactsAvailable;
        public final int screenedBlocked;
        public final int screenedSilenced;
        public final int localSpamReports;
        public final int trustedNumbers;
        public final int manuallyBlockedNumbers;
        public final int pendingFollowUps;
        public final int setupReadyCount;
        public final int setupTotalCount;
        public final boolean callerScreeningActive;
        @Nullable
        public final LatestCall latestCall;

        private Snapshot(
                boolean callLogAvailable,
                int missedToday,
                int contactCount,
                boolean contactsAvailable,
                int screenedBlocked,
                int screenedSilenced,
                int localSpamReports,
                int trustedNumbers,
                int manuallyBlockedNumbers,
                int pendingFollowUps,
                int setupReadyCount,
                int setupTotalCount,
                boolean callerScreeningActive,
                @Nullable LatestCall latestCall
        ) {
            this.callLogAvailable = callLogAvailable;
            this.missedToday = Math.max(0, missedToday);
            this.contactCount = Math.max(0, contactCount);
            this.contactsAvailable = contactsAvailable;
            this.screenedBlocked = Math.max(0, screenedBlocked);
            this.screenedSilenced = Math.max(0, screenedSilenced);
            this.localSpamReports = Math.max(0, localSpamReports);
            this.trustedNumbers = Math.max(0, trustedNumbers);
            this.manuallyBlockedNumbers = Math.max(0, manuallyBlockedNumbers);
            this.pendingFollowUps = Math.max(0, pendingFollowUps);
            this.setupReadyCount = Math.max(0, setupReadyCount);
            this.setupTotalCount = Math.max(1, setupTotalCount);
            this.callerScreeningActive = callerScreeningActive;
            this.latestCall = latestCall;
        }
    }

    public static final class LatestCall {
        @NonNull
        public final String displayName;
        @NonNull
        public final String number;
        public final int type;
        public final long timestamp;

        private LatestCall(
                @NonNull String displayName,
                @NonNull String number,
                int type,
                long timestamp
        ) {
            this.displayName = displayName;
            this.number = number;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    private static final int SETUP_TOTAL = 5;

    @NonNull
    private final Context appContext;
    @NonNull
    private final ProtectionRepository protectionRepository;
    @NonNull
    private final ScreeningHistoryRepository screeningHistoryRepository;
    @NonNull
    private final CallSecureDatabase database;

    public HomeDashboardRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        protectionRepository = new ProtectionRepository(appContext);
        screeningHistoryRepository = new ScreeningHistoryRepository(appContext);
        database = CallSecureDatabase.getInstance(appContext);
    }

    @NonNull
    public Snapshot loadSnapshot() {
        boolean callLogAvailable = hasPermission(Manifest.permission.READ_CALL_LOG);
        CallSummary callSummary = callLogAvailable ? readCallSummary() : new CallSummary(0, null);

        boolean contactsAvailable = hasPermission(Manifest.permission.READ_CONTACTS);
        int contacts = contactsAvailable ? readContactCount() : 0;

        ProtectionRepository.Stats protectionStats = protectionRepository.getStats();
        ScreeningHistoryRepository.Stats screeningStats = screeningHistoryRepository.getStats();
        int pendingFollowUps = database.callNoteDao().countPendingFollowUps();

        SetupState setupState = readSetupState();

        return new Snapshot(
                callLogAvailable,
                callSummary.missedToday,
                contacts,
                contactsAvailable,
                screeningStats.blocked,
                screeningStats.silenced,
                protectionStats.reports,
                protectionStats.trusted,
                protectionStats.blocked,
                pendingFollowUps,
                setupState.readyCount,
                SETUP_TOTAL,
                setupState.callerScreeningActive,
                callSummary.latestCall
        );
    }

    private boolean hasPermission(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(appContext, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private CallSummary readCallSummary() {
        long startOfDay = startOfTodayMillis();
        int missedToday = 0;
        LatestCall latestCall = null;

        String[] projection = new String[]{
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
        };

        try (Cursor cursor = appContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        )) {
            if (cursor == null) {
                return new CallSummary(0, null);
            }

            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);

            boolean first = true;
            while (cursor.moveToNext()) {
                int type = typeIndex >= 0 ? cursor.getInt(typeIndex) : CallLog.Calls.INCOMING_TYPE;
                long timestamp = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0L;

                if (first) {
                    String number = numberIndex >= 0 ? safe(cursor.getString(numberIndex)) : "";
                    String name = nameIndex >= 0 ? safe(cursor.getString(nameIndex)) : "";
                    if (name.isEmpty()) {
                        name = number;
                    }
                    latestCall = new LatestCall(name, number, type, timestamp);
                    first = false;
                }

                if (timestamp < startOfDay) {
                    break;
                }
                if (type == CallLog.Calls.MISSED_TYPE) {
                    missedToday++;
                }
            }
        } catch (SecurityException ignored) {
            return new CallSummary(0, null);
        }

        return new CallSummary(missedToday, latestCall);
    }

    private int readContactCount() {
        String selection = ContactsContract.Contacts.HAS_PHONE_NUMBER + " != 0";
        try (Cursor cursor = appContext.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID},
                selection,
                null,
                null
        )) {
            return cursor == null ? 0 : cursor.getCount();
        } catch (SecurityException ignored) {
            return 0;
        }
    }

    @NonNull
    private SetupState readSetupState() {
        int ready = 0;

        boolean callerScreeningReady = isCallerScreeningReady();
        if (callerScreeningReady) {
            ready++;
        }

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            ready++;
        }

        boolean telephony = appContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (!telephony || hasPermission(Manifest.permission.CALL_PHONE)) {
            ready++;
        }
        if (!telephony || hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            ready++;
        }

        if (isDefaultPhoneReady(telephony)) {
            ready++;
        }

        return new SetupState(ready, isCallerScreeningActuallyHeld());
    }

    private boolean isCallerScreeningReady() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        RoleManager roleManager = appContext.getSystemService(RoleManager.class);
        return roleManager == null
                || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                || roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private boolean isCallerScreeningActuallyHeld() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        RoleManager roleManager = appContext.getSystemService(RoleManager.class);
        return roleManager != null
                && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private boolean isDefaultPhoneReady(boolean telephony) {
        if (!telephony) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = appContext.getSystemService(RoleManager.class);
            return roleManager == null
                    || !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
                    || roleManager.isRoleHeld(RoleManager.ROLE_DIALER);
        }

        TelecomManager telecomManager =
                (TelecomManager) appContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager == null) {
            return true;
        }
        String defaultPackage = telecomManager.getDefaultDialerPackage();
        return appContext.getPackageName().equals(defaultPackage);
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CallSummary {
        final int missedToday;
        @Nullable
        final LatestCall latestCall;

        private CallSummary(int missedToday, @Nullable LatestCall latestCall) {
            this.missedToday = missedToday;
            this.latestCall = latestCall;
        }
    }

    private static final class SetupState {
        final int readyCount;
        final boolean callerScreeningActive;

        private SetupState(int readyCount, boolean callerScreeningActive) {
            this.readyCount = readyCount;
            this.callerScreeningActive = callerScreeningActive;
        }
    }
}
