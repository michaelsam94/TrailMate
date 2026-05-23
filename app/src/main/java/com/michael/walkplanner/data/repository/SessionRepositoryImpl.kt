package com.michael.walkplanner.data.repository

import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.model.Waypoint
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.repository.SessionRepository
import com.michael.walkplanner.data.local.db.SessionDao
import com.michael.walkplanner.data.local.db.SessionEntity
import com.michael.walkplanner.core.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun saveSession(session: ActiveSession): Result<Unit, DomainError> {
        return try {
            val entity = SessionEntity(
                sessionId = session.sessionId,
                routeId = session.routeId,
                startTime = session.startTime,
                endTime = session.endTime ?: System.currentTimeMillis(),
                currentPaceMinPerKm = session.currentPaceMinPerKm,
                distanceCoveredKm = session.distanceCoveredKm,
                elevationGainM = session.elevationGainM,
                caloriesBurned = session.caloriesBurned,
                isPaused = session.isPaused,
                liveWaypoints = session.liveWaypoints
            )
            sessionDao.insertSession(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(DomainError.Unknown(e))
        }
    }

    override fun getSessionHistory(): Flow<Result<List<ActiveSession>, DomainError>> {
        return sessionDao.getAllSessions()
            .map { entities ->
                val sessions = entities.map { entity ->
                    ActiveSession(
                        sessionId = entity.sessionId,
                        routeId = entity.routeId,
                        startTime = entity.startTime,
                        endTime = entity.endTime,
                        liveWaypoints = entity.liveWaypoints,
                        currentPaceMinPerKm = entity.currentPaceMinPerKm,
                        distanceCoveredKm = entity.distanceCoveredKm,
                        elevationGainM = entity.elevationGainM,
                        caloriesBurned = entity.caloriesBurned,
                        isPaused = entity.isPaused
                    )
                }
                Result.Success(sessions) as Result<List<ActiveSession>, DomainError>
            }
            .catch { e ->
                emit(Result.Failure(DomainError.Unknown(e)))
            }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit, DomainError> {
        return try {
            sessionDao.deleteSession(sessionId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(DomainError.Unknown(e))
        }
    }
}
