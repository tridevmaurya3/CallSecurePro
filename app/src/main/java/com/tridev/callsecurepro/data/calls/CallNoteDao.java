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
}
