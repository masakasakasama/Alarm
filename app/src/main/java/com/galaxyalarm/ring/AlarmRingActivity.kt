package com.galaxyalarm.ring

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.ui.theme.Danger
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme

/**
 * 全画面アラーム画面。ロック画面上でも表示し、停止はすべての鳴動へ即時適用する。
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoverFromNotificationIntent(intent)
        showOverLockscreen()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAllAndClose()
        })
        setContent {
            GalaxyAlarmTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingContent(
                        onSnooze = { sendAction(AlarmIntents.ACTION_SNOOZE, it) },
                        onStopAll = { stopAllAndClose() },
                        onEmpty = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recoverFromNotificationIntent(intent)
    }

    /**
     * 通知を押した瞬間にプロセス再生成やService再配信が重なると、プロセス内だけの
     * ActiveAlarms が一時的に空になることがある。その場合でも通知Intentに埋め込んだ
     * occurrence情報から画面を復元し、「押したのに即finishして何も起きない」を防ぐ。
     */
    private fun recoverFromNotificationIntent(source: Intent) {
        val occurrenceId = source.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1L)
        val dismissed = occurrenceId != -1L && AlarmDismissalStore(applicationContext).isDismissed(occurrenceId)
        val recovered = NotificationRingRecovery.candidate(
            isNotificationTap = source.action == NotificationHelper.ACTION_OPEN_ALARM_RING,
            occurrenceId = occurrenceId,
            alarmId = source.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L),
            label = source.getStringExtra(NotificationHelper.EXTRA_ALARM_LABEL),
            timeText = source.getStringExtra(NotificationHelper.EXTRA_ALARM_TIME_TEXT),
            dismissed = dismissed,
            alreadyActive = occurrenceId != -1L && ActiveAlarms.contains(occurrenceId),
        ) ?: return

        ActiveAlarms.push(recovered)
        Log.i(TAG, "recovered notification tap occurrence=${recovered.occurrenceId}")
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sendAction(action: String, occurrenceId: Long) {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
        })
    }

    private fun sendStopAll() {
        AlarmStopController.stopAllNow(
            this,
            intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1L),
        )
    }

    private fun stopAllAndClose() {
        sendStopAll()
        finishAndRemoveTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            stopAllAndClose()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val TAG = "AlarmRingActivity"
    }
}

/** Pure recovery decision so notification-tap edge cases can be unit tested without Android UI. */
internal object NotificationRingRecovery {
    fun candidate(
        isNotificationTap: Boolean,
        occurrenceId: Long,
        alarmId: Long,
        label: String?,
        timeText: String?,
        dismissed: Boolean,
        alreadyActive: Boolean,
    ): ActiveAlarm? {
        if (!isNotificationTap || occurrenceId == -1L || dismissed || alreadyActive) return null
        return ActiveAlarm(
            occurrenceId = occurrenceId,
            alarmId = alarmId,
            label = label.orEmpty().ifBlank { "アラーム" },
            timeText = timeText.orEmpty(),
        )
    }
}

@Composable
private fun RingContent(
    onSnooze: (Long) -> Unit,
    onStopAll: () -> Unit,
    onEmpty: () -> Unit,
) {
    val stack by ActiveAlarms.stack.collectAsState()
    if (stack.isEmpty()) { onEmpty(); return }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("⏰ アラーム", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        if (stack.size > 1) {
            Text("${stack.size} 件が同時に鳴動中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(stack, key = { it.occurrenceId }) { a ->
                Column(Modifier.fillMaxWidth()) {
                    Text(a.timeText, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(if (a.label.isBlank()) "アラーム" else a.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onStopAll,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        ) {
                            Text(if (stack.size > 1) "すべて停止" else "停止")
                        }
                        OutlinedButton(
                            onClick = { onSnooze(a.occurrenceId) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("スヌーズ") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
