package com.tridev.callsecurepro.community;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.tridev.callsecurepro.BuildConfig;

/**
 * Initializes Firebase from local Gradle properties instead of committing google-services.json.
 * Missing configuration keeps the app in local/offline community mode.
 */
public final class FirebaseCommunityConfig {

    private static final String FIREBASE_APP_NAME = "call-secure-community";
    private static volatile FirebaseApp firebaseApp;

    private FirebaseCommunityConfig() {
    }

    public static boolean isConfigured() {
        return !BuildConfig.FIREBASE_API_KEY.trim().isEmpty()
                && !BuildConfig.FIREBASE_APP_ID.trim().isEmpty()
                && !BuildConfig.FIREBASE_PROJECT_ID.trim().isEmpty();
    }

    public static boolean isAppCheckRequested() {
        return BuildConfig.FIREBASE_APP_CHECK_ENABLED;
    }

    /** Starts installation-scoped authentication without blocking app startup. */
    public static void warmUp(@NonNull Context context) {
        FirebaseApp app = getOrInitialize(context);
        if (app == null) {
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously();
        }
    }

    @Nullable
    public static FirebaseApp getOrInitialize(@NonNull Context context) {
        if (!isConfigured()) {
            return null;
        }
        if (firebaseApp != null) {
            return firebaseApp;
        }

        synchronized (FirebaseCommunityConfig.class) {
            if (firebaseApp != null) {
                return firebaseApp;
            }

            FirebaseApp existing = findExistingApp();
            if (existing != null) {
                firebaseApp = existing;
                return firebaseApp;
            }

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY.trim())
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID.trim())
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID.trim())
                    .build();

            FirebaseApp initialized = FirebaseApp.initializeApp(
                    context.getApplicationContext(),
                    options,
                    FIREBASE_APP_NAME
            );
            if (initialized == null) {
                return null;
            }

            if (BuildConfig.FIREBASE_APP_CHECK_ENABLED) {
                FirebaseAppCheck.getInstance(initialized).installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                );
            }

            firebaseApp = initialized;
            return firebaseApp;
        }
    }

    @Nullable
    private static FirebaseApp findExistingApp() {
        try {
            return FirebaseApp.getInstance(FIREBASE_APP_NAME);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @NonNull
    public static String statusLabel() {
        if (!isConfigured()) {
            return "Firebase project not configured";
        }
        return BuildConfig.FIREBASE_APP_CHECK_ENABLED
                ? "Firebase ready • App Check enabled"
                : "Firebase ready • App Check not enabled";
    }
}
