package com.tridev.callsecurepro.data.calls;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "call_notes",
        indices = {
                @Index("normalizedNumber"),
                @Index("followUpAt")
        }
)
public class CallNoteEntity {

    @PrimaryKey
    public long callLogId;

    @NonNull
    public String normalizedNumber;

    public long callTimestamp;

    @NonNull
    public String noteText;

    public long followUpAt;

    public boolean followUpDone;

    public long updatedAt;

    public CallNoteEntity(
            long callLogId,
            @NonNull String normalizedNumber,
            long callTimestamp,
            @NonNull String noteText,
            long followUpAt,
            boolean followUpDone,
            long updatedAt
    ) {
        this.callLogId = callLogId;
        this.normalizedNumber = normalizedNumber;
        this.callTimestamp = callTimestamp;
        this.noteText = noteText;
        this.followUpAt = followUpAt;
        this.followUpDone = followUpDone;
        this.updatedAt = updatedAt;
    }
}
