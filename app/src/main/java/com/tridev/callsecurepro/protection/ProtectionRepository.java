package com.tridev.callsecurepro.protection;

import android.content.Context;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.protection.ProtectionRuleDao;
import com.tridev.callsecurepro.data.protection.ProtectionRuleEntity;

/**
 * Device-local source of truth for manually trusted, blocked and reported numbers.
 * All methods are intended to run off the main thread.
 */
public final class ProtectionRepository {

    public static final class Stats {
        public final int blocked;
        public final int trusted;
        public final int reports;

        private Stats(int blocked, int trusted, int reports) {
            this.blocked = blocked;
            this.trusted = trusted;
            this.reports = reports;
        }
    }

    private final ProtectionRuleDao ruleDao;

    public ProtectionRepository(@NonNull Context context) {
        ruleDao = CallSecureDatabase.getInstance(context).protectionRuleDao();
    }

    @NonNull
    public static String normalize(@Nullable String number) {
        if (number == null) {
            return "";
        }
        String normalized = PhoneNumberUtils.normalizeNumber(number.trim());
        return normalized == null ? "" : normalized;
    }

    @Nullable
    public ProtectionRuleEntity getRule(@Nullable String number) {
        String normalized = normalize(number);
        if (normalized.isEmpty()) {
            return null;
        }
        return ruleDao.findByNumber(normalized);
    }

    public void setBlocked(@NonNull String number, boolean blocked) {
        ProtectionRuleEntity rule = getOrCreate(number);
        rule.userBlocked = blocked;
        if (blocked) {
            rule.trusted = false;
        }
        save(rule);
    }

    public void setTrusted(@NonNull String number, boolean trusted) {
        ProtectionRuleEntity rule = getOrCreate(number);
        rule.trusted = trusted;
        if (trusted) {
            rule.userBlocked = false;
        }
        save(rule);
    }

    public void addSpamReport(@NonNull String number) {
        ProtectionRuleEntity rule = getOrCreate(number);
        rule.spamReports = Math.min(9999, rule.spamReports + 1);
        save(rule);
    }

    public void setCustomLabel(@NonNull String number, @Nullable String label) {
        ProtectionRuleEntity rule = getOrCreate(number);
        if (label == null || label.trim().isEmpty()) {
            rule.customLabel = null;
        } else {
            rule.customLabel = label.trim();
        }
        save(rule);
    }

    @NonNull
    public Stats getStats() {
        return new Stats(
                ruleDao.getBlockedCount(),
                ruleDao.getTrustedCount(),
                ruleDao.getTotalReportCount()
        );
    }

    @NonNull
    private ProtectionRuleEntity getOrCreate(@NonNull String number) {
        String normalized = normalize(number);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A dialable phone number is required");
        }

        ProtectionRuleEntity current = ruleDao.findByNumber(normalized);
        if (current != null) {
            return current;
        }

        String display = number.trim().isEmpty() ? normalized : number.trim();
        return new ProtectionRuleEntity(
                normalized,
                display,
                false,
                false,
                0,
                null,
                System.currentTimeMillis()
        );
    }

    private void save(@NonNull ProtectionRuleEntity rule) {
        rule.updatedAt = System.currentTimeMillis();
        ruleDao.upsert(rule);
    }
}
