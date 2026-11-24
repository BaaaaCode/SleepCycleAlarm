package com.seungbeen.sleepalarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** 알람 소리·진동을 내고, 잠금화면 위로 AlarmActivity를 띄우는 포그라운드 서비스 */
class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.seungbeen.sleepalarm.STOP"
        private const val MAX_RING_MS = 20 * 60_000L // 안전장치: 20분 뒤 자동 정지
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val kind = intent?.getIntExtra(EXTRA_KIND, KIND_MAIN) ?: KIND_MAIN
        startAlarm(kind)
        return START_NOT_STICKY
    }

    private fun startAlarm(kind: Int) {
        Notifs.ensureChannels(this)

        val activityIntent = Intent(this, AlarmActivity::class.java)
            .putExtra(EXTRA_KIND, kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val full = PendingIntent.getActivity(
            this, 10, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (kind) {
            KIND_CHECK -> "확인 알람: 진짜 일어났어요?"
            KIND_TEST -> "테스트 알람"
            else -> "일어날 시간이에요"
        }
        val notification = NotificationCompat.Builder(this, Notifs.CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("탭해서 문제를 풀면 꺼져요")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()

        startForeground(Notifs.ID_ALARM, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        playSound()
        vibrate()
        handler.postDelayed({ stopAlarm() }, MAX_RING_MS)
        Prefs(this).addLog("$title 울림")
    }

    private fun playSound() {
        if (player != null) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // 알람 볼륨, 방해금지 중에도 알람 허용이면 울림
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Prefs(this).addLog("소리 재생 실패: ${e.message}")
            null
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attrs)
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        player?.run { try { stop() } catch (_: Exception) {}; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        vibrator?.cancel()
        super.onDestroy()
    }
}
