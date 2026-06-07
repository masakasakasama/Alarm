package com.galaxyalarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxyalarm.ui.theme.Danger
import com.galaxyalarm.ui.theme.Ok
import com.galaxyalarm.ui.theme.Warn

/** セクションカード。押下対応。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) { Box(Modifier.padding(18.dp)) { content() } }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) { Box(Modifier.padding(18.dp)) { content() } }
    }
}

/** 状態ピル(OK/警告/危険)。 */
@Composable
fun StatusPill(text: String, level: PillLevel) {
    val color = when (level) {
        PillLevel.OK -> Ok
        PillLevel.WARN -> Warn
        PillLevel.DANGER -> Danger
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

enum class PillLevel { OK, WARN, DANGER }

/** 大きい数字 + 小さい説明。 */
@Composable
fun BigStat(value: String, label: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = valueColor, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { left() }
        right()
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
