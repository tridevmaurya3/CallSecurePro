package com.tridev.callsecurepro.data.protection;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "screening_events",
        indices = {
                @Index("screenedAt"),
                @Index("action")
        }
)
public class ScreeningEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String normalizedNumber;

    @NonNull
    public String displayNumber;

    @NonNull
    public String action;

    @NonNull
    public String reason;

    @NonNull
    public String riskLevel;

    public int riskScore;

    public long screenedAt;

    public ScreeningEventEntity(
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            @NonNull String action,
            @NonNull String reason,
            @NonNull String riskLevel,
            int riskScore,
            long screenedAt
    ) {
        this.normalizedNumber = normalizedNumber;
        this.displayNumber = displayNumber;
        this.action = action;
        this.reason = reason;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.screenedAt = screenedAt;
    }
}
