package com.michael.walkplanner.data.local.db

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
abstract class WalkPlannerDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun sessionDao(): SessionDao
    abstract fun osmCacheDao(): OsmCacheDao

    companion object {
        @Volatile
        private var instance: WalkPlannerDatabase? = null

        fun getInstance(context: Context): WalkPlannerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalkPlannerDatabase::class.java,
                    "walkplanner_database"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
