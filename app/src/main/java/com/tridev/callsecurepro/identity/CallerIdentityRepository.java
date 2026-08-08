package com.tridev.callsecurepro.identity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.callsecurepro.community.CommunityCallerIdentityRemoteSource;
import com.tridev.callsecurepro.community.CommunityNetworkProvider;
import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.identity.CallerIdentityDao;
import com.tridev.callsecurepro.data.identity.CallerIdentityEntity;
import com.tridev.callsecurepro.data.identity.LookupHistoryDao;
import com.tridev.callsecurepro.data.identity.LookupHistoryEntity;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;

import java.util.List;

/**
 * Offline-first caller identity resolver.
 *
 * Resolution priority:
 * 1) a real contact saved on the device,
 * 2) a still-valid cached identity from a real prior source,
 * 3) the ordered multi-source remote provider pipeline,
 * 4) unknown identity with the local protection assessment only.
 *
 * New cache entries use a canonical E.164 key whenever the number can be parsed. This means
 * national and international representations of the same number converge on one learned result.
 * Legacy normalized cache entries remain readable during migration.
 *
 * Short-lived SHA-256 negative caching suppresses repeated remote misses without storing a raw
 * phone number in preferences. This repository never invents a caller/business name.
 */
public final class CallerIdentityRepository {

    private static final int MAX_LOOKUP_HISTORY = 100;

    private final Context appContext;
    private final CallerIdentityDao identityDao;
    private final LookupHistoryDao historyDao;
    private final CallerIntelligenceEngine intelligenceEngine;
    private final CallerIdentityRemoteSource remoteSource;
    private final CallerIdentityLookupKey lookupKeyResolver;
    private final RemoteLookupMissCache remoteMissCache;

    public CallerIdentityRepository(@NonNull Context context) {
        this(context, createDefaultRemotePipeline(context));
    }

    @NonNull
    private static CallerIdentityRemoteSource createDefaultRemotePipeline(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        return new MultiSourceCallerIdentityRemoteSource.Builder()
                .add(
                        "firebase-community",
                        100,
                        new CommunityCallerIdentityRemoteSource(
                                CommunityNetworkProvider.get(appContext)
                        )
                )
                .build();
    }

    public CallerIdentityRepository(
            @NonNull Context context,
            @NonNull CallerIdentityRemoteSource remoteSource
    ) {
        appContext = context.getApplicationContext();
        CallSecureDatabase database = CallSecureDatabase.getInstance(appContext);
        identityDao = database.callerIdentityDao();
        historyDao = database.lookupHistoryDao();
        intelligenceEngine = new CallerIntelligenceEngine(appContext);
        lookupKeyResolver = new CallerIdentityLookupKey();
        remoteMissCache = new RemoteLookupMissCache(appContext);
        this.remoteSource = remoteSource;
    }

    /** User-initiated number lookup. This is recorded in Recent lookups. */
    @NonNull
    public CallerIdentityResult lookup(@NonNull String rawNumber) {
        return resolveInternal(
                rawNumber,
                true,
                CallerIdentityLookupMode.USER_INITIATED
        );
    }

    /**
     * Passive caller resolution for incoming/ongoing calls. This never adds an item to the
     * manual Recent lookups list and keeps the remote path latency-sensitive.
     */
    @NonNull
    public CallerIdentityResult resolveCaller(@NonNull String rawNumber) {
        return resolveInternal(
                rawNumber,
                false,
                CallerIdentityLookupMode.PASSIVE_CALL_SCREENING
        );
    }

    @NonNull
    private CallerIdentityResult resolveInternal(
            @NonNull String rawNumber,
            boolean recordLookupHistory,
            @NonNull CallerIdentityLookupMode lookupMode
    ) {
        CallerIdentityLookupKey.Key lookupKey = lookupKeyResolver.resolve(rawNumber);
        String canonicalNumber = lookupKey.canonical;

        long now = System.currentTimeMillis();
        identityDao.deleteExpired(now);
        CallerAssessment assessment = intelligenceEngine.assess(rawNumber);

        ContactIdentity contactIdentity = findContact(rawNumber);
        if (contactIdentity != null) {
            CallerIdentityResult result = new CallerIdentityResult(
                    canonicalNumber,
                    contactIdentity.number,
                    contactIdentity.name,
                    "Saved contact",
                    CallerIdentityResult.IdentityType.CONTACT,
                    CallerIdentityResult.VerificationLevel.LOCAL_MATCH,
                    "Device contacts",
                    100,
                    assessment
            );
            remoteMissCache.clear(canonicalNumber);
            recordHistoryIfNeeded(rawNumber, result, recordLookupHistory);
            return result;
        }

        CallerIdentityEntity cached = findCachedIdentity(lookupKey, now);
        if (cached != null) {
            CallerIdentityResult result = fromCachedEntity(cached, assessment);
            remoteMissCache.clear(canonicalNumber);
            recordHistoryIfNeeded(rawNumber, result, recordLookupHistory);
            return result;
        }

        boolean remoteSkipped = remoteMissCache.shouldSkipRemote(
                canonicalNumber,
                lookupMode,
                now
        );
        if (!remoteSkipped) {
            CallerIdentityRemoteSource.RemoteIdentity remoteIdentity = remoteSource.lookup(
                    canonicalNumber,
                    lookupMode
            );
            if (remoteIdentity != null && hasMeaningfulRemoteIdentity(remoteIdentity)) {
                CallerIdentityEntity entity = toEntity(canonicalNumber, remoteIdentity, now);
                identityDao.upsert(entity);
                remoteMissCache.clear(canonicalNumber);
                CallerIdentityResult result = fromCachedEntity(entity, assessment);
                recordHistoryIfNeeded(rawNumber, result, recordLookupHistory);
                return result;
            }
            remoteMissCache.recordMiss(canonicalNumber, now);
        }

        CallerIdentityResult result = new CallerIdentityResult(
                canonicalNumber,
                rawNumber.trim().isEmpty() ? canonicalNumber : rawNumber.trim(),
                null,
                null,
                CallerIdentityResult.IdentityType.UNKNOWN,
                CallerIdentityResult.VerificationLevel.UNVERIFIED,
                remoteSkipped ? "Recent remote miss cache" : "Local intelligence",
                0,
                assessment
        );
        recordHistoryIfNeeded(rawNumber, result, recordLookupHistory);
        return result;
    }

    @Nullable
    private CallerIdentityEntity findCachedIdentity(
            @NonNull CallerIdentityLookupKey.Key lookupKey,
            long now
    ) {
        CallerIdentityEntity cached = identityDao.findByNumber(lookupKey.canonical);
        if (isUsableCache(cached, now)) {
            return cached;
        }

        if (lookupKey.hasDistinctLegacyAlias()) {
            CallerIdentityEntity legacy = identityDao.findByNumber(lookupKey.legacy);
            if (isUsableCache(legacy, now)) {
                return legacy;
            }
        }
        return null;
    }

    private boolean isUsableCache(@Nullable CallerIdentityEntity entity, long now) {
        return entity != null && (entity.expiresAt <= 0L || entity.expiresAt >= now);
    }

    @NonNull
    public List<LookupHistoryEntity> getRecentHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_LOOKUP_HISTORY, limit));
        return historyDao.getRecent(safeLimit);
    }

    public void clearLookupHistory() {
        historyDao.clearAll();
    }

    @Nullable
    private ContactIdentity findContact(@NonNull String number) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );

        String[] projection = new String[]{
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
        };

        try (Cursor cursor = appContext.getContentResolver().query(
                lookupUri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);
            if (nameIndex < 0) {
                return null;
            }

            String name = cursor.getString(nameIndex);
            String contactNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : number;
            if (name == null || name.trim().isEmpty()) {
                return null;
            }

            return new ContactIdentity(
                    name.trim(),
                    contactNumber == null || contactNumber.trim().isEmpty()
                            ? number
                            : contactNumber.trim()
            );
        } catch (SecurityException ignored) {
            return null;
        }
    }

    @NonNull
    private CallerIdentityResult fromCachedEntity(
            @NonNull CallerIdentityEntity entity,
            @NonNull CallerAssessment assessment
    ) {
        String resolvedName = firstNonBlank(entity.businessName, entity.displayName);
        return new CallerIdentityResult(
                entity.normalizedNumber,
                entity.displayNumber,
                resolvedName,
                entity.category,
                parseIdentityType(entity.identityType),
                parseVerificationLevel(entity.verificationLevel),
                entity.source,
                entity.confidence,
                assessment
        );
    }

    @NonNull
    private CallerIdentityEntity toEntity(
            @NonNull String canonicalNumber,
            @NonNull CallerIdentityRemoteSource.RemoteIdentity remoteIdentity,
            long now
    ) {
        String businessName = remoteIdentity.identityType == CallerIdentityResult.IdentityType.BUSINESS
                ? remoteIdentity.displayName
                : null;
        String personName = remoteIdentity.identityType == CallerIdentityResult.IdentityType.BUSINESS
                ? null
                : remoteIdentity.displayName;

        return new CallerIdentityEntity(
                canonicalNumber,
                remoteIdentity.displayNumber.trim().isEmpty()
                        ? canonicalNumber
                        : remoteIdentity.displayNumber.trim(),
                personName,
                businessName,
                remoteIdentity.category,
                remoteIdentity.identityType.name(),
                remoteIdentity.verificationLevel.name(),
                remoteIdentity.source,
                Math.max(0, Math.min(100, remoteIdentity.confidence)),
                now,
                remoteIdentity.expiresAt
        );
    }

    private boolean hasMeaningfulRemoteIdentity(
            @NonNull CallerIdentityRemoteSource.RemoteIdentity identity
    ) {
        return identity.displayName != null
                && !identity.displayName.trim().isEmpty()
                && !identity.source.trim().isEmpty();
    }

    private void recordHistoryIfNeeded(
            @NonNull String queryNumber,
            @NonNull CallerIdentityResult result,
            boolean recordLookupHistory
    ) {
        if (!recordLookupHistory) {
            return;
        }
        historyDao.insert(new LookupHistoryEntity(
                result.getNormalizedNumber(),
                queryNumber.trim().isEmpty() ? result.getDisplayNumber() : queryNumber.trim(),
                result.getDisplayName(),
                result.getIdentityType().name(),
                result.getSource(),
                result.getAssessment().getLevel().name(),
                System.currentTimeMillis()
        ));
        historyDao.trimToLatest(MAX_LOOKUP_HISTORY);
    }

    @NonNull
    private CallerIdentityResult.IdentityType parseIdentityType(@Nullable String value) {
        if (value == null) {
            return CallerIdentityResult.IdentityType.UNKNOWN;
        }
        try {
            return CallerIdentityResult.IdentityType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CallerIdentityResult.IdentityType.UNKNOWN;
        }
    }

    @NonNull
    private CallerIdentityResult.VerificationLevel parseVerificationLevel(@Nullable String value) {
        if (value == null) {
            return CallerIdentityResult.VerificationLevel.UNVERIFIED;
        }
        try {
            return CallerIdentityResult.VerificationLevel.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CallerIdentityResult.VerificationLevel.UNVERIFIED;
        }
    }

    @Nullable
    private String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private static final class ContactIdentity {
        @NonNull
        private final String name;
        @NonNull
        private final String number;

        private ContactIdentity(@NonNull String name, @NonNull String number) {
            this.name = name;
            this.number = number;
        }
    }
}
