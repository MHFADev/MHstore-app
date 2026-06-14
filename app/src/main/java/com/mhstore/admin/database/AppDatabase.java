package com.mhstore.admin.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database singleton.
 * Version 2 — adds new order columns for the 4-service / 3-package system.
 *
 * To migrate manually run: fallbackToDestructiveMigration() is used here
 * since this is a fresh install. In production, add explicit @Migration.
 */
@Database(
    entities  = { OrderEntity.class },
    version   = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "mhstore_admin.db";

    public abstract OrderDao orderDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                        )
                        .fallbackToDestructiveMigration() // safe for fresh installs
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    /** Call only in tests or when user explicitly clears data. */
    public static void destroyInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) INSTANCE.close();
            INSTANCE = null;
        }
    }
}
