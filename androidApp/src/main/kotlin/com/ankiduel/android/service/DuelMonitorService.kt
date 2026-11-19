package com.ankiduel.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ankiduel.android.AnkiDuelApplication
import com.ankiduel.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DuelMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initialisation du suivi AnkiDroid…"))
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startMonitoringLoop() {
        val repo = (application as AnkiDuelApplication).container.repository
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                runCatching {
                    repo.refreshAndSync()
                    val usage = repo.usageSnapshot.value
                    val lock = repo.lockStatus.value
                    val ankiMin = usage.ankiEffectiveSeconds / 60L
                    val socialMin = usage.socialForegroundSeconds / 60L
                    val statusText = if (lock.isLocked) {
                        "Réseaux bloqués • ${lock.maxDebtMinutes} min Anki à rattraper (Anki ${ankiMin}m / Soc ${socialMin}m)"
                    } else {
                        "Réseaux débloqués • Anki ${ankiMin}m vs Réseaux ${socialMin}m"
                    }
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(statusText))
                }
                delay(15_000L)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance AnkiDuel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante affichant votre ratio AnkiDroid vs Réseaux Sociaux."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.ankiduel.android.R.drawable.ic_notification)
            .setColor(0xFF3B82F6.toInt())
            .setContentTitle("AnkiDuel actif")
            .setContentText(content)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ankiduel_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DuelMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
