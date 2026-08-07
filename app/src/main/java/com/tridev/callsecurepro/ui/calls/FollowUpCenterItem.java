package com.tridev.callsecurepro.ui.calls;

import androidx.annotation.NonNull;

public final class FollowUpCenterItem {

    public enum Bucket {
        OVERDUE,
        UPCOMING,
        DONE
    }

    public final long callLogId;
    @NonNull
    public final String displayName;
    @NonNull
    public final String number;
    @NonNull
    public final String noteText;
    public final long callTimestamp;
    public final long followUpAt;
    public final boolean followUpDone;
    public final boolean callLogAvailable;
    public final boolean missedCall;
    public final int priorityScore;
    @NonNull
    public final String priorityReason;

    public FollowUpCenterItem(
            long callLogId,
            @NonNull String displayName,
            @NonNull String number,
            @NonNull String noteText,
            long callTimestamp,
            long followUpAt,
            boolean followUpDone,
            boolean callLogAvailable,
            boolean missedCall,
            int priorityScore,
            @NonNull String priorityReason
    ) {
        this.callLogId = callLogId;
        this.displayName = displayName;
        this.number = number;
        this.noteText = noteText;
        this.callTimestamp = callTimestamp;
        this.followUpAt = followUpAt;
        this.followUpDone = followUpDone;
        this.callLogAvailable = callLogAvailable;
        this.missedCall = missedCall;
        this.priorityScore = Math.max(0, Math.min(100, priorityScore));
        this.priorityReason = priorityReason;
    }

    @NonNull
    public Bucket bucket(long now) {
        if (followUpDone) {
            return Bucket.DONE;
        }
        return followUpAt < now ? Bucket.OVERDUE : Bucket.UPCOMING;
    }
}
