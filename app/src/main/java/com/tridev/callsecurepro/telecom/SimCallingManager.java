package com.tridev.callsecurepro.telecom;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public-API-only SIM/phone-account resolver for outgoing calls.
 *
 * The manager never guesses hidden subscription identifiers and never forces a selected
 * account for emergency calls. User preferences are stored locally and are applied only when
 * the same enabled cellular PhoneAccountHandle is still available on the device.
 */
public final class SimCallingManager {

    private static final String PREFS_NAME = "smart_sim_calling";
    private static final String KEY_GLOBAL_ACCOUNT = "global_account";
    private static final String KEY_NUMBER_PREFIX = "number_account_";
    private static final String SYSTEM_DEFAULT_KEY = "system_default";

    public static final class SimOption {
        @NonNull
        private final String stableKey;
        @NonNull
        private final String label;
        @Nullable
        private final PhoneAccountHandle phoneAccountHandle;
        private final boolean systemDefault;
        private final int simSlotIndex;
        private final int subscriptionId;

        private SimOption(
                @NonNull String stableKey,
                @NonNull String label,
                @Nullable PhoneAccountHandle phoneAccountHandle,
                boolean systemDefault,
                int simSlotIndex,
                int subscriptionId
        ) {
            this.stableKey = stableKey;
            this.label = label;
            this.phoneAccountHandle = phoneAccountHandle;
            this.systemDefault = systemDefault;
            this.simSlotIndex = simSlotIndex;
            this.subscriptionId = subscriptionId;
        }

        @NonNull
        public String getStableKey() {
            return stableKey;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        @Nullable
        public PhoneAccountHandle getPhoneAccountHandle() {
            return phoneAccountHandle;
        }

        public boolean isSystemDefault() {
            return systemDefault;
        }

        public int getSimSlotIndex() {
            return simSlotIndex;
        }

        public int getSubscriptionId() {
            return subscriptionId;
        }
    }

    public static final class LoadResult {
        @NonNull
        private final List<SimOption> options;
        private final boolean telephonyAvailable;
        private final boolean permissionGranted;
        private final boolean exactSimMappingSupported;

        private LoadResult(
                @NonNull List<SimOption> options,
                boolean telephonyAvailable,
                boolean permissionGranted,
                boolean exactSimMappingSupported
        ) {
            this.options = Collections.unmodifiableList(options);
            this.telephonyAvailable = telephonyAvailable;
            this.permissionGranted = permissionGranted;
            this.exactSimMappingSupported = exactSimMappingSupported;
        }

        @NonNull
        public List<SimOption> getOptions() {
            return options;
        }

        public boolean isTelephonyAvailable() {
            return telephonyAvailable;
        }

        public boolean isPermissionGranted() {
            return permissionGranted;
        }

        public boolean isExactSimMappingSupported() {
            return exactSimMappingSupported;
        }
    }

    private final Context appContext;
    private final SharedPreferences preferences;

    public SimCallingManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasPhoneStatePermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasTelephony() {
        return appContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    @NonNull
    public LoadResult loadOptions() {
        List<SimOption> options = new ArrayList<>();
        options.add(systemDefaultOption());

        boolean telephonyAvailable = hasTelephony();
        boolean permissionGranted = hasPhoneStatePermission();
        boolean exactMappingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

        if (!telephonyAvailable || !permissionGranted) {
            return new LoadResult(
                    options,
                    telephonyAvailable,
                    permissionGranted,
                    exactMappingSupported
            );
        }

        // Android 8-10 do not expose a reliable public API to map each cellular subscription
        // to its PhoneAccountHandle. On those versions, stay on System default instead of
        // risking a call through the wrong account.
        if (!exactMappingSupported) {
            return new LoadResult(options, true, true, false);
        }

        TelecomManager telecomManager =
                (TelecomManager) appContext.getSystemService(Context.TELECOM_SERVICE);
        TelephonyManager telephonyManager =
                (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telecomManager == null || telephonyManager == null) {
            return new LoadResult(options, true, true, true);
        }

        try {
            Map<Integer, SubscriptionInfo> subscriptionById = loadSubscriptionsById();
            if (subscriptionById.isEmpty()) {
                return new LoadResult(options, true, true, true);
            }

            List<PhoneAccountHandle> handles = telecomManager.getCallCapablePhoneAccounts();
            if (handles == null || handles.isEmpty()) {
                return new LoadResult(options, true, true, true);
            }

            for (PhoneAccountHandle handle : handles) {
                if (handle == null) {
                    continue;
                }

                int subscriptionId;
                try {
                    subscriptionId = telephonyManager.getSubscriptionId(handle);
                } catch (SecurityException | UnsupportedOperationException ignored) {
                    continue;
                }

                SubscriptionInfo info = subscriptionById.get(subscriptionId);
                if (info == null) {
                    // Ignore non-cellular/VoIP phone accounts; this UI is specifically for SIMs.
                    continue;
                }

                int slotIndex = info.getSimSlotIndex();
                if (slotIndex < 0) {
                    continue;
                }

                options.add(new SimOption(
                        stableKey(handle),
                        buildLabel(info, slotIndex),
                        handle,
                        false,
                        slotIndex,
                        subscriptionId
                ));
            }
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // Keep System default as the safe fallback.
        }

        return new LoadResult(options, true, true, true);
    }

    @NonNull
    private Map<Integer, SubscriptionInfo> loadSubscriptionsById() {
        Map<Integer, SubscriptionInfo> result = new HashMap<>();
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager == null) {
            return result;
        }

        try {
            List<SubscriptionInfo> active = subscriptionManager.getActiveSubscriptionInfoList();
            if (active == null) {
                return result;
            }
            for (SubscriptionInfo info : active) {
                if (info != null) {
                    result.put(info.getSubscriptionId(), info);
                }
            }
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // No subscription metadata means no specific-SIM option will be exposed.
        }
        return result;
    }

    @NonNull
    private String buildLabel(@NonNull SubscriptionInfo info, int slotIndex) {
        String base = "SIM " + (slotIndex + 1);
        CharSequence carrierName = info.getCarrierName();
        if (carrierName == null || carrierName.toString().trim().isEmpty()) {
            carrierName = info.getDisplayName();
        }

        if (carrierName == null || carrierName.toString().trim().isEmpty()) {
            return base;
        }
        return base + " • " + carrierName.toString().trim();
    }

    @NonNull
    public SimOption resolveInitialSelection(
            @Nullable String number,
            @NonNull List<SimOption> options
    ) {
        if (options.isEmpty()) {
            return systemDefaultOption();
        }

        String numberKey = preferenceKeyForNumber(number);
        if (numberKey != null) {
            SimOption matched = findByStableKey(
                    preferences.getString(numberKey, null),
                    options
            );
            if (matched != null) {
                return matched;
            }
        }

        SimOption global = findByStableKey(
                preferences.getString(KEY_GLOBAL_ACCOUNT, null),
                options
        );
        if (global != null) {
            return global;
        }

        if (options.size() == 2 && options.get(0).isSystemDefault()) {
            return options.get(1);
        }

        return options.get(0);
    }

    @Nullable
    public SimOption resolveNumberSpecificSelection(
            @Nullable String number,
            @NonNull List<SimOption> options
    ) {
        String numberKey = preferenceKeyForNumber(number);
        if (numberKey == null) {
            return null;
        }
        return findByStableKey(preferences.getString(numberKey, null), options);
    }

    public void rememberGlobalSelection(@NonNull SimOption option) {
        preferences.edit().putString(KEY_GLOBAL_ACCOUNT, option.getStableKey()).apply();
    }

    public void rememberSelectionForNumber(
            @Nullable String number,
            @NonNull SimOption option
    ) {
        String key = preferenceKeyForNumber(number);
        if (key == null) {
            return;
        }
        preferences.edit().putString(key, option.getStableKey()).apply();
    }

    public void clearSelectionForNumber(@Nullable String number) {
        String key = preferenceKeyForNumber(number);
        if (key != null) {
            preferences.edit().remove(key).apply();
        }
    }

    public boolean isEmergencyNumber(@NonNull String number) {
        try {
            return PhoneNumberUtils.isEmergencyNumber(number);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @NonNull
    public Bundle createCallExtras(
            @NonNull String number,
            @Nullable SimOption option
    ) {
        Bundle extras = new Bundle();
        if (option == null
                || option.isSystemDefault()
                || option.getPhoneAccountHandle() == null
                || isEmergencyNumber(number)) {
            return extras;
        }

        extras.putParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                option.getPhoneAccountHandle()
        );
        return extras;
    }

    @NonNull
    private SimOption systemDefaultOption() {
        return new SimOption(
                SYSTEM_DEFAULT_KEY,
                "System default",
                null,
                true,
                -1,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
        );
    }

    @Nullable
    private SimOption findByStableKey(
            @Nullable String stableKey,
            @NonNull List<SimOption> options
    ) {
        if (stableKey == null || stableKey.trim().isEmpty()) {
            return null;
        }
        for (SimOption option : options) {
            if (stableKey.equals(option.getStableKey())) {
                return option;
            }
        }
        return null;
    }

    @NonNull
    private String stableKey(@NonNull PhoneAccountHandle handle) {
        String component = handle.getComponentName() == null
                ? ""
                : handle.getComponentName().flattenToString();
        return component + "#" + handle.getId();
    }

    @Nullable
    private String preferenceKeyForNumber(@Nullable String number) {
        if (number == null) {
            return null;
        }
        String normalized = PhoneNumberUtils.normalizeNumber(number.trim());
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        return KEY_NUMBER_PREFIX + sha256(normalized);
    }

    @NonNull
    private String sha256(@NonNull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format(Locale.US, "%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
