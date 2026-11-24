package com.seungbeen.sleepalarm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설정과 상태를 SharedPreferences에 보관한다. */
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("sleep_alarm", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = p.getInt(key, def)
    private fun setInt(key: String, v: Int) = p.edit().putInt(key, v).apply()

    /** 목표 수면 주기 수 (1주기 = 90분) */
    var cycles: Int get() = int("cycles", 5); set(v) = setInt("cycles", v)
    /** 마감 시각에 맞추려고 줄일 때의 최소 주기 수 */
    var minCycles: Int get() = int("minCycles", 3); set(v) = setInt("minCycles", v)
    /** 워치 감지 지연 보정(분): 실제 잠든 시각 = 감지 시각 - lagMin */
    var lagMin: Int get() = int("lagMin", 10); set(v) = setInt("lagMin", v)

    /** 이 시각보다 늦게는 절대 안 깨움 */
    var deadlineEnabled: Boolean
        get() = p.getBoolean("deadlineEnabled", true)
        set(v) = p.edit().putBoolean("deadlineEnabled", v).apply()
    var deadlineHour: Int get() = int("deadlineHour", 8); set(v) = setInt("deadlineHour", v)
    var deadlineMinute: Int get() = int("deadlineMinute", 30); set(v) = setInt("deadlineMinute", v)

    /** 이 시간대에 들어온 수면 신호만 인정 (예: 21시~6시) */
    var windowStartHour: Int get() = int("windowStartHour", 21); set(v) = setInt("windowStartHour", v)
    var windowEndHour: Int get() = int("windowEndHour", 6); set(v) = setInt("windowEndHour", v)

    /** 알람을 끈 뒤 다시 잠들었는지 확인하는 알람 */
    var checkEnabled: Boolean
        get() = p.getBoolean("checkEnabled", true)
        set(v) = p.edit().putBoolean("checkEnabled", v).apply()
    var checkDelayMin: Int get() = int("checkDelayMin", 10); set(v) = setInt("checkDelayMin", v)

    /** 현재 예약된 메인 알람 시각 (0 = 없음) */
    var scheduledAt: Long
        get() = p.getLong("scheduledAt", 0L)
        set(v) = p.edit().putLong("scheduledAt", v).apply()
    /** 확인 알람 시각 (0 = 없음) */
    var checkAt: Long
        get() = p.getLong("checkAt", 0L)
        set(v) = p.edit().putLong("checkAt", v).apply()
    /** 이미 알람을 맞춘 밤 (같은 밤에 다시 잠들어도 알람을 뒤로 밀지 않기 위함) */
    var lastNightKey: String
        get() = p.getString("lastNightKey", "") ?: ""
        set(v) = p.edit().putString("lastNightKey", v).apply()

    var log: String
        get() = p.getString("log", "") ?: ""
        private set(v) = p.edit().putString("log", v).apply()

    fun addLog(msg: String) {
        val t = SimpleDateFormat("MM-dd HH:mm", Locale.KOREA).format(Date())
        log = (listOf("$t  $msg") + log.lines())
            .filter { it.isNotBlank() }
            .take(40)
            .joinToString("\n")
    }
}
