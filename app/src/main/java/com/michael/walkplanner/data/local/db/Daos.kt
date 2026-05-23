package com.michael.walkplanner.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRoutes(routes: List<RouteEntity>)

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRouteById(id: String): RouteEntity?

    @Query("DELETE FROM routes WHERE createdAt < :thresholdTime")
    suspend fun deleteOldRoutes(thresholdTime: Long)

    @Query("DELETE FROM routes")
    suspend fun clearAll()
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface OsmCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNodes(nodes: List<NodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWays(ways: List<WayEntity>)

    @Query("SELECT * FROM osm_nodes WHERE id = :id")
    suspend fun getNodeById(id: Long): NodeEntity?

    @Query("SELECT * FROM osm_nodes")
    suspend fun getAllNodes(): List<NodeEntity>

    @Query("SELECT * FROM osm_ways")
    suspend fun getAllWays(): List<WayEntity>

    @Query("DELETE FROM osm_nodes WHERE insertedAt < :thresholdTime")
    suspend fun deleteOldNodes(thresholdTime: Long)

    @Query("DELETE FROM osm_ways WHERE insertedAt < :thresholdTime")
    suspend fun deleteOldWays(thresholdTime: Long)
}
