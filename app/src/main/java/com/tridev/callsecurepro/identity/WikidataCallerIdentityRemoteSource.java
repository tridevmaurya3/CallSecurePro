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
 * Key-free, user-initiated exact phone-number lookup against Wikidata public data.
 *
 * The query explicitly excludes human entities. Results are therefore treated only as
 * public/business/service identity hints and are always marked UNVERIFIED.
 */
public final class WikidataCallerIdentityRemoteSource implements CallerIdentityRemoteSource {

    private static final String ENDPOINT = "https://query.wikidata.org/sparql";
    private static final int CONNECT_TIMEOUT_MILLIS = 2200;
    private static final int READ_TIMEOUT_MILLIS = 2600;
    private static final long CACHE_TTL_MILLIS = 30L * 24L * 60L * 60L * 1000L;

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
            List<String> variants = DynamicPhoneLookupUtils.buildLookupVariants(normalizedNumber);
            String query = buildQuery(variants);
            if (query == null) {
                return null;
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL(ENDPOINT + "?format=json&query=" + encoded);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/sparql-results+json, application/json");
            connection.setRequestProperty("User-Agent", "CallSecurePro/1.0 Android public-directory lookup");

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
        Set<String> compact = new LinkedHashSet<>();
        for (String variant : variants) {
            String value = DynamicPhoneLookupUtils.compactPublicPhone(variant);
            if (!value.isEmpty()) {
                compact.add(value);
            }
        }
        if (compact.isEmpty()) {
            return null;
        }

        StringBuilder values = new StringBuilder();
        for (String value : compact) {
            values.append('"').append(escapeSparql(value)).append("\" ");
        }

        return "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n"
                + "PREFIX wd: <http://www.wikidata.org/entity/>\n"
                + "PREFIX wikibase: <http://wikiba.se/ontology#>\n"
                + "PREFIX bd: <http://www.bigdata.com/rdf#>\n"
                + "SELECT ?item ?itemLabel ?phone WHERE {\n"
                + "  ?item wdt:P1329 ?phone .\n"
                + "  FILTER NOT EXISTS { ?item wdt:P31 wd:Q5 . }\n"
                + "  BIND(REPLACE(STR(?phone), \"[^0-9+]\", \"\") AS ?compactPhone)\n"
                + "  VALUES ?wanted { " + values + "}\n"
                + "  FILTER(?compactPhone = ?wanted)\n"
                + "  SERVICE wikibase:label { bd:serviceParam wikibase:language \"en,hi\". }\n"
                + "}\nLIMIT 3";
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

        JSONObject results = root.optJSONObject("results");
        if (results == null) {
            return null;
        }
        JSONArray bindings = results.optJSONArray("bindings");
        if (bindings == null) {
            return null;
        }

        for (int index = 0; index < bindings.length(); index++) {
            JSONObject binding = bindings.optJSONObject(index);
            if (binding == null) {
                continue;
            }
            String label = value(binding.optJSONObject("itemLabel"));
            String publishedPhone = value(binding.optJSONObject("phone"));
            if (label.isEmpty() || label.matches("Q\\d+")) {
                continue;
            }
            if (!DynamicPhoneLookupUtils.containsEquivalentPhone(
                    publishedPhone,
                    normalizedNumber
            )) {
                continue;
            }

            return new RemoteIdentity(
                    publishedPhone.isEmpty() ? normalizedNumber : publishedPhone,
                    label,
                    "Public/business directory",
                    CallerIdentityResult.IdentityType.BUSINESS,
                    CallerIdentityResult.VerificationLevel.UNVERIFIED,
                    "Wikidata exact phone match",
                    72,
                    System.currentTimeMillis() + CACHE_TTL_MILLIS
            );
        }
        return null;
    }

    @NonNull
    private String value(@Nullable JSONObject object) {
        if (object == null) {
            return "";
        }
        String value = object.optString("value", "");
        return value == null ? "" : value.trim();
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
    private String escapeSparql(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
