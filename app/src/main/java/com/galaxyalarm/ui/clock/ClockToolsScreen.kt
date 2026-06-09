package com.galaxyalarm.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill
import com.galaxyalarm.ui.components.WheelPicker
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ClockToolsScreen(
    vm: MainViewModel,
    onAddAlarm: () -> Unit,
) {
    val nextAlarm by vm.nextAlarmRow.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("時計", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(formatLocalDate(now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatLocalTime(now), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("次のアラーム", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (nextAlarm == null) {
                            Text("予定なし", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("ウィジェットにもこの状態が表示されます", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val row = nextAlarm!!
                            Text(TimeFormat.nextTrigger(row.nextTriggerAt), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${TimeFormat.hourMinute12(row.alarm.hour, row.alarm.minute)} ・ ${row.alarm.label.ifBlank { row.groupName }}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onAddAlarm) { Text("+ 追加") }
                }
            }
        }
        item { WorldClockBlock(now) }
        item { StopwatchBlock() }
        item { TimerBlock() }
    }
}

@Composable
private fun WorldClockBlock(now: Long) {
    val zones = remember {
        listOf(
            "東京" to "Asia/Tokyo",
            "ニューヨーク" to "America/New_York",
            "ロンドン" to "Europe/London",
            "ロサンゼルス" to "America/Los_Angeles"
        )
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("世界時計", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            zones.forEach { (city, zoneId) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(city, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTimeInZone(now, zoneId), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StopwatchBlock() {
    var running by rememberSaveable { mutableStateOf(false) }
    var baseElapsed by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var displayElapsed by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running, baseElapsed, startedAt) {
        while (running) {
            displayElapsed = baseElapsed + (System.currentTimeMillis() - startedAt)
            delay(50)
        }
        if (!running) displayElapsed = baseElapsed
    }

    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ストップウォッチ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(if (running) "計測中" else "停止", if (running) PillLevel.OK else PillLevel.WARN)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatDuration(displayElapsed), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (running) {
                            laps.add(0, displayElapsed)
                        } else {
                            baseElapsed = 0L
                            displayElapsed = 0L
                            laps.clear()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (running) "ラップ" else "リセット") }
                Button(
                    onClick = {
                        if (running) {
                            baseElapsed = displayElapsed
                            running = false
                        } else {
                            startedAt = System.currentTimeMillis()
                            running = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (running) "停止" else "開始") }
            }
            laps.take(3).forEachIndexed { index, lap ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ラップ ${laps.size - index}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(lap), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimerBlock() {
    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    var armed by rememberSaveable { mutableStateOf(false) }   // カウントダウン中(実行/一時停止)
    var remainingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var resetKey by rememberSaveable { mutableIntStateOf(0) }  // ホイールをプリセットに合わせて再描画する用

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
        if (remainingSeconds <= 0) {
            running = false
            armed = false
        }
    }

    val configuredSeconds = hours * 3600 + minutes * 60 + seconds

    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("タイマー", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(if (running) "実行中" else if (armed) "一時停止" else "待機", if (running) PillLevel.OK else PillLevel.WARN)
            }
            Spacer(Modifier.height(8.dp))

            if (!armed) {
                // 時・分・秒のホイールで任意の時間を設定。
                key(resetKey) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimerWheel("時", 24, hours) { hours = it }
                        TimerWheel("分", 60, minutes) { minutes = it }
                        TimerWheel("秒", 60, seconds) { seconds = it }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10).forEach { m ->
                        OutlinedButton(
                            onClick = { hours = 0; minutes = m; seconds = 0; resetKey++ },
                            modifier = Modifier.weight(1f)
                        ) { Text("${m}分") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        remainingSeconds = configuredSeconds
                        armed = true
                        running = true
                    },
                    enabled = configuredSeconds > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("開始") }
            } else {
                Text(formatSeconds(remainingSeconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { running = false; armed = false; remainingSeconds = 0 },
                        modifier = Modifier.weight(1f)
                    ) { Text("キャンセル") }
                    Button(
                        onClick = { running = !running },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (running) "一時停止" else "再開") }
                }
            }
        }
    }
}

@Composable
private fun TimerWheel(label: String, count: Int, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            items = (0 until count).map { it.toString().padStart(2, '0') },
            initialIndex = value.coerceIn(0, count - 1),
            modifier = Modifier.width(54.dp),
            loop = true,
            visibleCount = 3,
            onSelectedIndex = onChange
        )
        Text(label, modifier = Modifier.padding(start = 2.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatLocalTime(millis: Long): String =
    SimpleDateFormat("h:mm:ss a", Locale.JAPAN).format(millis)

private fun formatLocalDate(millis: Long): String =
    SimpleDateFormat("yyyy年M月d日 EEEE", Locale.JAPAN).format(millis)

private fun formatTimeInZone(millis: Long, zoneId: String): String =
    SimpleDateFormat("h:mm a", Locale.JAPAN).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(millis)

private fun formatDuration(ms: Long): String {
    val total = ms / 10
    val centis = total % 100
    val seconds = (total / 100) % 60
    val minutes = (total / 6000) % 60
    val hours = total / 360000
    return if (hours > 0) {
        String.format(Locale.JAPAN, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.JAPAN, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}

private fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds / 60) % 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.JAPAN, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.JAPAN, "%02d:%02d", m, s)
    }
}
