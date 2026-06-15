package com.galaxyalarm.ring

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.ui.theme.Danger
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme

/**
 * 全画面アラーム画面。ロック画面上でも表示。複数同時鳴動はスタックで列挙。
 * 1件停止しても他は止めない。「すべて停止」は別ボタン。
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        setContent {
            GalaxyAlarmTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingContent(
                        onStop = { sendAction(AlarmIntents.ACTION_STOP, it) },
                        onSnooze = { sendAction(AlarmIntents.ACTION_SNOOZE, it) },
                        onStopAll = { sendStopAll() },
                        onEmpty = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
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
        sendBroadcast(Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_STOP_ALL
        })
    }
}

@Composable
private fun RingContent(
    onStop: (Long) -> Unit,
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onStop(a.occurrenceId) }, modifier = Modifier.weight(1f)) { Text("停止") }
                        OutlinedButton(onClick = { onSnooze(a.occurrenceId) }, modifier = Modifier.weight(1f)) { Text("スヌーズ") }
                    }
                }
            }
        }

        if (stack.size > 1) {
            Button(
                onClick = onStopAll, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger)
            ) { Text("すべて停止") }
        }
        Spacer(Modifier.height(12.dp))
    }
}
