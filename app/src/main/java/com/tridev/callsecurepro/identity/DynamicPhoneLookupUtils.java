package com.tridev.callsecurepro.identity;

import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared normalization helpers for dynamic public/business phone lookups. */
final class DynamicPhoneLookupUtils {

    private DynamicPhoneLookupUtils() {
    }

    @NonNull
    static List<String> buildLookupVariants(@NonNull String input) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, input);

        NumberIntelligenceAnalyzer.Result info = new NumberIntelligenceAnalyzer().analyze(input);
        if (info.isParsed()) {
            add(values, info.getE164Format());
            add(values, info.getInternationalFormat());
            add(values, info.getNationalFormat());
        }

        String normalized = PhoneNumberUtils.normalizeNumber(input);
        add(values, normalized);
        if (!normalized.isEmpty() && normalized.charAt(0) != '+') {
            add(values, "+" + normalized);
        }

        String digits = digitsOnly(normalized);
        add(values, digits);
        if (digits.startsWith("91") && digits.length() == 12) {
            String national = digits.substring(2);
            add(values, national);
            add(values, "0" + national);
            add(values, "+91" + national);
        }

        return new ArrayList<>(values);
    }

    @NonNull
    static String canonicalComparable(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        NumberIntelligenceAnalyzer.Result info = new NumberIntelligenceAnalyzer().analyze(value);
        if (info.isParsed() && !info.getE164Format().trim().isEmpty()) {
            return digitsOnly(info.getE164Format());
        }
        return digitsOnly(PhoneNumberUtils.normalizeNumber(value));
    }

    static boolean containsEquivalentPhone(
            @Nullable String publishedValue,
            @NonNull String requestedNumber
    ) {
        if (publishedValue == null || publishedValue.trim().isEmpty()) {
            return false;
        }
        String requested = canonicalComparable(requestedNumber);
        if (requested.isEmpty()) {
            return false;
        }

        String[] parts = publishedValue.split("[;,|]");
        for (String part : parts) {
            String candidate = canonicalComparable(part);
            if (candidate.equals(requested)) {
                return true;
            }
        }
        return canonicalComparable(publishedValue).equals(requested);
    }

    @NonNull
    static String compactPublicPhone(@Nullable String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character >= '0' && character <= '9') || character == '+') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String digitsOnly(@Nullable String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static void add(@NonNull Set<String> values, @Nullable String value) {
        if (value == null) {
            return;
        }
        String clean = value.trim();
        if (!clean.isEmpty() && clean.length() <= 64) {
            values.add(clean);
        }
        String compact = compactPublicPhone(clean);
        if (!compact.isEmpty() && compact.length() <= 64) {
            values.add(compact);
        }
    }
}
