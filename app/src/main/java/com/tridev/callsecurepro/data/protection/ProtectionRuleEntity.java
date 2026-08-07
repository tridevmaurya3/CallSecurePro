package com.tridev.callsecurepro.data.protection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * User-controlled local caller protection data.
 *
 * No cloud/community value is stored here unless it is explicitly added by a later
 * verified backend integration. In the current version these values are device-local.
 */
@Entity(tableName = "protection_rules")
public class ProtectionRuleEntity {

    @PrimaryKey
    @NonNull
    public String normalizedNumber;

    @NonNull
    public String displayNumber;

    public boolean userBlocked;
    public boolean trusted;
    public int spamReports;

    @Nullable
    public String customLabel;

    public long updatedAt;

    public ProtectionRuleEntity(
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            boolean userBlocked,
            boolean trusted,
            int spamReports,
            @Nullable String customLabel,
            long updatedAt
    ) {
        this.normalizedNumber = normalizedNumber;
        this.displayNumber = displayNumber;
        this.userBlocked = userBlocked;
        this.trusted = trusted;
        this.spamReports = spamReports;
        this.customLabel = customLabel;
        this.updatedAt = updatedAt;
    }
}
