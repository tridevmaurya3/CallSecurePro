package com.tridev.callsecurepro.ui.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CallHistoryItem {

    private final long id;
    @NonNull
    private final String number;
    @Nullable
    private final String cachedName;
    private final int type;
    private final long timestamp;
    private final long durationSeconds;
    @Nullable
    private final String noteText;
    private final long followUpAt;
    private final boolean followUpDone;

    public CallHistoryItem(
            long id,
            @NonNull String number,
            @Nullable String cachedName,
            int type,
            long timestamp,
            long durationSeconds
    ) {
        this(id, number, cachedName, type, timestamp, durationSeconds, null, 0L, false);
    }

    public CallHistoryItem(
            long id,
            @NonNull String number,
            @Nullable String cachedName,
            int type,
            long timestamp,
            long durationSeconds,
            @Nullable String noteText,
            long followUpAt,
            boolean followUpDone
    ) {
        this.id = id;
        this.number = number;
        this.cachedName = cachedName;
        this.type = type;
        this.timestamp = timestamp;
        this.durationSeconds = durationSeconds;
        this.noteText = noteText;
        this.followUpAt = followUpAt;
        this.followUpDone = followUpDone;
    }

    public long getId() {
        return id;
    }

    @NonNull
    public String getNumber() {
        return number;
    }

    @Nullable
    public String getCachedName() {
        return cachedName;
    }

    public int getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    @Nullable
    public String getNoteText() {
        return noteText;
    }

    public long getFollowUpAt() {
        return followUpAt;
    }

    public boolean isFollowUpDone() {
        return followUpDone;
    }

    public boolean hasNote() {
        return noteText != null && !noteText.trim().isEmpty();
    }

    public boolean hasFollowUp() {
        return followUpAt > 0L;
    }

    @NonNull
    public CallHistoryItem withNoteMetadata(
            @Nullable String noteText,
            long followUpAt,
            boolean followUpDone
    ) {
        return new CallHistoryItem(
                id,
                number,
                cachedName,
                type,
                timestamp,
                durationSeconds,
                noteText,
                followUpAt,
                followUpDone
        );
    }

    @NonNull
    public String getDisplayTitle() {
        if (cachedName != null && !cachedName.trim().isEmpty()) {
            return cachedName.trim();
        }
        return number;
    }
}
