package com.seungbeen.sleepalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val EXTRA_KIND = "kind"
const val KIND_MAIN = 0
const val KIND_CHECK = 1
const val KIND_TEST = 2

object AlarmScheduler {
    private const val CYCLE_MS = 90 * 60_000L

    fun fmt(t: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(t))

    private fun inWindow(hour: Int, start: Int, end: Int): Boolean =
        if (start > end) hour >= start || hour < end else hour in start until end

    /** 저녁~새벽을 하나의 '밤'으로 묶는 키 (정오 기준으로 날짜를 자름) */
    private fun nightKey(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(t - 12 * 60 * 60_000L))

    private fun nextOccurrence(from: Long, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** @return (기상 시각, 주기 수). 주기 수가 -1이면 마감 시각에 맞춘 것. */
    fun computeWakeTime(p: Prefs, detectedAt: Long): Pair<Long, Int> {
        val onset = detectedAt - p.lagMin * 60_000L
        var n = p.cycles.coerceAtLeast(1)
        var wake = onset + n * CYCLE_MS
        if (p.deadlineEnabled) {
            val deadline = nextOccurrence(detectedAt, p.deadlineHour, p.deadlineMinute)
            while (wake > deadline && n > p.minCycles) {
                n--
                wake = onset + n * CYCLE_MS
            }
            if (wake > deadline) return deadline to -1
        }
        return wake to n
    }

    /** 수면 신호(방해금지 켜짐)가 들어왔을 때 호출 */
    fun onSleepDetected(ctx: Context, detectedAt: Long = System.currentTimeMillis(), force: Boolean = false) {
        val p = Prefs(ctx)
        val hour = Calendar.getInstance().apply { timeInMillis = detectedAt }.get(Calendar.HOUR_OF_DAY)

        if (!force && !inWindow(hour, p.windowStartHour, p.windowEndHour)) {
            p.addLog("수면 신호 무시: 인정 시간대 밖")
            return
        }
        val key = nightKey(detectedAt)
        if (!force && p.lastNightKey == key) {
            p.addLog("수면 신호 무시: 오늘 밤 알람 이미 설정됨 (${fmt(p.scheduledAt)})")
            return
        }

        val (wake, n) = computeWakeTime(p, detectedAt)
        if (wake <= System.currentTimeMillis() + 60_000L) {
            p.addLog("계산된 기상 시각이 이미 지나서 건너뜀")
            return
        }
        if (!schedule(ctx, wake, KIND_MAIN)) {
            p.addLog("정확한 알람 권한이 없어 설정 실패")
            Notifs.info(ctx, "알람 설정 실패", "앱을 열어 권한을 확인해 주세요")
            return
        }
        p.scheduledAt = wake
        p.lastNightKey = key
        val desc = if (n > 0) "${n}주기" else "마감 시각"
        p.addLog("수면 감지 → 알람 ${fmt(wake)} ($desc)")
        Notifs.info(ctx, "수면 감지됨", "알람 ${fmt(wake)} 설정 ($desc)")
    }

    private fun requestCode(kind: Int) = 1000 + kind

    private fun operation(ctx: Context, kind: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode(kind),
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(ctx: Context, at: Long, kind: Int): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return false
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock: 절전(Doze) 중에도 정확히 울리고, 상태바에 알람 아이콘이 뜬다
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation(ctx, kind))
        return true
    }

    fun cancel(ctx: Context, kind: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(operation(ctx, kind))
    }

    fun cancelAll(ctx: Context) {
        cancel(ctx, KIND_MAIN); cancel(ctx, KIND_CHECK); cancel(ctx, KIND_TEST)
        Prefs(ctx).apply { scheduledAt = 0; checkAt = 0 }
    }
}
