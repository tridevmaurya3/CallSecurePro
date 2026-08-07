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

    public CallHistoryItem(
            long id,
            @NonNull String number,
            @Nullable String cachedName,
            int type,
            long timestamp,
            long durationSeconds
    ) {
        this.id = id;
        this.number = number;
        this.cachedName = cachedName;
        this.type = type;
        this.timestamp = timestamp;
        this.durationSeconds = durationSeconds;
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

    @NonNull
    public String getDisplayTitle() {
        if (cachedName != null && !cachedName.trim().isEmpty()) {
            return cachedName.trim();
        }
        return number;
    }
}
