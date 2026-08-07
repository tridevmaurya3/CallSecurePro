package com.tridev.callsecurepro.data.protection;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScreeningEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(@NonNull ScreeningEventEntity entity);

    @NonNull
    @Query("SELECT * FROM screening_events ORDER BY screenedAt DESC LIMIT :limit")
    List<ScreeningEventEntity> recent(int limit);

    @Query("SELECT COUNT(*) FROM screening_events WHERE action = :action")
    int countByAction(@NonNull String action);

    @Query("DELETE FROM screening_events")
    void clear();

    @Query("DELETE FROM screening_events WHERE id NOT IN (SELECT id FROM screening_events ORDER BY screenedAt DESC LIMIT :maxItems)")
    void trimToLatest(int maxItems);
}
