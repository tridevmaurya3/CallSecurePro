package com.tridev.callsecurepro.community;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.identity.CallerIdentityRemoteSource;

/**
 * Contract for the future authenticated Call Secure community backend.
 *
 * Implementations must never invent identities or report acknowledgements. A null lookup means
 * no trustworthy cloud identity is available. submitReport must return success only after a real
 * backend accepts the report.
 */
public interface CommunityNetworkGateway {

    boolean isAvailable();

    @NonNull
    String getStatusLabel();

    @Nullable
    CallerIdentityRemoteSource.RemoteIdentity lookup(@NonNull String normalizedNumber);

    @NonNull
    ReportSubmissionResult submitReport(
            @NonNull String normalizedNumber,
            @NonNull String category
    );

    final class ReportSubmissionResult {
        public final boolean accepted;
        @Nullable
        public final String serverReportId;
        @NonNull
        public final String statusCode;

        public ReportSubmissionResult(
                boolean accepted,
                @Nullable String serverReportId,
                @NonNull String statusCode
        ) {
            this.accepted = accepted;
            this.serverReportId = serverReportId;
            this.statusCode = statusCode;
        }

        @NonNull
        public static ReportSubmissionResult unavailable() {
            return new ReportSubmissionResult(false, null, "BACKEND_UNAVAILABLE");
        }
    }
}
