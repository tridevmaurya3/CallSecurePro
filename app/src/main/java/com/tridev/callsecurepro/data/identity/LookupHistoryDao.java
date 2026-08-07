package com.tridev.callsecurepro.data.identity;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LookupHistoryDao {

    @Insert
    long insert(LookupHistoryEntity entity);

    @Query("SELECT * FROM lookup_history ORDER BY lookedUpAt DESC LIMIT :limit")
    List<LookupHistoryEntity> getRecent(int limit);

    @Query("DELETE FROM lookup_history WHERE id NOT IN (SELECT id FROM lookup_history ORDER BY lookedUpAt DESC LIMIT :keepCount)")
    void trimToLatest(int keepCount);

    @Query("DELETE FROM lookup_history")
    void clearAll();
}
