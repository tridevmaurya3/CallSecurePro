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
        mutate(number, blocked, false, null, false);
    }

    public void setTrusted(@NonNull String number, boolean trusted) {
        mutate(number, false, trusted, null, false);
    }

    public void addSpamReport(@NonNull String number) {
        mutate(number, false, false, null, true);
    }

    public void setCustomLabel(@NonNull String number, @Nullable String label) {
        mutate(number, false, false, label, false);
    }

    @NonNull
    public Stats getStats() {
        return new Stats(
                ruleDao.getBlockedCount(),
                ruleDao.getTrustedCount(),
                ruleDao.getTotalReportCount()
        );
    }

    private void mutate(
            @NonNull String number,
            boolean setBlocked,
            boolean setTrusted,
            @Nullable String customLabel,
            boolean incrementReport
    ) {
        String normalized = normalize(number);
        if (normalized.isEmpty()) {
            return;
        }

        ProtectionRuleEntity current = ruleDao.findByNumber(normalized);
        String display = number.trim().isEmpty() ? normalized : number.trim();

        boolean blocked = current != null && current.userBlocked;
        boolean trusted = current != null && current.trusted;
        int reports = current == null ? 0 : current.spamReports;
        String label = current == null ? null : current.customLabel;

        if (setBlocked) {
            blocked = true;
            trusted = false;
        } else if (setTrusted) {
            trusted = true;
            blocked = false;
        } else if (!incrementReport && customLabel == null) {
            // Explicit false call from setBlocked/setTrusted clears that state.
            if (current != null) {
                if (current.userBlocked) {
                    blocked = false;
                } else if (current.trusted) {
                    trusted = false;
                }
            }
        }

        if (incrementReport) {
            reports = Math.min(9999, reports + 1);
        }

        if (customLabel != null) {
            String trimmed = customLabel.trim();
            label = trimmed.isEmpty() ? null : trimmed;
        }

        ruleDao.upsert(new ProtectionRuleEntity(
                normalized,
                display,
                blocked,
                trusted,
                reports,
                label,
                System.currentTimeMillis()
        ));
    }
}
