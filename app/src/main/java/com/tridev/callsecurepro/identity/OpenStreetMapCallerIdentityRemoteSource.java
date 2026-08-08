package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Key-free, user-initiated exact phone-tag lookup against OpenStreetMap public data.
 *
 * Only named map objects with an equivalent published phone tag are accepted. OSM is
 * community-maintained, so matches are always labelled UNVERIFIED and cached locally only.
 */
public final class OpenStreetMapCallerIdentityRemoteSource implements CallerIdentityRemoteSource {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final int CONNECT_TIMEOUT_MILLIS = 2200;
    private static final int READ_TIMEOUT_MILLIS = 3000;
    private static final long CACHE_TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L;
    private static final String[] PHONE_KEYS = {
            "phone",
            "contact:phone",
            "mobile",
            "contact:mobile"
    };

    @Nullable
    @Override
    public RemoteIdentity lookup(@NonNull String normalizedNumber) {
        return lookup(normalizedNumber, CallerIdentityLookupMode.USER_INITIATED);
    }

    @Nullable
    @Override
    public RemoteIdentity lookup(
            @NonNull String normalizedNumber,
            @NonNull CallerIdentityLookupMode mode
    ) {
        if (!mode.areSlowRemoteProvidersAllowed() || normalizedNumber.trim().isEmpty()) {
            return null;
        }

        try {
            String query = buildQuery(
                    DynamicPhoneLookupUtils.buildLookupVariants(normalizedNumber)
            );
            if (query == null) {
                return null;
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL(ENDPOINT + "?data=" + encoded);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CallSecurePro/1.0 Android exact-phone lookup");

            try {
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    return null;
                }
                String body = readAll(connection.getInputStream());
                return parseResponse(body, normalizedNumber);
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String buildQuery(@NonNull List<String> variants) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String variant : variants) {
            if (!variant.trim().isEmpty()) {
                candidates.add(variant.trim());
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[out:json][timeout:4];(");
        int clauses = 0;
        for (String value : candidates) {
            String escaped = escapeOverpass(value);
            for (String key : PHONE_KEYS) {
                builder.append("nwr[\"")
                        .append(key)
                        .append("\"=\"")
                        .append(escaped)
                        .append("\"];");
                clauses++;
                if (clauses >= 48) {
                    break;
                }
            }
            if (clauses >= 48) {
                break;
            }
        }
        builder.append(");out tags 8;");
        return builder.toString();
    }

    @Nullable
    private RemoteIdentity parseResponse(
            @NonNull String body,
            @NonNull String normalizedNumber
    ) {
        final JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException ignored) {
            return null;
        }

        JSONArray elements = root.optJSONArray("elements");
        if (elements == null) {
            return null;
        }

        for (int index = 0; index < elements.length(); index++) {
            JSONObject element = elements.optJSONObject(index);
            JSONObject tags = element == null ? null : element.optJSONObject("tags");
            if (tags == null) {
                continue;
            }

            String name = firstNonBlank(
                    tags.optString("name", ""),
                    tags.optString("name:en", ""),
                    tags.optString("brand", ""),
                    tags.optString("operator", "")
            );
            if (name.isEmpty()) {
                continue;
            }

            String matchedPhone = findMatchedPhone(tags, normalizedNumber);
            if (matchedPhone.isEmpty()) {
                continue;
            }

            return new RemoteIdentity(
                    matchedPhone,
                    name,
                    category(tags),
                    CallerIdentityResult.IdentityType.BUSINESS,
                    CallerIdentityResult.VerificationLevel.UNVERIFIED,
                    "OpenStreetMap exact phone match",
                    66,
                    System.currentTimeMillis() + CACHE_TTL_MILLIS
            );
        }
        return null;
    }

    @NonNull
    private String findMatchedPhone(
            @NonNull JSONObject tags,
            @NonNull String normalizedNumber
    ) {
        for (String key : PHONE_KEYS) {
            String value = tags.optString(key, "");
            if (DynamicPhoneLookupUtils.containsEquivalentPhone(value, normalizedNumber)) {
                return value.trim();
            }
        }
        return "";
    }

    @NonNull
    private String category(@NonNull JSONObject tags) {
        String value = firstNonBlank(
                tags.optString("amenity", ""),
                tags.optString("healthcare", ""),
                tags.optString("shop", ""),
                tags.optString("office", ""),
                tags.optString("tourism", ""),
                tags.optString("craft", "")
        );
        if (value.isEmpty()) {
            return "Public/business directory";
        }
        return value.replace('_', ' ');
    }

    @NonNull
    private String firstNonBlank(@Nullable String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    @NonNull
    private String readAll(@NonNull InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                if (builder.length() > 512_000) {
                    throw new IllegalStateException("Public lookup response too large");
                }
            }
        }
        return builder.toString();
    }

    @NonNull
    private String escapeOverpass(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
