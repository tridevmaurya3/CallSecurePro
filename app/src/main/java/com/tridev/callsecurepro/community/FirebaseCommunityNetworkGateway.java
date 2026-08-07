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
import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;
import com.tridev.callsecurepro.identity.CallerIdentityResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Real Firebase Auth + Cloud Firestore implementation for the Call Secure community network.
 *
 * Reads are exact document gets against a SHA-256 phone-number key. Community clients can submit
 * reports but cannot directly publish or edit caller-directory aggregate identities.
 */
public final class FirebaseCommunityNetworkGateway implements CommunityNetworkGateway {

    private static final String DIRECTORY_COLLECTION = "caller_directory";
    private static final String REPORT_ROOT_COLLECTION = "community_report_submissions";
    private static final long AUTH_TIMEOUT_SECONDS = 3L;
    private static final long LOOKUP_TIMEOUT_MILLIS = 1500L;
    private static final long WRITE_TIMEOUT_SECONDS = 8L;

    private final Context appContext;

    public FirebaseCommunityNetworkGateway(@NonNull Context context) {
        appContext = context.getApplicationContext();
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
        if (!isAvailable() || normalizedNumber.trim().isEmpty() || isMainThread()) {
            return null;
        }

        FirebaseApp app = FirebaseCommunityConfig.getOrInitialize(appContext);
        if (app == null || !ensureAuthenticated(app)) {
            return null;
        }

        String numberHash = CommunityNumberHasher.sha256(normalizedNumber);
        try {
            DocumentSnapshot document = Tasks.await(
                    FirebaseFirestore.getInstance(app)
                            .collection(DIRECTORY_COLLECTION)
                            .document(numberHash)
                            .get(Source.SERVER),
                    LOOKUP_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
            );
            if (!document.exists() || !"PUBLISHED".equals(document.getString("status"))) {
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
                    displayNumber == null ? normalizedNumber : displayNumber,
                    displayName,
                    category,
                    identityType,
                    verificationLevel,
                    source,
                    confidence,
                    expiryMillis
            );
        } catch (Exception ignored) {
            // Caller identification always falls back to local/cache logic on cloud failure.
            return null;
        }
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

        String reportId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("numberHash", CommunityNumberHasher.sha256(normalizedNumber));
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
        } catch (java.util.concurrent.TimeoutException exception) {
            return new ReportSubmissionResult(false, null, "TIMEOUT");
        } catch (Exception exception) {
            return new ReportSubmissionResult(false, null, "FIRESTORE_ERROR");
        }
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
                    AUTH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            return result.getUser() != null;
        } catch (Exception ignored) {
            return false;
        }
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
