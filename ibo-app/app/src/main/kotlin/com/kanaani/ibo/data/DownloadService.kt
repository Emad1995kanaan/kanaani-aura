package com.kanaani.ibo.data

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
import androidx.core.app.NotificationCompat
import com.kanaani.ibo.Brand
import com.kanaani.ibo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Service de premier plan : garde les téléchargements en vie quand l'application passe en arrière-plan. */
class DownloadService : Service() {
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopForegroundCompat(); stopSelf(); return START_NOT_STICKY }
        val n = notification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (ticker?.isActive != true) ticker = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) { delay(2_000); runCatching { manager().notify(ID, notification()) } }
        }
        return START_STICKY
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { ticker?.cancel(); if (instance === this) instance = null; super.onDestroy() }
    fun finish() { ticker?.cancel(); stopForegroundCompat(); stopSelf() }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true) }

    private fun manager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26 && manager().getNotificationChannel(CHANNEL) == null) {
            manager().createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val active = Downloads.list.filter { it.state == DlState.Running || it.state == DlState.Queued }
        val first = active.firstOrNull()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(Brand.name + " · " + (first?.title ?: "⬇"))
            .setContentText(if (active.size > 1) "⬇ ${active.size}" else first?.let { "${(it.progress * 100).toInt()}%" } ?: "")
            .setProgress(100, ((first?.progress ?: 0f) * 100).toInt(), first == null || first.total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .build()
    }

    companion object {
        private const val ID = 4201
        private const val CHANNEL = "downloads"
        private const val STOP = "stop"
        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }
        @Volatile private var instance: DownloadService? = null
        fun stop(@Suppress("UNUSED_PARAMETER") ctx: Context) {
            val s = instance ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { s.finish() } }
        }
    }
}
