package com.tridev.callsecurepro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.tridev.callsecurepro.data.identity.CallerIdentityDao;
import com.tridev.callsecurepro.data.identity.CallerIdentityEntity;
import com.tridev.callsecurepro.data.identity.LookupHistoryDao;
import com.tridev.callsecurepro.data.identity.LookupHistoryEntity;
import com.tridev.callsecurepro.data.protection.ProtectionRuleDao;
import com.tridev.callsecurepro.data.protection.ProtectionRuleEntity;

@Database(
        entities = {
                ProtectionRuleEntity.class,
                CallerIdentityEntity.class,
                LookupHistoryEntity.class
        },
        version = 2,
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
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ProtectionRuleDao protectionRuleDao();

    public abstract CallerIdentityDao callerIdentityDao();

    public abstract LookupHistoryDao lookupHistoryDao();
}
