package com.tridev.callsecurepro.community;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.tridev.callsecurepro.BuildConfig;
import com.tridev.callsecurepro.identity.CallerIdentityLookupMode;
import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;
import com.tridev.callsecurepro.identity.CallerIdentityResult;
import com.tridev.callsecurepro.identity.NumberIntelligenceAnalyzer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Real Firebase Auth + Cloud Firestore implementation for the Call Secure community network.
 *
 * Directory records are addressed by SHA-256 hashes. New records use a canonical E.164 phone
 * number before hashing, while lookup keeps backward-compatible aliases for older national-format
 * records. No raw number is written by the community-report path.
 */
public final class FirebaseCommunityNetworkGateway implements CommunityNetworkGateway {

    private static final String DIRECTORY_COLLECTION = "caller_directory";
    private static final String REPORT_ROOT_COLLECTION = "community_report_submissions";
    private static final long AUTH_TIMEOUT_MILLIS = 1000L;
    private static final long USER_LOOKUP_TIMEOUT_MILLIS = 2200L;
    private static final long PASSIVE_LOOKUP_TIMEOUT_MILLIS = 900L;
    private static final long WRITE_TIMEOUT_SECONDS = 8L;
    private static final int USER_LOOKUP_MAX_KEYS = 3;
    private static final int PASSIVE_LOOKUP_MAX_KEYS = 2;

    private final Context appContext;
    private final NumberIntelligenceAnalyzer numberAnalyzer;

    public FirebaseCommunityNetworkGateway(@NonNull Context context) {
        appContext = context.getApplicationContext();
        numberAnalyzer = new NumberIntelligenceAnalyzer();
    }

    @Override
    public boolean isAvailable() {
        return FirebaseCommunityConfig.isConfigured();
    }

    @NonNull
    @Override
    public String getStatusLabel() {
        if (!FirebaseCommunityConfig.isConfigured()) {
            return FirebaseCommunityConfig.statusLabel();
        }

        FirebaseApp app = FirebaseCommunityConfig.getOrInitialize(appContext);
        if (app == null) {
            return "Firebase configuration could not initialize";
        }

        FirebaseUser user = FirebaseAuth.getInstance(app).getCurrentUser();
        if (user != null) {
            return FirebaseCommunityConfig.isAppCheckRequested()
                    ? "Firebase connected • authenticated • App Check enabled"
                    : "Firebase connected • authenticated • App Check not enabled";
        }
        return FirebaseCommunityConfig.isAppCheckRequested()
                ? "Firebase configured • authentication pending • App Check enabled"
                : "Firebase configured • authentication pending • App Check not enabled";
    }

    @Nullable
    @Override
    public CallerIdentityRemoteSource.RemoteIdentity lookup(@NonNull String normalizedNumber) {
        return lookup(normalizedNumber, CallerIdentityLookupMode.USER_INITIATED);
    }

    @Nullable
    @Override
    public CallerIdentityRemoteSource.RemoteIdentity lookup(
            @NonNull String normalizedNumber,
            @NonNull CallerIdentityLookupMode mode
    ) {
        String cleanNumber = normalizedNumber.trim();
        if (!isAvailable() || cleanNumber.isEmpty() || isMainThread()) {
            return null;
        }

        FirebaseApp app = FirebaseCommunityConfig.getOrInitialize(appContext);
        if (app == null || !ensureAuthenticated(app)) {
            return null;
        }

        List<String> lookupKeys = buildLookupKeys(cleanNumber);
        int maxKeys = mode.areSlowRemoteProvidersAllowed()
                ? USER_LOOKUP_MAX_KEYS
                : PASSIVE_LOOKUP_MAX_KEYS;
        long timeoutMillis = mode.areSlowRemoteProvidersAllowed()
                ? USER_LOOKUP_TIMEOUT_MILLIS
                : PASSIVE_LOOKUP_TIMEOUT_MILLIS;

        FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
        int attempts = Math.min(maxKeys, lookupKeys.size());
        for (int index = 0; index < attempts; index++) {
            String numberHash = CommunityNumberHasher.sha256(lookupKeys.get(index));
            try {
                DocumentSnapshot document = Tasks.await(
                        firestore.collection(DIRECTORY_COLLECTION)
                                .document(numberHash)
                                .get(Source.SERVER),
                        timeoutMillis,
                        TimeUnit.MILLISECONDS
                );
                if (!document.exists()) {
                    continue;
                }
                CallerIdentityRemoteSource.RemoteIdentity identity = parseDirectoryDocument(
                        document,
                        cleanNumber
                );
                if (identity != null) {
                    return identity;
                }
                return null;
            } catch (TimeoutException timeout) {
                // Do not stack extra cloud waits after a timeout, especially during call screening.
                return null;
            } catch (Exception ignored) {
                // Older restrictive rules can return PERMISSION_DENIED for a missing hash. Continue
                // to the next canonical/legacy alias; genuine cloud failure still falls back safely.
            }
        }
        return null;
    }

    @NonNull
    @Override
    public ReportSubmissionResult submitReport(
            @NonNull String normalizedNumber,
            @NonNull String category
    ) {
        if (!isAvailable()) {
            return ReportSubmissionResult.unavailable();
        }
        if (normalizedNumber.trim().isEmpty() || category.trim().isEmpty()) {
            return new ReportSubmissionResult(false, null, "INVALID_REPORT");
        }
        if (isMainThread()) {
            return new ReportSubmissionResult(false, null, "MAIN_THREAD_BLOCKED");
        }

        FirebaseApp app = FirebaseCommunityConfig.getOrInitialize(appContext);
        if (app == null) {
            return ReportSubmissionResult.unavailable();
        }
        if (!ensureAuthenticated(app)) {
            return new ReportSubmissionResult(false, null, "AUTH_UNAVAILABLE");
        }

        FirebaseUser user = FirebaseAuth.getInstance(app).getCurrentUser();
        if (user == null) {
            return new ReportSubmissionResult(false, null, "AUTH_UNAVAILABLE");
        }

        String canonicalNumber = canonicalCloudNumber(normalizedNumber.trim());
        String numberHash = CommunityNumberHasher.sha256(canonicalNumber);
        String reportId = dailyReportId(numberHash, category);
        Map<String, Object> payload = new HashMap<>();
        payload.put("numberHash", numberHash);
        payload.put("category", category);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("appVersion", BuildConfig.VERSION_NAME);
        payload.put("schemaVersion", 1L);
        payload.put("client", "ANDROID");

        try {
            Tasks.await(
                    FirebaseFirestore.getInstance(app)
                            .collection(REPORT_ROOT_COLLECTION)
                            .document(user.getUid())
                            .collection("reports")
                            .document(reportId)
                            .set(payload),
                    WRITE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            return new ReportSubmissionResult(true, reportId, "ACCEPTED");
        } catch (TimeoutException exception) {
            return new ReportSubmissionResult(false, null, "TIMEOUT");
        } catch (Exception exception) {
            return new ReportSubmissionResult(false, null, "FIRESTORE_ERROR");
        }
    }

    @Nullable
    private CallerIdentityRemoteSource.RemoteIdentity parseDirectoryDocument(
            @NonNull DocumentSnapshot document,
            @NonNull String fallbackDisplayNumber
    ) {
        if (!"PUBLISHED".equals(document.getString("status"))) {
            return null;
        }

        String displayName = clean(document.getString("displayName"));
        String displayNumber = clean(document.getString("displayNumber"));
        String category = clean(document.getString("category"));
        String source = clean(document.getString("source"));
        Long confidenceValue = document.getLong("confidence");
        Timestamp expiresAt = document.getTimestamp("expiresAt");

        if (displayName == null || source == null || expiresAt == null) {
            return null;
        }
        long expiryMillis = expiresAt.toDate().getTime();
        if (expiryMillis <= System.currentTimeMillis()) {
            return null;
        }

        CallerIdentityResult.IdentityType identityType = parseIdentityType(
                document.getString("identityType")
        );
        CallerIdentityResult.VerificationLevel verificationLevel = parseVerification(
                document.getString("verificationLevel")
        );
        int confidence = confidenceValue == null
                ? 0
                : Math.max(0, Math.min(100, confidenceValue.intValue()));

        return new CallerIdentityRemoteSource.RemoteIdentity(
                displayNumber == null ? fallbackDisplayNumber : displayNumber,
                displayName,
                category,
                identityType,
                verificationLevel,
                source,
                confidence,
                expiryMillis
        );
    }

    @NonNull
    private List<String> buildLookupKeys(@NonNull String number) {
        Set<String> ordered = new LinkedHashSet<>();
        NumberIntelligenceAnalyzer.Result result = numberAnalyzer.analyze(number);

        if (result.isParsed() && !result.getE164Format().trim().isEmpty()) {
            String e164 = result.getE164Format().trim();
            ordered.add(e164);
            ordered.add(number);

            int callingCode = result.getCountryCallingCode();
            String prefix = callingCode > 0 ? "+" + callingCode : "";
            if (!prefix.isEmpty() && e164.startsWith(prefix) && e164.length() > prefix.length()) {
                ordered.add(e164.substring(prefix.length()));
            }
        } else {
            ordered.add(number);
        }

        return new ArrayList<>(ordered);
    }

    @NonNull
    private String canonicalCloudNumber(@NonNull String number) {
        List<String> lookupKeys = buildLookupKeys(number);
        return lookupKeys.isEmpty() ? number : lookupKeys.get(0);
    }

    private boolean ensureAuthenticated(@NonNull FirebaseApp app) {
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        if (auth.getCurrentUser() != null) {
            return true;
        }
        if (isMainThread()) {
            return false;
        }
        try {
            AuthResult result = Tasks.await(
                    auth.signInAnonymously(),
                    AUTH_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
            );
            return result.getUser() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    private String dailyReportId(@NonNull String numberHash, @NonNull String category) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String utcDay = format.format(new Date());
        return CommunityNumberHasher.sha256(numberHash + ":" + category + ":" + utcDay);
    }

    private boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    @Nullable
    private String clean(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @NonNull
    private CallerIdentityResult.IdentityType parseIdentityType(@Nullable String value) {
        if (value == null) {
            return CallerIdentityResult.IdentityType.UNKNOWN;
        }
        try {
            CallerIdentityResult.IdentityType type = CallerIdentityResult.IdentityType.valueOf(value);
            return type == CallerIdentityResult.IdentityType.CONTACT
                    ? CallerIdentityResult.IdentityType.UNKNOWN
                    : type;
        } catch (IllegalArgumentException ignored) {
            return CallerIdentityResult.IdentityType.UNKNOWN;
        }
    }

    @NonNull
    private CallerIdentityResult.VerificationLevel parseVerification(@Nullable String value) {
        if (value == null) {
            return CallerIdentityResult.VerificationLevel.UNVERIFIED;
        }
        try {
            CallerIdentityResult.VerificationLevel level =
                    CallerIdentityResult.VerificationLevel.valueOf(value);
            return level == CallerIdentityResult.VerificationLevel.LOCAL_MATCH
                    ? CallerIdentityResult.VerificationLevel.UNVERIFIED
                    : level;
        } catch (IllegalArgumentException ignored) {
            return CallerIdentityResult.VerificationLevel.UNVERIFIED;
        }
    }
}
