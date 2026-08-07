package com.tridev.callsecurepro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.tridev.callsecurepro.data.protection.ProtectionRuleDao;
import com.tridev.callsecurepro.data.protection.ProtectionRuleEntity;

@Database(
        entities = {ProtectionRuleEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class CallSecureDatabase extends RoomDatabase {

    private static volatile CallSecureDatabase INSTANCE;

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
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ProtectionRuleDao protectionRuleDao();
}
