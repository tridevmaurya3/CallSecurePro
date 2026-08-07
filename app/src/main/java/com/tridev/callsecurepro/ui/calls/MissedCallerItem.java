package com.tridev.callsecurepro.ui.calls;

import androidx.annotation.NonNull;

public final class MissedCallerItem {

    public final long latestCallLogId;
    @NonNull
    public final String number;
    @NonNull
    public final String displayName;
    public final long latestTimestamp;
    public final int totalCount30Days;
    public final int count7Days;
    public final int priorityScore;
    public final boolean dialable;

    public MissedCallerItem(
            long latestCallLogId,
            @NonNull String number,
            @NonNull String displayName,
            long latestTimestamp,
            int totalCount30Days,
            int count7Days,
            int priorityScore,
            boolean dialable
    ) {
        this.latestCallLogId = latestCallLogId;
        this.number = number;
        this.displayName = displayName;
        this.latestTimestamp = latestTimestamp;
        this.totalCount30Days = totalCount30Days;
        this.count7Days = count7Days;
        this.priorityScore = Math.max(0, Math.min(100, priorityScore));
        this.dialable = dialable;
    }

    public boolean isRepeatCaller() {
        return count7Days >= 2;
    }
}
