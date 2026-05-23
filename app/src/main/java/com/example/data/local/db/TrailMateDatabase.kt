package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RouteEntity::class,
        SessionEntity::class,
        NodeEntity::class,
        WayEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrailMateDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun sessionDao(): SessionDao
    abstract fun osmCacheDao(): OsmCacheDao

    companion object {
        @Volatile
        private var instance: TrailMateDatabase? = null

        fun getInstance(context: Context): TrailMateDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrailMateDatabase::class.java,
                    "trailmate_database"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
