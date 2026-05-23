package com.michael.walkplanner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.michael.walkplanner.WalkPlannerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActiveRunNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> {
                ActiveRunTracker.togglePauseFromNotification()
                ActiveRunForegroundService.refreshNotification(context)
            }
            ACTION_STOP -> {
                val session = ActiveRunTracker.getCurrentSession() ?: return
                val pendingResult = goAsync()
                val app = context.applicationContext as WalkPlannerApplication
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        app.stopSessionUseCase(session)
                    } finally {
                        ActiveRunTracker.clear()
                        ActiveRunTracker.notifySessionEnded()
                        ActiveRunForegroundService.stop(context)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.michael.walkplanner.action.PAUSE_RUN"
        const val ACTION_STOP = "com.michael.walkplanner.action.STOP_RUN"
    }
}
