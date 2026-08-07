package com.tridev.callsecurepro.data.identity;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CallerIdentityDao {

    @Nullable
    @Query("SELECT * FROM caller_identities WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    CallerIdentityEntity findByNumber(String normalizedNumber);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CallerIdentityEntity entity);

    @Query("DELETE FROM caller_identities WHERE expiresAt > 0 AND expiresAt < :now")
    int deleteExpired(long now);
}
