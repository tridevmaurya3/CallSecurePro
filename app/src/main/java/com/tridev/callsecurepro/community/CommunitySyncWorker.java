package com.tridev.callsecurepro.community;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.community.CommunityReportDao;
import com.tridev.callsecurepro.data.community.CommunityReportEntity;

import java.util.List;

public final class CommunitySyncWorker extends Worker {

    private static final int BATCH_SIZE = 25;

    public CommunitySyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
        CommunityNetworkGateway gateway = CommunityNetworkProvider.get(appContext);
        if (!gateway.isAvailable()) {
            return Result.success();
        }

        CommunityReportDao dao = CallSecureDatabase.getInstance(appContext).communityReportDao();
        List<CommunityReportEntity> reports = dao.getPending(BATCH_SIZE);
        if (reports.isEmpty()) {
            return Result.success();
        }

        boolean transientFailure = false;
        long now = System.currentTimeMillis();
        for (CommunityReportEntity report : reports) {
            try {
                CommunityNetworkGateway.ReportSubmissionResult result = gateway.submitReport(
                        report.normalizedNumber,
                        report.category
                );
                if (result.accepted) {
                    dao.markSent(report.id, result.serverReportId, now);
                } else {
                    dao.markAttempted(report.id, now);
                    if (!"BACKEND_UNAVAILABLE".equals(result.statusCode)) {
                        transientFailure = true;
                    }
                }
            } catch (RuntimeException exception) {
                dao.markAttempted(report.id, now);
                transientFailure = true;
            }
        }

        if (!dao.getPending(1).isEmpty()) {
            return transientFailure ? Result.retry() : Result.success();
        }
        return Result.success();
    }
}
