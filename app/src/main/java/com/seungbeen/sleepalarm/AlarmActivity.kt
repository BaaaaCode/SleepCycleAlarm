package com.seungbeen.sleepalarm

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import kotlin.random.Random

/** 잠금화면 위에 뜨는 알람 화면. 암산 문제를 풀어야 꺼진다. */
class AlarmActivity : ComponentActivity() {

    private var answer = 0
    private lateinit var problem: TextView
    private lateinit var input: EditText
    private lateinit var feedback: TextView
    private var kind = KIND_MAIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        kind = intent.getIntExtra(EXTRA_KIND, KIND_MAIN)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = when (kind) {
                KIND_CHECK -> "다시 잠들지 않았죠?"
                KIND_TEST -> "테스트 알람"
                else -> "일어날 시간이에요"
            }
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = AlarmScheduler.fmt(System.currentTimeMillis())
            textSize = 56f
            gravity = Gravity.CENTER
        })
        problem = TextView(this).apply { textSize = 32f; gravity = Gravity.CENTER; setPadding(0, pad, 0, pad / 2) }
        root.addView(problem)
        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 28f
            gravity = Gravity.CENTER
            hint = "정답"
        }
        root.addView(input)
        feedback = TextView(this).apply { gravity = Gravity.CENTER; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(feedback)
        root.addView(Button(this).apply {
            text = "끄기"
            textSize = 22f
            setOnClickListener { check() }
        })
        setContentView(root)

        newProblem()
        // 뒤로가기로 못 끄게 막음
        onBackPressedDispatcher.addCallback(this) { }
    }

    private fun newProblem() {
        val a = Random.nextInt(13, 60)
        val b = Random.nextInt(13, 60)
        answer = a + b
        problem.text = "$a + $b = ?"
        input.setText("")
    }

    private fun check() {
        if (input.text.toString().trim().toIntOrNull() == answer) {
            dismiss()
        } else {
            feedback.text = "틀렸어요. 새 문제예요."
            newProblem()
        }
    }

    private fun dismiss() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        val p = Prefs(this)
        if (kind == KIND_MAIN && p.checkEnabled) {
            val at = System.currentTimeMillis() + p.checkDelayMin * 60_000L
            if (AlarmScheduler.schedule(this, at, KIND_CHECK)) {
                p.checkAt = at
                p.addLog("확인 알람 ${AlarmScheduler.fmt(at)} 예약")
                Notifs.info(this, "확인 알람 예약됨", "${AlarmScheduler.fmt(at)}에 한 번 더 울려요")
            }
        }
        p.addLog("알람 해제")
        finish()
    }
}
