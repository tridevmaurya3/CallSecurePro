package com.tridev.callsecurepro.data.community;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "community_report_outbox",
        indices = {
                @Index("normalizedNumber"),
                @Index("status"),
                @Index("createdAt")
        }
)
public class CommunityReportEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public final String normalizedNumber;

    @NonNull
    public final String displayNumber;

    @NonNull
    public final String category;

    @NonNull
    public final String status;

    public final long createdAt;
    public final long updatedAt;
    public final int attemptCount;

    @Nullable
    public final String serverReportId;

    public CommunityReportEntity(
            long id,
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            @NonNull String category,
            @NonNull String status,
            long createdAt,
            long updatedAt,
            int attemptCount,
            @Nullable String serverReportId
    ) {
        this.id = id;
        this.normalizedNumber = normalizedNumber;
        this.displayNumber = displayNumber;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attemptCount = attemptCount;
        this.serverReportId = serverReportId;
    }
}
