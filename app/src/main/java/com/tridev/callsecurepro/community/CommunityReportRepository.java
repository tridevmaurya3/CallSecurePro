package com.tridev.callsecurepro.community;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.community.CommunityReportDao;
import com.tridev.callsecurepro.data.community.CommunityReportEntity;
import com.tridev.callsecurepro.protection.ProtectionRepository;

import java.util.List;

public final class CommunityReportRepository {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    private static final long DUPLICATE_WINDOW_MS = 24L * 60L * 60L * 1000L;
    private static final String UNIQUE_SYNC_WORK = "call_secure_community_report_sync";

    public enum Category {
        SPAM,
        SCAM_FRAUD,
        TELEMARKETING,
        BUSINESS,
        SAFE_LEGITIMATE,
        OTHER
    }

    public enum SubmitResult {
        SAVED_PENDING,
        DUPLICATE_RECENT,
        INVALID_NUMBER
    }

    private final Context appContext;
    private final CommunityReportDao reportDao;
    private final CommunityNetworkGateway gateway;

    public CommunityReportRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        reportDao = CallSecureDatabase.getInstance(appContext).communityReportDao();
        gateway = CommunityNetworkProvider.get(appContext);
    }

    @NonNull
    public SubmitResult submit(
            @NonNull String rawNumber,
            @NonNull Category category
    ) {
        String normalized = ProtectionRepository.normalize(rawNumber);
        if (normalized.isEmpty()) {
            return SubmitResult.INVALID_NUMBER;
        }

        long now = System.currentTimeMillis();
        int duplicates = reportDao.countRecentDuplicate(
                normalized,
                category.name(),
                now - DUPLICATE_WINDOW_MS
        );
        if (duplicates > 0) {
            return SubmitResult.DUPLICATE_RECENT;
        }

        reportDao.insert(new CommunityReportEntity(
                0L,
                normalized,
                rawNumber.trim().isEmpty() ? normalized : rawNumber.trim(),
                category.name(),
                STATUS_PENDING,
                now,
                now,
                0,
                null
        ));

        enqueueSyncIfAvailable();
        return SubmitResult.SAVED_PENDING;
    }

    public void enqueueSyncIfAvailable() {
        if (!gateway.isAvailable()) {
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CommunitySyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_SYNC_WORK,
                ExistingWorkPolicy.KEEP,
                request
        );
    }

    @NonNull
    public Snapshot getSnapshot() {
        return new Snapshot(
                gateway.isAvailable(),
                gateway.getStatusLabel(),
                reportDao.countPending(),
                reportDao.countSent(),
                reportDao.getRecent(50)
        );
    }

    public static final class Snapshot {
        public final boolean cloudAvailable;
        @NonNull
        public final String cloudStatus;
        public final int pendingCount;
        public final int sentCount;
        @NonNull
        public final List<CommunityReportEntity> recentReports;

        Snapshot(
                boolean cloudAvailable,
                @NonNull String cloudStatus,
                int pendingCount,
                int sentCount,
                @NonNull List<CommunityReportEntity> recentReports
        ) {
            this.cloudAvailable = cloudAvailable;
            this.cloudStatus = cloudStatus;
            this.pendingCount = Math.max(0, pendingCount);
            this.sentCount = Math.max(0, sentCount);
            this.recentReports = recentReports;
        }
    }
}
