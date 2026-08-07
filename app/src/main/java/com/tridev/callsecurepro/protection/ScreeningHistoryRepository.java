package com.tridev.callsecurepro.protection;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.protection.ScreeningEventDao;
import com.tridev.callsecurepro.data.protection.ScreeningEventEntity;

import java.util.List;

public final class ScreeningHistoryRepository {

    public static final String ACTION_ALLOWED = "ALLOWED";
    public static final String ACTION_SILENCED = "SILENCED";
    public static final String ACTION_BLOCKED = "BLOCKED";

    private static final int MAX_HISTORY_ITEMS = 200;

    private final ScreeningEventDao dao;

    public ScreeningHistoryRepository(@NonNull Context context) {
        dao = CallSecureDatabase.getInstance(context.getApplicationContext()).screeningEventDao();
    }

    public void record(
            @NonNull String normalizedNumber,
            @NonNull String displayNumber,
            @NonNull String action,
            @NonNull String reason,
            @NonNull String riskLevel,
            int riskScore
    ) {
        dao.insert(new ScreeningEventEntity(
                normalizedNumber,
                displayNumber,
                action,
                reason,
                riskLevel,
                Math.max(0, Math.min(100, riskScore)),
                System.currentTimeMillis()
        ));
        dao.trimToLatest(MAX_HISTORY_ITEMS);
    }

    @NonNull
    public List<ScreeningEventEntity> recent(int limit) {
        return dao.recent(Math.max(1, Math.min(50, limit)));
    }

    @NonNull
    public Stats getStats() {
        return new Stats(
                dao.countByAction(ACTION_BLOCKED),
                dao.countByAction(ACTION_SILENCED),
                dao.countByAction(ACTION_ALLOWED)
        );
    }

    public void clear() {
        dao.clear();
    }

    public static final class Stats {
        public final int blocked;
        public final int silenced;
        public final int allowed;

        private Stats(int blocked, int silenced, int allowed) {
            this.blocked = blocked;
            this.silenced = silenced;
            this.allowed = allowed;
        }
    }
}
