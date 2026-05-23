package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.db.TrailMateDatabase
import com.example.data.local.db.SessionEntity
import com.example.domain.model.ActiveSession
import com.example.domain.model.Waypoint
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SaveSessionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionJson = inputData.getString(KEY_SESSION_JSON)
            ?: return Result.failure()

        return try {
            val session = sessionAdapter.fromJson(sessionJson)
                ?: return Result.failure()

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
            val dao = TrailMateDatabase.getInstance(applicationContext).sessionDao()
            dao.insertSession(entity)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SESSION_JSON = "session_json"
        private const val MAX_RETRIES = 3

        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val sessionAdapter = moshi.adapter(ActiveSession::class.java)
    }
}
