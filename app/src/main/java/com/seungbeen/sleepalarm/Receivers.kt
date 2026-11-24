package com.seungbeen.sleepalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 예약 시각이 되면 알람 서비스를 띄운다. (setAlarmClock 알람은 백그라운드 FGS 시작이 허용됨) */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val kind = intent.getIntExtra(EXTRA_KIND, KIND_MAIN)
        val p = Prefs(ctx)
        when (kind) {
            KIND_MAIN -> p.scheduledAt = 0
            KIND_CHECK -> p.checkAt = 0
        }
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, AlarmService::class.java).putExtra(EXTRA_KIND, kind)
        )
    }
}

/** 재부팅하면 AlarmManager 예약이 사라지므로 다시 건다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        if (p.scheduledAt > now) AlarmScheduler.schedule(ctx, p.scheduledAt, KIND_MAIN)
        if (p.checkAt > now) AlarmScheduler.schedule(ctx, p.checkAt, KIND_CHECK)
        p.addLog("재부팅 후 알람 복구")
    }
}
