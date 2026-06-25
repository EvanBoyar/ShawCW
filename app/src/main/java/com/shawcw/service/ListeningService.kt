package com.shawcw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.shawcw.AppState
import com.shawcw.R
import com.shawcw.engine.DetectionEngine
import com.shawcw.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps capture and feedback running while the screen
 * is off and the app is backgrounded. It owns the [DetectionEngine] and follows
 * [AppState.settings] so settings changes from the UI take effect live.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var engine: DetectionEngine
    private var started = false

    override fun onCreate() {
        super.onCreate()
        engine = DetectionEngine(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's Stop action restarts the service with this action so
        // it can shut down without opening the app. Stopping clears the foreground
        // state, which dismisses the ongoing notification.
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            startForeground()
            // The running service is the source of truth for "listening", so the
            // UI stays correct even if the flag is touched elsewhere.
            AppState.updateSettings { it.copy(listening = true) }
            engine.start(AppState.settings.value)
            AppState.settings
                .onEach { engine.update(it) }
                .launchIn(scope)
        }
        return START_STICKY
    }

    private fun startForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "listening"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.listening_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        // Tapping the notification body brings the app back to the foreground.
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // The Stop action shuts the service down in place, without opening the app.
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.listening_notification_title))
            .setContentText(getString(R.string.listening_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.listening_notification_stop),
                    stopIntent,
                ).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        engine.release()
        scope.cancel()
        AppState.updateSettings { it.copy(listening = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.shawcw.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ListeningService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
