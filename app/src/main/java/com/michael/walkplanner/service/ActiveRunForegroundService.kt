package com.michael.walkplanner.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.michael.walkplanner.MainActivity
import com.michael.walkplanner.R
import com.michael.walkplanner.WalkPlannerApplication
import com.michael.walkplanner.core.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ActiveRunForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var updateJob: Job? = null
    private var sessionId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as WalkPlannerApplication
        app.notificationHelper.ensureChannelCreated()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> refreshForegroundNotification()
            else -> {
                sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                acquireWakeLock()
                startForeground(Constants.NOTIFICATION_ID_ACTIVE_RUN, buildNotification())
                startNotificationUpdates()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun refreshForegroundNotification() {
        val stats = ActiveRunTracker.stats.value
        val notification = buildNotification(stats)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(Constants.NOTIFICATION_ID_ACTIVE_RUN, notification)
        if (stats?.isPaused == true) {
            releaseWakeLock()
        } else {
            acquireWakeLock()
        }
    }

    private fun startNotificationUpdates() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val stats = ActiveRunTracker.stats.value
                if (stats != null) {
                    refreshForegroundNotification()
                }
                delay(2_000)
            }
        }
    }

    private fun buildNotification(stats: ActiveRunStats? = ActiveRunTracker.stats.value): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("${Constants.DEEP_LINK_BASE}/${sessionId.ifEmpty { stats?.sessionId ?: "" }}")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_PAUSE,
            Intent(this, ActiveRunNotificationReceiver::class.java).apply {
                action = ActiveRunNotificationReceiver.ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_STOP,
            Intent(this, ActiveRunNotificationReceiver::class.java).apply {
                action = ActiveRunNotificationReceiver.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val paused = stats?.isPaused == true
        val title = if (paused) getString(R.string.notification_run_paused) else getString(R.string.notification_run_active)
        val body = stats?.let {
            "${it.paceDisplay} · ${it.distanceDisplay} · ${it.elapsedDisplay}"
        } ?: getString(R.string.notification_run_tracking)

        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_ACTIVE_RUN)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                if (paused) getString(R.string.notification_action_resume) else getString(R.string.notification_action_pause),
                pauseIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.notification_action_stop),
                stopIntent
            )
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WalkPlanner:ActiveRunWakeLock"
        ).apply { acquire(10 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        const val ACTION_REFRESH = "com.michael.walkplanner.action.REFRESH_RUN_NOTIFICATION"
        private const val REQUEST_CODE_PAUSE = 1
        private const val REQUEST_CODE_STOP = 2

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, ActiveRunForegroundService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun refreshNotification(context: Context) {
            val intent = Intent(context, ActiveRunForegroundService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveRunForegroundService::class.java))
        }
    }
}
