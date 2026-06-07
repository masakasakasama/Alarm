package com.galaxyalarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS 風の回転ホイール(ドラム)ピッカー。中央の項目が選択値。
 * loop=true で無限スクロール風(大きく繰り返したリストの中央から開始)。
 */
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    visibleCount: Int = 5,
    itemHeight: Dp = 42.dp,
    onSelectedIndex: (Int) -> Unit,
) {
    val realCount = items.size
    if (realCount == 0) return
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val half = visibleCount / 2

    val totalCount = if (loop) realCount * 1000 else realCount
    val loopBase = if (loop) realCount * 500 else 0
    val startIndex = (loopBase + initialIndex).coerceIn(0, totalCount - 1)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 画面中央に来ている項目のインデックス。
    val centeredIndex by remember {
        derivedStateOf {
            val offsetItems = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            listState.firstVisibleItemIndex + offsetItems
        }
    }
    LaunchedEffect(centeredIndex) {
        onSelectedIndex(((centeredIndex % realCount) + realCount) % realCount)
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        // 中央の選択枠
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * half),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(totalCount) { index ->
                val value = items[index % realCount]
                val distance = abs(index - centeredIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.45f
                    2 -> 0.22f
                    else -> 0.1f
                }
                Box(
                    Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value,
                        fontSize = if (distance == 0) 26.sp else 22.sp,
                        fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 時・分・AM/PM の3列ホイール。値は24時間制(hour24)で受け渡しするが、表示は12時間制。
 */
@Composable
fun WheelTimePicker(
    hour24: Int,
    minute: Int,
    onChange: (hour24: Int, minute: Int) -> Unit,
) {
    val hours = remember { (1..12).map { it.toString() } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val ampm = remember { listOf("AM", "PM") }

    var hIndex by remember { mutableIntStateOf(if (hour24 % 12 == 0) 11 else hour24 % 12 - 1) }
    var mIndex by remember { mutableIntStateOf(minute.coerceIn(0, 59)) }
    var apIndex by remember { mutableIntStateOf(if (hour24 < 12) 0 else 1) }

    fun emit() {
        val h12 = hIndex + 1
        val h24 = when {
            apIndex == 0 && h12 == 12 -> 0     // 12 AM = 0時
            apIndex == 0 -> h12                // 午前
            h12 == 12 -> 12                    // 12 PM = 12時
            else -> h12 + 12                   // 午後
        }
        onChange(h24, mIndex)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(hours, hIndex, Modifier.width(64.dp), loop = true) { hIndex = it; emit() }
        Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp))
        WheelPicker(minutes, mIndex, Modifier.width(64.dp), loop = true) { mIndex = it; emit() }
        Spacer(Modifier.width(10.dp))
        WheelPicker(ampm, apIndex, Modifier.width(64.dp), loop = false) { apIndex = it; emit() }
    }
}
