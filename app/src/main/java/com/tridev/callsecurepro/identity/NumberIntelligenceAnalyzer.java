package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

/**
 * Offline numbering-plan intelligence backed by libphonenumber metadata.
 *
 * This class does not locate a device or person. Region information describes the phone
 * numbering plan only and must never be presented as a caller's live/current location.
 */
public final class NumberIntelligenceAnalyzer {

    private static final String UNKNOWN_REGION_CODE = "ZZ";
    private static final String NON_GEO_REGION_CODE = "001";
    private static final String INDIA_REGION_CODE = "IN";

    public enum Validity {
        VALID,
        POSSIBLE,
        INVALID
    }

    public enum NumberType {
        MOBILE,
        FIXED_LINE,
        FIXED_OR_MOBILE,
        TOLL_FREE,
        PREMIUM_RATE,
        SHARED_COST,
        VOIP,
        PERSONAL,
        PAGER,
        UAN,
        VOICEMAIL,
        UNKNOWN
    }

    public static final class Result {
        @NonNull
        private final Validity validity;
        @NonNull
        private final NumberType numberType;
        @NonNull
        private final String regionCode;
        @NonNull
        private final String regionDisplayName;
        @NonNull
        private final String internationalFormat;
        @NonNull
        private final String nationalFormat;
        @NonNull
        private final String e164Format;
        private final int countryCallingCode;
        private final boolean parsed;

        private Result(
                @NonNull Validity validity,
                @NonNull NumberType numberType,
                @NonNull String regionCode,
                @NonNull String regionDisplayName,
                @NonNull String internationalFormat,
                @NonNull String nationalFormat,
                @NonNull String e164Format,
                int countryCallingCode,
                boolean parsed
        ) {
            this.validity = validity;
            this.numberType = numberType;
            this.regionCode = regionCode;
            this.regionDisplayName = regionDisplayName;
            this.internationalFormat = internationalFormat;
            this.nationalFormat = nationalFormat;
            this.e164Format = e164Format;
            this.countryCallingCode = countryCallingCode;
            this.parsed = parsed;
        }

        @NonNull
        public Validity getValidity() {
            return validity;
        }

        @NonNull
        public NumberType getNumberType() {
            return numberType;
        }

        @NonNull
        public String getRegionCode() {
            return regionCode;
        }

        @NonNull
        public String getRegionDisplayName() {
            return regionDisplayName;
        }

        @NonNull
        public String getInternationalFormat() {
            return internationalFormat;
        }

        @NonNull
        public String getNationalFormat() {
            return nationalFormat;
        }

        @NonNull
        public String getE164Format() {
            return e164Format;
        }

        public int getCountryCallingCode() {
            return countryCallingCode;
        }

        public boolean isParsed() {
            return parsed;
        }
    }

    private final PhoneNumberUtil phoneNumberUtil;

    public NumberIntelligenceAnalyzer() {
        phoneNumberUtil = PhoneNumberUtil.getInstance();
    }

    @NonNull
    public Result analyze(@Nullable String rawNumber) {
        String input = rawNumber == null ? "" : rawNumber.trim();
        if (input.isEmpty()) {
            return invalidResult();
        }

        String parseInput = normalizeInternationalPrefix(input);
        String defaultRegion = resolveDefaultRegion(parseInput);

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(parseInput, defaultRegion);
            boolean valid = phoneNumberUtil.isValidNumber(parsed);
            boolean possible = phoneNumberUtil.isPossibleNumber(parsed);

            Validity validity = valid
                    ? Validity.VALID
                    : possible
                    ? Validity.POSSIBLE
                    : Validity.INVALID;

            String regionCode = phoneNumberUtil.getRegionCodeForNumber(parsed);
            if (regionCode == null || regionCode.trim().isEmpty()) {
                regionCode = UNKNOWN_REGION_CODE;
            }

            String regionName = regionDisplayName(regionCode);
            String international = safeFormat(
                    parsed,
                    PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
            );
            String national = safeFormat(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            String e164 = safeFormat(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);

            return new Result(
                    validity,
                    mapType(phoneNumberUtil.getNumberType(parsed)),
                    regionCode,
                    regionName,
                    international,
                    national,
                    e164,
                    parsed.getCountryCode(),
                    true
            );
        } catch (NumberParseException | IllegalArgumentException ignored) {
            return invalidResult();
        }
    }

    /**
     * A phone number beginning with '+' already carries its own country code. For national input,
     * prefer India when the digits clearly match an Indian mobile pattern; otherwise retain the
     * device locale as the numbering-plan hint.
     */
    @NonNull
    private String resolveDefaultRegion(@NonNull String input) {
        if (input.startsWith("+")) {
            return UNKNOWN_REGION_CODE;
        }

        String digits = digitsOnly(input);
        if (isIndianNationalMobile(digits)) {
            return INDIA_REGION_CODE;
        }

        String localeRegion = Locale.getDefault().getCountry();
        if (localeRegion == null || localeRegion.trim().isEmpty()) {
            return UNKNOWN_REGION_CODE;
        }
        return localeRegion.trim().toUpperCase(Locale.US);
    }

    /** Treat a bare 91-prefixed Indian mobile as an international number even when '+' is omitted. */
    @NonNull
    private String normalizeInternationalPrefix(@NonNull String input) {
        if (input.startsWith("+")) {
            return input;
        }
        String digits = digitsOnly(input);
        if (digits.length() == 12
                && digits.startsWith("91")
                && isIndianNationalMobile(digits.substring(2))) {
            return "+" + digits;
        }
        return input;
    }

    private boolean isIndianNationalMobile(@NonNull String digits) {
        if (digits.length() == 10) {
            char first = digits.charAt(0);
            return first >= '6' && first <= '9';
        }
        if (digits.length() == 11 && digits.charAt(0) == '0') {
            char firstSubscriberDigit = digits.charAt(1);
            return firstSubscriberDigit >= '6' && firstSubscriberDigit <= '9';
        }
        return false;
    }

    @NonNull
    private String digitsOnly(@NonNull String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char value = input.charAt(i);
            if (value >= '0' && value <= '9') {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    @NonNull
    private String regionDisplayName(@NonNull String regionCode) {
        if (UNKNOWN_REGION_CODE.equals(regionCode)
                || NON_GEO_REGION_CODE.equals(regionCode)
                || regionCode.length() != 2) {
            return "";
        }
        try {
            return new Locale.Builder()
                    .setRegion(regionCode)
                    .build()
                    .getDisplayCountry(Locale.getDefault());
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @NonNull
    private String safeFormat(
            @NonNull Phonenumber.PhoneNumber number,
            @NonNull PhoneNumberUtil.PhoneNumberFormat format
    ) {
        try {
            String formatted = phoneNumberUtil.format(number, format);
            return formatted == null ? "" : formatted.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @NonNull
    private NumberType mapType(@NonNull PhoneNumberUtil.PhoneNumberType type) {
        switch (type) {
            case MOBILE:
                return NumberType.MOBILE;
            case FIXED_LINE:
                return NumberType.FIXED_LINE;
            case FIXED_LINE_OR_MOBILE:
                return NumberType.FIXED_OR_MOBILE;
            case TOLL_FREE:
                return NumberType.TOLL_FREE;
            case PREMIUM_RATE:
                return NumberType.PREMIUM_RATE;
            case SHARED_COST:
                return NumberType.SHARED_COST;
            case VOIP:
                return NumberType.VOIP;
            case PERSONAL_NUMBER:
                return NumberType.PERSONAL;
            case PAGER:
                return NumberType.PAGER;
            case UAN:
                return NumberType.UAN;
            case VOICEMAIL:
                return NumberType.VOICEMAIL;
            case UNKNOWN:
            default:
                return NumberType.UNKNOWN;
        }
    }

    @NonNull
    private Result invalidResult() {
        return new Result(
                Validity.INVALID,
                NumberType.UNKNOWN,
                UNKNOWN_REGION_CODE,
                "",
                "",
                "",
                "",
                0,
                false
        );
    }
}
