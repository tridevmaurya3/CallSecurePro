package com.tridev.callsecurepro.protection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.data.protection.ProtectionRuleEntity;

/**
 * Explainable, offline-first caller assessment.
 *
 * This engine deliberately avoids guessing that an unfamiliar number is spam. Strong actions
 * come only from explicit user rules or locally recorded reports. A later verified backend can
 * contribute additional evidence without replacing these local safeguards.
 */
public final class CallerIntelligenceEngine {

    private final Context appContext;
    private final ProtectionRepository repository;

    public CallerIntelligenceEngine(@NonNull Context context) {
        appContext = context.getApplicationContext();
        repository = new ProtectionRepository(appContext);
    }

    @NonNull
    public CallerAssessment assess(@Nullable String rawNumber) {
        String normalized = ProtectionRepository.normalize(rawNumber);
        if (normalized.isEmpty()) {
            return new CallerAssessment(
                    "",
                    CallerAssessment.Level.UNKNOWN,
                    35,
                    "Caller number is hidden or unavailable",
                    false,
                    false,
                    false,
                    0,
                    false,
                    false
            );
        }

        boolean savedContact = isSavedContact(rawNumber == null ? normalized : rawNumber);
        ProtectionRuleEntity rule = repository.getRule(normalized);
        boolean userBlocked = rule != null && rule.userBlocked;
        boolean trusted = rule != null && rule.trusted;
        int reports = rule == null ? 0 : Math.max(0, rule.spamReports);

        CallerAssessment.Level level;
        int score;
        String reason;

        if (userBlocked) {
            level = CallerAssessment.Level.SPAM;
            score = 100;
            reason = "Blocked by you on this device";
        } else if (trusted) {
            level = CallerAssessment.Level.SAFE;
            score = 0;
            reason = "Marked trusted on this device";
        } else if (savedContact) {
            level = CallerAssessment.Level.SAFE;
            score = 0;
            reason = "Saved in your contacts";
        } else if (reports >= 3) {
            level = CallerAssessment.Level.SPAM;
            score = Math.min(95, 75 + (reports * 5));
            reason = reports + " local spam reports on this device";
        } else if (reports == 2) {
            level = CallerAssessment.Level.SUSPICIOUS;
            score = 65;
            reason = "Reported twice as spam on this device";
        } else if (reports == 1) {
            level = CallerAssessment.Level.SUSPICIOUS;
            score = 50;
            reason = "Reported once as spam on this device";
        } else {
            level = CallerAssessment.Level.UNKNOWN;
            score = 20;
            reason = "No trusted or spam evidence yet";
        }

        boolean autoBlockHighRisk = ProtectionPreferences.isAutoBlockHighRiskEnabled(appContext);
        boolean silenceSuspicious = ProtectionPreferences.isSilenceSuspiciousEnabled(appContext);

        boolean shouldBlock = userBlocked
                || (!savedContact
                && autoBlockHighRisk
                && level == CallerAssessment.Level.SPAM);
        boolean shouldSilence = !shouldBlock
                && !savedContact
                && silenceSuspicious
                && (level == CallerAssessment.Level.SUSPICIOUS
                || level == CallerAssessment.Level.SPAM);

        return new CallerAssessment(
                normalized,
                level,
                score,
                reason,
                savedContact,
                userBlocked,
                trusted,
                reports,
                shouldBlock,
                shouldSilence
        );
    }

    private boolean isSavedContact(@NonNull String number) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );

        try (Cursor cursor = appContext.getContentResolver().query(
                lookupUri,
                new String[]{ContactsContract.PhoneLookup._ID},
                null,
                null,
                null
        )) {
            return cursor != null && cursor.moveToFirst();
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
