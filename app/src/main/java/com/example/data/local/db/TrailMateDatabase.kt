package com.example.data.local.db

import androidx.room.Database
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
}
