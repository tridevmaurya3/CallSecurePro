package com.tridev.callsecurepro.data.protection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ProtectionRuleDao {

    @Nullable
    @Query("SELECT * FROM protection_rules WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    ProtectionRuleEntity findByNumber(@NonNull String normalizedNumber);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull ProtectionRuleEntity rule);

    @Query("SELECT COUNT(*) FROM protection_rules WHERE userBlocked = 1")
    int getBlockedCount();

    @Query("SELECT COUNT(*) FROM protection_rules WHERE trusted = 1")
    int getTrustedCount();

    @Query("SELECT COALESCE(SUM(spamReports), 0) FROM protection_rules")
    int getTotalReportCount();

    @NonNull
    @Query("SELECT * FROM protection_rules WHERE userBlocked = 1 OR trusted = 1 OR spamReports > 0 ORDER BY updatedAt DESC LIMIT :limit")
    List<ProtectionRuleEntity> getRecentRules(int limit);
}
