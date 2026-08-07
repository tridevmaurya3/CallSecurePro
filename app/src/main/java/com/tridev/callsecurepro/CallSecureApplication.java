package com.tridev.callsecurepro;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.community.FirebaseCommunityConfig;
import com.tridev.callsecurepro.theme.AppVisualThemeManager;
import com.tridev.callsecurepro.theme.GlobalDialButtonManager;

/** Applies saved visual settings and shared app services across activities. */
public class CallSecureApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCommunityConfig.warmUp(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(
                    @NonNull Activity activity,
                    @Nullable Bundle savedInstanceState
            ) {
                applySharedUi(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                applySharedUi(activity);
            }

            private void applySharedUi(@NonNull Activity activity) {
                if (!(activity instanceof MainActivity)) {
                    AppVisualThemeManager.applyActivity(activity);
                }
                GlobalDialButtonManager.ensure(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(
                    @NonNull Activity activity,
                    @NonNull Bundle outState
            ) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }
}
