package com.seungbeen.sleepalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    private lateinit var p: Prefs
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private lateinit var cycles: EditText
    private lateinit var minCycles: EditText
    private lateinit var lag: EditText
    private lateinit var deadlineOn: CheckBox
    private lateinit var deadline: EditText
    private lateinit var winStart: EditText
    private lateinit var winEnd: EditText
    private lateinit var checkOn: CheckBox
    private lateinit var checkDelay: EditText

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        Notifs.ensureChannels(this)

        val pad = dp(16)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        col.addView(header("상태"))
        status = TextView(this).apply { textSize = 15f }
        col.addView(status)

        col.addView(header("권한"))
        col.addView(button("① 알림 접근 허용 (방해금지 감지용)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(button("② 알림 권한") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })
        col.addView(button("③ 전체화면 알림 허용 (잠금화면 위 알람)") {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            } else toast("이 버전은 따로 설정할 필요 없어요")
        })
        col.addView(button("④ 배터리 최적화 제외 (리스너가 죽지 않게)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        col.addView(header("설정"))
        cycles = number(col, "목표 수면 주기 수 (1주기 = 90분)", p.cycles)
        minCycles = number(col, "마감에 맞출 때 최소 주기 수", p.minCycles)
        lag = number(col, "감지 지연 보정 (분, 감지 시각에서 빼기)", p.lagMin)
        deadlineOn = CheckBox(this).apply { text = "마감 시각 사용 (이보다 늦게는 안 깨움)"; isChecked = p.deadlineEnabled }
        col.addView(deadlineOn)
        deadline = text(col, "마감 시각 (HH:mm)", "%02d:%02d".format(p.deadlineHour, p.deadlineMinute))
        winStart = number(col, "수면 신호 인정 시작 (시)", p.windowStartHour)
        winEnd = number(col, "수면 신호 인정 끝 (시)", p.windowEndHour)
        checkOn = CheckBox(this).apply { text = "확인 알람 사용 (끈 뒤 한 번 더)"; isChecked = p.checkEnabled }
        col.addView(checkOn)
        checkDelay = number(col, "확인 알람 간격 (분)", p.checkDelayMin)
        col.addView(button("저장") { save() })

        col.addView(header("테스트"))
        col.addView(button("1분 뒤 테스트 알람") {
            val at = System.currentTimeMillis() + 60_000L
            if (AlarmScheduler.schedule(this, at, KIND_TEST)) toast("${AlarmScheduler.fmt(at)}에 울려요. 화면 꺼두고 기다려 보세요")
            else toast("정확한 알람 권한이 없어요")
        })
        col.addView(button("지금 잠든 것으로 처리") {
            save()
            AlarmScheduler.onSleepDetected(this, force = true)
            refresh()
        })
        col.addView(button("예약된 알람 모두 취소") {
            AlarmScheduler.cancelAll(this)
            p.addLog("사용자가 알람 취소")
            refresh()
        })

        col.addView(header("기록"))
        logView = TextView(this).apply { textSize = 13f; typeface = Typeface.MONOSPACE }
        col.addView(logView)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun save() {
        p.cycles = cycles.int(p.cycles).coerceIn(1, 8)
        p.minCycles = minCycles.int(p.minCycles).coerceIn(1, p.cycles)
        p.lagMin = lag.int(p.lagMin).coerceIn(0, 60)
        p.deadlineEnabled = deadlineOn.isChecked
        deadline.text.toString().split(":").takeIf { it.size == 2 }?.let { (h, m) ->
            val hh = h.trim().toIntOrNull(); val mm = m.trim().toIntOrNull()
            if (hh != null && mm != null && hh in 0..23 && mm in 0..59) {
                p.deadlineHour = hh; p.deadlineMinute = mm
            }
        }
        p.windowStartHour = winStart.int(p.windowStartHour).coerceIn(0, 23)
        p.windowEndHour = winEnd.int(p.windowEndHour).coerceIn(0, 23)
        p.checkEnabled = checkOn.isChecked
        p.checkDelayMin = checkDelay.int(p.checkDelayMin).coerceIn(1, 60)
        toast("저장했어요")
        refresh()
    }

    private fun refresh() {
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val notifOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val am = getSystemService(AlarmManager::class.java)
        val exactOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val fullOn = Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        fun mark(b: Boolean) = if (b) "✅" else "❌"
        val now = System.currentTimeMillis()
        val main = if (p.scheduledAt > now) AlarmScheduler.fmt(p.scheduledAt) else "없음"
        val check = if (p.checkAt > now) AlarmScheduler.fmt(p.checkAt) else "없음"
        status.text = """
            ${mark(listenerOn)} 알림 접근   ${mark(notifOn)} 알림
            ${mark(exactOn)} 정확한 알람   ${mark(fullOn)} 전체화면 알림
            예약된 알람: $main   확인 알람: $check
        """.trimIndent()
        logView.text = p.log.ifBlank { "(아직 기록 없음)" }
    }

    // ---- 작은 UI 헬퍼들 ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun header(s: String) = TextView(this).apply {
        text = s
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun button(s: String, onClick: (View) -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener(onClick)
    }

    private fun number(parent: LinearLayout, label: String, value: Int): EditText {
        parent.addView(TextView(this).apply { text = label; setPadding(0, dp(8), 0, 0) })
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            parent.addView(this)
        }
    }

    private fun text(parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(TextView(this).apply { text = label; setPadding(0, dp(8), 0, 0) })
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            setText(value)
            parent.addView(this)
        }
    }

    private fun EditText.int(def: Int) = text.toString().trim().toIntOrNull() ?: def
}
