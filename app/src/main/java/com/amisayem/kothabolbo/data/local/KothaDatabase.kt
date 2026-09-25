package com.amisayem.kothabolbo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RestoredMessageEntity::class,
        MediaDeleteEntity::class,
        ChatCacheEntity::class,
        ProfileCacheEntity::class,
        DraftEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KothaDatabase : RoomDatabase() {
    abstract fun dao(): KothaDao

    companion object {
        @Volatile private var instance: KothaDatabase? = null

        fun get(context: Context): KothaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KothaDatabase::class.java,
                "kotha.db"
            ).fallbackToDestructiveMigration(false).build().also { instance = it }
        }
    }
}
