package com.michael.walkplanner.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.michael.walkplanner.core.Constants
import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.repository.SessionSaveScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

class WorkManagerSessionSaveScheduler(
    private val context: Context
) : SessionSaveScheduler {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val sessionAdapter = moshi.adapter(ActiveSession::class.java)

    override fun scheduleSave(session: ActiveSession) {
        val json = sessionAdapter.toJson(session)
        val request = OneTimeWorkRequestBuilder<SaveSessionWorker>()
            .setInputData(workDataOf(SaveSessionWorker.KEY_SESSION_JSON to json))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(Constants.WORK_TAG_SAVE_SESSION)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
