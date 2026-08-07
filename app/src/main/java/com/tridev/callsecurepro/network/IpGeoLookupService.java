package com.tridev.callsecurepro.network;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Explicit, user-triggered approximate public-IP lookup.
 *
 * The caller must use this only for addresses already classified PUBLIC by
 * {@link IpIntelligenceAnalyzer}. The response intentionally exposes only broad region/network
 * fields; city, coordinates and postal information are not returned to the UI.
 */
public final class IpGeoLookupService {

    private static final String ENDPOINT = "https://ipwho.is/";
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_RESPONSE_CHARS = 65536;

    public static final class Result {
        @NonNull
        public final String country;
        @NonNull
        public final String countryCode;
        @NonNull
        public final String region;
        @NonNull
        public final String timezone;
        @NonNull
        public final String asn;
        @NonNull
        public final String organization;
        @NonNull
        public final String isp;

        private Result(
                @NonNull String country,
                @NonNull String countryCode,
                @NonNull String region,
                @NonNull String timezone,
                @NonNull String asn,
                @NonNull String organization,
                @NonNull String isp
        ) {
            this.country = country;
            this.countryCode = countryCode;
            this.region = region;
            this.timezone = timezone;
            this.asn = asn;
            this.organization = organization;
            this.isp = isp;
        }
    }

    @NonNull
    public Result lookup(@NonNull String publicIp) throws IOException, JSONException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ENDPOINT + publicIp);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CallSecurePro/1.0 Android");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readBody(stream);
            if (body.isEmpty()) {
                throw new IOException("Empty IP lookup response");
            }

            JSONObject json = new JSONObject(body);
            if (!json.optBoolean("success", false)) {
                String message = json.optString("message", "IP lookup failed");
                throw new IOException(message);
            }

            JSONObject connectionJson = json.optJSONObject("connection");
            JSONObject timezoneJson = json.optJSONObject("timezone");

            String asn = "";
            String organization = "";
            String isp = "";
            if (connectionJson != null) {
                long asnNumber = connectionJson.optLong("asn", 0L);
                if (asnNumber > 0L) {
                    asn = "AS" + asnNumber;
                }
                organization = safe(connectionJson.optString("org", ""));
                isp = safe(connectionJson.optString("isp", ""));
            }

            String timezone = timezoneJson == null
                    ? ""
                    : safe(timezoneJson.optString("id", ""));

            return new Result(
                    safe(json.optString("country", "")),
                    safe(json.optString("country_code", "")),
                    safe(json.optString("region", "")),
                    timezone,
                    asn,
                    organization,
                    isp
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                int remaining = MAX_RESPONSE_CHARS - builder.length();
                if (remaining <= 0) {
                    break;
                }
                builder.append(buffer, 0, Math.min(read, remaining));
            }
        }
        return builder.toString();
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
