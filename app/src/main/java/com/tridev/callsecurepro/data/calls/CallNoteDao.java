package com.tridev.callsecurepro.data.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CallNoteDao {

    @Nullable
    @Query("SELECT * FROM call_notes WHERE callLogId = :callLogId LIMIT 1")
    CallNoteEntity findByCallLogId(long callLogId);

    @NonNull
    @Query("SELECT * FROM call_notes WHERE callLogId IN (:callLogIds)")
    List<CallNoteEntity> findByCallLogIds(@NonNull List<Long> callLogIds);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull CallNoteEntity entity);

    @Query("DELETE FROM call_notes WHERE callLogId = :callLogId")
    void deleteByCallLogId(long callLogId);

    @Query("UPDATE call_notes SET followUpDone = 1, updatedAt = :updatedAt WHERE callLogId = :callLogId")
    void markFollowUpDone(long callLogId, long updatedAt);

    @Query("UPDATE call_notes SET followUpAt = :followUpAt, followUpDone = 0, updatedAt = :updatedAt WHERE callLogId = :callLogId")
    void snoozeFollowUp(long callLogId, long followUpAt, long updatedAt);

    @Query("SELECT COUNT(*) FROM call_notes WHERE followUpAt > 0 AND followUpDone = 0")
    int countPendingFollowUps();

    @NonNull
    @Query("SELECT * FROM call_notes WHERE followUpAt > 0 ORDER BY followUpDone ASC, followUpAt ASC LIMIT :limit")
    List<CallNoteEntity> getFollowUps(int limit);

    @NonNull
    @Query("SELECT * FROM call_notes WHERE followUpAt > 0 AND followUpDone = 0 ORDER BY followUpAt ASC LIMIT :limit")
    List<CallNoteEntity> getPendingFollowUps(int limit);

    @Query("SELECT COUNT(*) FROM call_notes WHERE followUpAt > 0 AND followUpDone = 0 AND followUpAt < :now")
    int countOverdueFollowUps(long now);

    @Query("SELECT COUNT(*) FROM call_notes WHERE followUpAt >= :now AND followUpDone = 0")
    int countUpcomingFollowUps(long now);

    @Query("SELECT COUNT(*) FROM call_notes WHERE followUpAt > 0 AND followUpDone = 1")
    int countCompletedFollowUps();
}
