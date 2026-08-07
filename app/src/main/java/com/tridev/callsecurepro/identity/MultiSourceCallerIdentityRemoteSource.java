package com.tridev.callsecurepro.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered, fail-open remote caller identity pipeline.
 *
 * Each provider is isolated: one provider timing out or throwing must never prevent later
 * providers or the repository's local fallback from running. Providers are evaluated by
 * descending priority and the first meaningful real identity wins.
 */
public final class MultiSourceCallerIdentityRemoteSource implements CallerIdentityRemoteSource {

    private final List<Entry> entries;

    private MultiSourceCallerIdentityRemoteSource(@NonNull List<Entry> entries) {
        this.entries = entries;
    }

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
        if (normalizedNumber.trim().isEmpty()) {
            return null;
        }

        for (Entry entry : entries) {
            try {
                RemoteIdentity identity = entry.source.lookup(normalizedNumber, mode);
                if (isMeaningful(identity)) {
                    return identity;
                }
            } catch (RuntimeException ignored) {
                // A provider failure must never break caller screening or manual lookup.
            }
        }
        return null;
    }

    private boolean isMeaningful(@Nullable RemoteIdentity identity) {
        if (identity == null || identity.displayName == null) {
            return false;
        }
        if (identity.displayName.trim().isEmpty() || identity.source.trim().isEmpty()) {
            return false;
        }
        return identity.expiresAt <= 0L || identity.expiresAt > System.currentTimeMillis();
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        @NonNull
        public Builder add(
                @NonNull String id,
                int priority,
                @NonNull CallerIdentityRemoteSource source
        ) {
            String safeId = id.trim();
            if (safeId.isEmpty()) {
                throw new IllegalArgumentException("Provider id is required");
            }
            if (!ids.add(safeId)) {
                throw new IllegalArgumentException("Duplicate provider id: " + safeId);
            }
            entries.add(new Entry(safeId, priority, source));
            return this;
        }

        @NonNull
        public MultiSourceCallerIdentityRemoteSource build() {
            List<Entry> sorted = new ArrayList<>(entries);
            Collections.sort(sorted, Comparator.comparingInt((Entry entry) -> entry.priority).reversed());
            return new MultiSourceCallerIdentityRemoteSource(Collections.unmodifiableList(sorted));
        }
    }

    private static final class Entry {
        @NonNull
        private final String id;
        private final int priority;
        @NonNull
        private final CallerIdentityRemoteSource source;

        private Entry(
                @NonNull String id,
                int priority,
                @NonNull CallerIdentityRemoteSource source
        ) {
            this.id = id;
            this.priority = priority;
            this.source = source;
        }
    }
}
