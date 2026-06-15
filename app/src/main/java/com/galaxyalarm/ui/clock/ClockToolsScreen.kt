package com.galaxyalarm.ui.clock

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.timer.StopwatchController
import com.galaxyalarm.timer.TimerController
import com.galaxyalarm.timer.TimerEntry
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

/** タイマー専用タブ。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val timers by TimerController.timers.collectAsStateWithLifecycle()
    val history by TimerController.history.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("タイマー", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        items(timers, key = { it.id }) { entry ->
            ActiveTimerCard(entry = entry, onCancel = { TimerController.cancel(context, entry.id) })
        }
        if (timers.isEmpty() || showForm) {
            item(key = "add_form") {
                AddTimerForm(history = history) { seconds, soundOn ->
                    TimerController.start(context, seconds, soundOn)
                    showForm = false
                }
            }
        } else {
            item(key = "add_button") {
                OutlinedButton(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ タイマーを追加")
                }
            }
        }
    }
}

/** ストップウォッチ専用タブ。 */
@Composable
fun StopwatchScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("ストップウォッチ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item { StopwatchBlock() }
    }
}

/** 現在時刻(日付+大きな時計)だけのカード。 */
@Composable
fun NowCard() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Text(formatLocalDate(now), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatLocalTime(now), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** 次のアラームだけのカード(現在時刻とは別に表示)。 */
@Composable
fun NextAlarmCard(vm: MainViewModel, onAddAlarm: () -> Unit) {
    val nextAlarm by vm.nextAlarmRow.collectAsStateWithLifecycle()
    SectionCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("次のアラーム", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (nextAlarm == null) {
                    Text("予定なし", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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

/** アラームタブ上部に出す「現在時刻+次のアラーム」カード。 */
@Composable
fun NowAndNextAlarmCard(vm: MainViewModel, onAddAlarm: () -> Unit) {
    val nextAlarm by vm.nextAlarmRow.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatLocalDate(now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatLocalTime(now), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (nextAlarm == null) {
                    Text("次のアラーム: 予定なし", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val row = nextAlarm!!
                    Text(
                        "次のアラーム: ${TimeFormat.nextTrigger(row.nextTriggerAt)} ・ ${row.alarm.label.ifBlank { row.groupName }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onAddAlarm) { Text("+ 追加") }
        }
    }
}

/** アラームタブ上部に出す世界時計カード。 */
@Composable
fun WorldClockCard() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    WorldClockBlock(now)
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
    // 状態はシングルトンに保持(タブ移動で消えない)。
    val running by StopwatchController.running.collectAsStateWithLifecycle()
    val laps by StopwatchController.laps.collectAsStateWithLifecycle()
    var display by remember { mutableLongStateOf(StopwatchController.elapsed()) }

    LaunchedEffect(running) {
        do {
            display = StopwatchController.elapsed()
            delay(50)
        } while (running)
        display = StopwatchController.elapsed()
    }

    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ストップウォッチ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(if (running) "計測中" else "停止", if (running) PillLevel.OK else PillLevel.WARN)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatDuration(display), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { StopwatchController.lapOrReset() },
                    modifier = Modifier.weight(1f)
                ) { Text(if (running) "ラップ" else "リセット") }
                Button(
                    onClick = { StopwatchController.startStop() },
                    modifier = Modifier.weight(1f)
                ) { Text(if (running) "停止" else "開始") }
            }
            laps.take(5).forEachIndexed { index, lap ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ラップ ${laps.size - index}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(lap), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerBlock() {
    val context = LocalContext.current
    val endAt by TimerController.endAt.collectAsStateWithLifecycle()
    val persistedSound by TimerController.soundOn.collectAsStateWithLifecycle()
    val history by TimerController.history.collectAsStateWithLifecycle()
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt > 0L) {
            nowTick = System.currentTimeMillis()
            delay(250)
        }
    }
    val running = endAt > nowTick
    val remaining = ((endAt - nowTick) / 1000L).toInt().coerceAtLeast(0)

    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var resetKey by rememberSaveable { mutableIntStateOf(0) }
    var soundOn by rememberSaveable { mutableStateOf(persistedSound) }
    val configuredSeconds = hours * 3600 + minutes * 60 + seconds

    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("カウントダウン", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(if (running) "実行中" else "待機", if (running) PillLevel.OK else PillLevel.WARN)
            }
            Spacer(Modifier.height(8.dp))

            if (!running) {
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

                // 履歴: 過去に使った時間をタップで再設定。
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("履歴", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.forEach { sec ->
                            OutlinedButton(onClick = {
                                hours = sec / 3600
                                minutes = (sec / 60) % 60
                                seconds = sec % 60
                                resetKey++
                            }) { Text(formatSeconds(sec)) }
                        }
                    }
                }

                // 音あり / 音なし(バイブのみ)の選択。
                Spacer(Modifier.height(12.dp))
                Text("終了時の鳴り方", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = soundOn,
                        onClick = { soundOn = true },
                        label = { Text("音あり") }
                    )
                    FilterChip(
                        selected = !soundOn,
                        onClick = { soundOn = false },
                        label = { Text("音なし(バイブ)") }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { TimerController.start(context, configuredSeconds, soundOn) },
                    enabled = configuredSeconds > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("開始") }
            } else {
                Text(formatSeconds(remaining), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (persistedSound) "時間が来たらアラームとして鳴ります(タブを移動しても継続)"
                    else "時間が来たら音なし(バイブのみ)で知らせます(タブを移動しても継続)",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { TimerController.cancel(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("キャンセル") }
            }
        }
    }
}

/** アラームタブ(ホーム)に出す「実行中タイマー」カード。実行中のみ表示。 */
@Composable
fun RunningTimerCard() {
    val context = LocalContext.current
    val endAt by TimerController.endAt.collectAsStateWithLifecycle()
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt > 0L) {
            nowTick = System.currentTimeMillis()
            delay(250)
        }
    }
    if (endAt <= nowTick) return
    val remaining = ((endAt - nowTick) / 1000L).toInt().coerceAtLeast(0)
    SectionCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("タイマー実行中", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatSeconds(remaining), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { TimerController.cancel(context) }) { Text("キャンセル") }
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
