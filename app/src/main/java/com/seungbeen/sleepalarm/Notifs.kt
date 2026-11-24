package com.seungbeen.sleepalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    const val CH_ALARM = "alarm"
    const val CH_INFO = "info"
    const val ID_ALARM = 1
    const val ID_INFO = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                // 소리/진동은 AlarmService가 직접 낸다
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "알람 설정 안내", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun info(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            nmc.notify(ID_INFO, n)
        } catch (_: SecurityException) {
        }
    }
}
