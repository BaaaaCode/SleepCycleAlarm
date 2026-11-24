package com.seungbeen.sleepalarm

import android.service.notification.NotificationListenerService

/**
 * 시스템이 계속 바인딩해 두는 알림 리스너.
 * 워치가 잠든 걸 감지 → 수면 모드 → 폰 방해금지가 켜지면 onInterruptionFilterChanged가 불린다.
 */
class SleepListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        Prefs(this).addLog("리스너 연결됨")
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        val p = Prefs(this)
        when (interruptionFilter) {
            INTERRUPTION_FILTER_ALL -> p.addLog("방해금지 꺼짐")
            INTERRUPTION_FILTER_UNKNOWN -> Unit
            else -> {
                p.addLog("방해금지 켜짐 감지 (filter=$interruptionFilter)")
                AlarmScheduler.onSleepDetected(this)
            }
        }
    }
}
