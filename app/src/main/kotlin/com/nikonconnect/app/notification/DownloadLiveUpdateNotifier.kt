package com.nikonconnect.app.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.nikonconnect.app.MainActivity
import com.nikonconnect.app.R

data class DownloadLiveUpdate(
    val fileName: String,
    val currentItem: Int,
    val totalItems: Int,
    val transferredBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else {
            ((transferredBytes.coerceIn(0L, totalBytes).toDouble() / totalBytes) * 100.0).toInt()
        }
}

class DownloadLiveUpdateNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var lastPostedAt = 0L
    private var lastPercent = -1
    private var lastItem = -1

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "照片传输",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示从 Nikon 相机传输照片的实时进度"
            setSound(null, null)
            enableVibration(false)
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun update(state: DownloadLiveUpdate, force: Boolean = false) {
        if (!canPostNotifications()) return
        val now = SystemClock.elapsedRealtime()
        val itemChanged = state.currentItem != lastItem
        val progressChanged = state.percent != lastPercent
        if (!force && !itemChanged &&
            (!progressChanged || now - lastPostedAt < MIN_UPDATE_INTERVAL_MILLIS)
        ) return

        val style = NotificationCompat.ProgressStyle()
            .setProgress(state.percent)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(appContext, R.drawable.ic_notification_transfer),
            )
        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle("正在传输 ${state.currentItem}/${state.totalItems}")
            .setContentText("${state.fileName} · ${state.percent}%")
            .setShortCriticalText("${state.percent}%")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRequestPromotedOngoing(true)
            .build()

        val posted = runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }.isSuccess
        if (!posted) return
        lastPostedAt = now
        lastPercent = state.percent
        lastItem = state.currentItem
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
        lastPostedAt = 0L
        lastPercent = -1
        lastItem = -1
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return notificationManager.areNotificationsEnabled()
    }

    private companion object {
        const val CHANNEL_ID = "camera_transfer_live_update"
        const val NOTIFICATION_ID = 15_740
        const val MIN_UPDATE_INTERVAL_MILLIS = 500L
    }
}
