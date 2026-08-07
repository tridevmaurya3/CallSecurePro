package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.protection.CallerAssessment;

public final class CallerIdentityResult {

    public enum IdentityType {
        CONTACT,
        PERSON,
        BUSINESS,
        UNKNOWN
    }

    public enum VerificationLevel {
        LOCAL_MATCH,
        UNVERIFIED,
        VERIFIED
    }

    @NonNull
    private final String normalizedNumber;
    @NonNull
    private final String displayNumber;
    @Nullable
    private final String displayName;
    @Nullable
    private final String category;
    @NonNull
    private final IdentityType identityType;
    @NonNull
    private final VerificationLevel verificationLevel;
    @NonNull
    private final String source;
    private final int confidence;
    @NonNull
    private final CallerAssessment assessment;

    public CallerIdentityResult(
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            @Nullable String displayName,
            @Nullable String category,
            @NonNull IdentityType identityType,
            @NonNull VerificationLevel verificationLevel,
            @NonNull String source,
            int confidence,
            @NonNull CallerAssessment assessment
    ) {
        this.normalizedNumber = normalizedNumber;
        this.displayNumber = displayNumber;
        this.displayName = displayName;
        this.category = category;
        this.identityType = identityType;
        this.verificationLevel = verificationLevel;
        this.source = source;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.assessment = assessment;
    }

    @NonNull
    public String getNormalizedNumber() {
        return normalizedNumber;
    }

    @NonNull
    public String getDisplayNumber() {
        return displayNumber;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getCategory() {
        return category;
    }

    @NonNull
    public IdentityType getIdentityType() {
        return identityType;
    }

    @NonNull
    public VerificationLevel getVerificationLevel() {
        return verificationLevel;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    public int getConfidence() {
        return confidence;
    }

    @NonNull
    public CallerAssessment getAssessment() {
        return assessment;
    }

    public boolean hasResolvedName() {
        return displayName != null && !displayName.trim().isEmpty();
    }
}
