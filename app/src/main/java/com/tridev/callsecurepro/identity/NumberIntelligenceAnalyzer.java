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

        String defaultRegion = Locale.getDefault().getCountry();
        if (defaultRegion == null || defaultRegion.trim().isEmpty()) {
            defaultRegion = UNKNOWN_REGION_CODE;
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(input, defaultRegion);
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
