package com.tridev.callsecurepro.data.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "lookup_history",
        indices = {
                @Index(value = {"normalizedNumber"}),
                @Index(value = {"lookedUpAt"})
        }
)
public class LookupHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String normalizedNumber;

    @NonNull
    public String queryNumber;

    @Nullable
    public String resolvedName;

    @NonNull
    public String identityType;

    @NonNull
    public String source;

    @NonNull
    public String riskLevel;

    public long lookedUpAt;

    public LookupHistoryEntity(
            @NonNull String normalizedNumber,
            @NonNull String queryNumber,
            @Nullable String resolvedName,
            @NonNull String identityType,
            @NonNull String source,
            @NonNull String riskLevel,
            long lookedUpAt
    ) {
        this.normalizedNumber = normalizedNumber;
        this.queryNumber = queryNumber;
        this.resolvedName = resolvedName;
        this.identityType = identityType;
        this.source = source;
        this.riskLevel = riskLevel;
        this.lookedUpAt = lookedUpAt;
    }
}
