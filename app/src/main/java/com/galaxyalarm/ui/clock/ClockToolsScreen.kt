package com.galaxyalarm.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxyalarm.ui.components.SectionCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ClockToolsScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("世界時計", "ストップウォッチ", "タイマー")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) }
                )
            }
        }
        when (selected) {
            0 -> WorldClockPane()
            1 -> StopwatchPane()
            else -> TimerPane()
        }
    }
}

@Composable
private fun WorldClockPane() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val zones = remember {
        listOf(
            "東京" to "Asia/Tokyo",
            "ニューヨーク" to "America/New_York",
            "ロンドン" to "Europe/London",
            "ロサンゼルス" to "America/Los_Angeles",
            "ソウル" to "Asia/Seoul",
            "シンガポール" to "Asia/Singapore"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("世界時計", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        zones.forEach { (city, zoneId) ->
            item {
                val zone = TimeZone.getTimeZone(zoneId)
                SectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(city, style = MaterialTheme.typography.titleLarge)
                            Text(zone.id, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatTime(now, zone), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchPane() {
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
        displayElapsed = baseElapsed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("ストップウォッチ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(formatDuration(displayElapsed), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
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
                }
            }
        }
        laps.forEachIndexed { index, lap ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ラップ ${laps.size - index}")
                    Text(formatDuration(lap), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimerPane() {
    var totalSeconds by rememberSaveable { mutableIntStateOf(5 * 60) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(totalSeconds) }
    var running by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
        if (remainingSeconds <= 0) running = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("タイマー", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(formatSeconds(remainingSeconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 30).forEach { minute ->
                            OutlinedButton(
                                onClick = {
                                    totalSeconds = minute * 60
                                    remainingSeconds = totalSeconds
                                    running = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("${minute}分") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                running = false
                                remainingSeconds = totalSeconds
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("リセット") }
                        Button(
                            onClick = { running = !running },
                            enabled = remainingSeconds > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (running) "一時停止" else "開始") }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long, timeZone: TimeZone): String =
    SimpleDateFormat("h:mm:ss a", Locale.JAPAN).apply { this.timeZone = timeZone }.format(millis)

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
