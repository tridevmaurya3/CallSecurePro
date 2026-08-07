package com.tridev.callsecurepro.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.tridev.callsecurepro.community.FirebaseCommunityConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android client for the authenticated multi-source intelligence backend.
 *
 * This class deliberately contains no third-party provider credentials. Twilio, Telesign,
 * Veriphone, Abstract, IPinfo, MaxMind, IP2Location, DB-IP, GreyNoise and AbuseIPDB credentials
 * live only in the server-side Firebase Secret Manager configuration.
 *
 * The client is prepared now but should only be called after the Cloud Functions backend has
 * been deployed. Existing local/Firebase caller identity logic remains independent and safe.
 */
public final class CloudIntelligenceClient {

    private static final String FUNCTIONS_REGION = "asia-south1";
    private static final String PHONE_FUNCTION = "lookupPhoneIntelligence";
    private static final String IP_FUNCTION = "lookupIpIntelligence";

    public interface Callback {
        void onSuccess(@NonNull LookupResponse response);

        void onError(@NonNull String errorCode);
    }

    public static final class LookupResponse {
        @NonNull
        public final List<Map<String, Object>> evidence;
        @NonNull
        public final List<Map<String, Object>> providerStatus;
        @NonNull
        public final List<String> configuredSources;

        private LookupResponse(
                @NonNull List<Map<String, Object>> evidence,
                @NonNull List<Map<String, Object>> providerStatus,
                @NonNull List<String> configuredSources
        ) {
            this.evidence = evidence;
            this.providerStatus = providerStatus;
            this.configuredSources = configuredSources;
        }
    }

    @NonNull
    private final Context appContext;

    public CloudIntelligenceClient(@NonNull Context context) {
        appContext = context.getApplicationContext();
        FirebaseCommunityConfig.warmUp(appContext);
    }

    public boolean isFirebaseReady() {
        return FirebaseCommunityConfig.isConfigured();
    }

    public void lookupPhone(@NonNull String e164PhoneNumber, @NonNull Callback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("phoneNumber", e164PhoneNumber.trim());
        call(PHONE_FUNCTION, payload, callback);
    }

    public void lookupIp(@NonNull String ipAddress, @NonNull Callback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ipAddress", ipAddress.trim());
        call(IP_FUNCTION, payload, callback);
    }

    private void call(
            @NonNull String functionName,
            @NonNull Map<String, Object> payload,
            @NonNull Callback callback
    ) {
        FirebaseApp app = FirebaseCommunityConfig.getOrInitialize(appContext);
        if (app == null) {
            callback.onError("FIREBASE_NOT_CONFIGURED");
            return;
        }

        FirebaseFunctions functions = FirebaseFunctions.getInstance(app, FUNCTIONS_REGION);
        functions.getHttpsCallable(functionName)
                .call(payload)
                .addOnSuccessListener(result -> callback.onSuccess(parseResult(result)))
                .addOnFailureListener(error -> callback.onError(
                        error.getMessage() == null || error.getMessage().trim().isEmpty()
                                ? "CLOUD_LOOKUP_FAILED"
                                : error.getMessage().trim()
                ));
    }

    @NonNull
    private LookupResponse parseResult(@NonNull HttpsCallableResult result) {
        Object raw = result.getData();
        if (!(raw instanceof Map)) {
            return new LookupResponse(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        Map<?, ?> map = (Map<?, ?>) raw;
        return new LookupResponse(
                toMapList(map.get("evidence")),
                toMapList(map.get("providerStatus")),
                toStringList(map.get("configuredSources"))
        );
    }

    @NonNull
    private List<Map<String, Object>> toMapList(@Nullable Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            output.add(converted);
        }
        return Collections.unmodifiableList(output);
    }

    @NonNull
    private List<String> toStringList(@Nullable Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<String> output = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item != null) {
                String value = String.valueOf(item).trim();
                if (!value.isEmpty()) {
                    output.add(value);
                }
            }
        }
        return Collections.unmodifiableList(output);
    }
}
