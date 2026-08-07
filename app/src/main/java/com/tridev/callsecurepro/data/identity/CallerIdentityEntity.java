package com.tridev.callsecurepro.data.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cached caller identity record.
 *
 * Only identities obtained from a real source should be stored here. The app must never
 * fabricate a person/business name or verification state for an unknown number.
 */
@Entity(tableName = "caller_identities")
public class CallerIdentityEntity {

    @PrimaryKey
    @NonNull
    public String normalizedNumber;

    @NonNull
    public String displayNumber;

    @Nullable
    public String displayName;

    @Nullable
    public String businessName;

    @Nullable
    public String category;

    @NonNull
    public String identityType;

    @NonNull
    public String verificationLevel;

    @NonNull
    public String source;

    public int confidence;
    public long updatedAt;
    public long expiresAt;

    public CallerIdentityEntity(
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            @Nullable String displayName,
            @Nullable String businessName,
            @Nullable String category,
            @NonNull String identityType,
            @NonNull String verificationLevel,
            @NonNull String source,
            int confidence,
            long updatedAt,
            long expiresAt
    ) {
        this.normalizedNumber = normalizedNumber;
        this.displayNumber = displayNumber;
        this.displayName = displayName;
        this.businessName = businessName;
        this.category = category;
        this.identityType = identityType;
        this.verificationLevel = verificationLevel;
        this.source = source;
        this.confidence = confidence;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
    }
}
