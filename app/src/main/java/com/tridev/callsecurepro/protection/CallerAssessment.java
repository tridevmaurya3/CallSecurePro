package com.tridev.callsecurepro.protection;

import androidx.annotation.NonNull;

public final class CallerAssessment {

    public enum Level {
        SAFE,
        UNKNOWN,
        SUSPICIOUS,
        SPAM
    }

    @NonNull
    private final String normalizedNumber;
    @NonNull
    private final Level level;
    private final int riskScore;
    @NonNull
    private final String reason;
    private final boolean savedContact;
    private final boolean userBlocked;
    private final boolean trusted;
    private final int localReports;
    private final boolean shouldBlock;
    private final boolean shouldSilence;

    public CallerAssessment(
            @NonNull String normalizedNumber,
            @NonNull Level level,
            int riskScore,
            @NonNull String reason,
            boolean savedContact,
            boolean userBlocked,
            boolean trusted,
            int localReports,
            boolean shouldBlock,
            boolean shouldSilence
    ) {
        this.normalizedNumber = normalizedNumber;
        this.level = level;
        this.riskScore = Math.max(0, Math.min(100, riskScore));
        this.reason = reason;
        this.savedContact = savedContact;
        this.userBlocked = userBlocked;
        this.trusted = trusted;
        this.localReports = Math.max(0, localReports);
        this.shouldBlock = shouldBlock;
        this.shouldSilence = shouldSilence;
    }

    @NonNull
    public String getNormalizedNumber() {
        return normalizedNumber;
    }

    @NonNull
    public Level getLevel() {
        return level;
    }

    public int getRiskScore() {
        return riskScore;
    }

    @NonNull
    public String getReason() {
        return reason;
    }

    public boolean isSavedContact() {
        return savedContact;
    }

    public boolean isUserBlocked() {
        return userBlocked;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public int getLocalReports() {
        return localReports;
    }

    public boolean shouldBlock() {
        return shouldBlock;
    }

    public boolean shouldSilence() {
        return shouldSilence;
    }
}
