package com.tridev.callsecurepro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.tridev.callsecurepro.data.calls.CallNoteDao;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;
import com.tridev.callsecurepro.data.identity.CallerIdentityDao;
import com.tridev.callsecurepro.data.identity.CallerIdentityEntity;
import com.tridev.callsecurepro.data.identity.LookupHistoryDao;
import com.tridev.callsecurepro.data.identity.LookupHistoryEntity;
import com.tridev.callsecurepro.data.protection.ProtectionRuleDao;
import com.tridev.callsecurepro.data.protection.ProtectionRuleEntity;
import com.tridev.callsecurepro.data.protection.ScreeningEventDao;
import com.tridev.callsecurepro.data.protection.ScreeningEventEntity;

@Database(
        entities = {
                ProtectionRuleEntity.class,
                CallerIdentityEntity.class,
                LookupHistoryEntity.class,
                CallNoteEntity.class,
                ScreeningEventEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class CallSecureDatabase extends RoomDatabase {

    private static volatile CallSecureDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caller_identities` (" +
                            "`normalizedNumber` TEXT NOT NULL, " +
                            "`displayNumber` TEXT NOT NULL, " +
                            "`displayName` TEXT, " +
                            "`businessName` TEXT, " +
                            "`category` TEXT, " +
                            "`identityType` TEXT NOT NULL, " +
                            "`verificationLevel` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`confidence` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "`expiresAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`normalizedNumber`))"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lookup_history` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`normalizedNumber` TEXT NOT NULL, " +
                            "`queryNumber` TEXT NOT NULL, " +
                            "`resolvedName` TEXT, " +
                            "`identityType` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`riskLevel` TEXT NOT NULL, " +
                            "`lookedUpAt` INTEGER NOT NULL)"
            );

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lookup_history_normalizedNumber` " +
                            "ON `lookup_history` (`normalizedNumber`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lookup_history_lookedUpAt` " +
                            "ON `lookup_history` (`lookedUpAt`)"
            );
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_notes` (" +
                            "`callLogId` INTEGER NOT NULL, " +
                            "`normalizedNumber` TEXT NOT NULL, " +
                            "`callTimestamp` INTEGER NOT NULL, " +
                            "`noteText` TEXT NOT NULL, " +
                            "`followUpAt` INTEGER NOT NULL, " +
                            "`followUpDone` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`callLogId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_call_notes_normalizedNumber` " +
                            "ON `call_notes` (`normalizedNumber`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_call_notes_followUpAt` " +
                            "ON `call_notes` (`followUpAt`)"
            );
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `screening_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`normalizedNumber` TEXT NOT NULL, " +
                            "`displayNumber` TEXT NOT NULL, " +
                            "`action` TEXT NOT NULL, " +
                            "`reason` TEXT NOT NULL, " +
                            "`riskLevel` TEXT NOT NULL, " +
                            "`riskScore` INTEGER NOT NULL, " +
                            "`screenedAt` INTEGER NOT NULL)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_screening_events_screenedAt` " +
                            "ON `screening_events` (`screenedAt`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_screening_events_action` " +
                            "ON `screening_events` (`action`)"
            );
        }
    };

    @NonNull
    public static CallSecureDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (CallSecureDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    CallSecureDatabase.class,
                                    "call_secure_pro.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ProtectionRuleDao protectionRuleDao();

    public abstract CallerIdentityDao callerIdentityDao();

    public abstract LookupHistoryDao lookupHistoryDao();

    public abstract CallNoteDao callNoteDao();

    public abstract ScreeningEventDao screeningEventDao();
}
