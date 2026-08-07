package com.tridev.callsecurepro.data.community;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CommunityReportDao {

    @Insert
    long insert(@NonNull CommunityReportEntity entity);

    @Query("SELECT * FROM community_report_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    @NonNull
    List<CommunityReportEntity> getPending(int limit);

    @Query("SELECT * FROM community_report_outbox ORDER BY createdAt DESC LIMIT :limit")
    @NonNull
    List<CommunityReportEntity> getRecent(int limit);

    @Query("SELECT COUNT(*) FROM community_report_outbox WHERE status = 'PENDING'")
    int countPending();

    @Query("SELECT COUNT(*) FROM community_report_outbox WHERE status = 'SENT'")
    int countSent();

    @Query("SELECT COUNT(*) FROM community_report_outbox WHERE normalizedNumber = :normalizedNumber AND category = :category AND createdAt >= :since")
    int countRecentDuplicate(
            @NonNull String normalizedNumber,
            @NonNull String category,
            long since
    );

    @Query("UPDATE community_report_outbox SET status = 'SENT', serverReportId = :serverReportId, updatedAt = :updatedAt, attemptCount = attemptCount + 1 WHERE id = :id")
    void markSent(long id, String serverReportId, long updatedAt);

    @Query("UPDATE community_report_outbox SET updatedAt = :updatedAt, attemptCount = attemptCount + 1 WHERE id = :id")
    void markAttempted(long id, long updatedAt);
}
