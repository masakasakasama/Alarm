package com.galaxyalarm.ui.alarms

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.ui.AlarmRow
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import org.junit.Rule
import org.junit.Test

class AlarmHourSectionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun everyHourAndAlarmIsVisibleByScrollingWithoutOpeningAnything() {
        val rows = listOf(
            AlarmRow(AlarmItem(id = 1, groupId = 1, hour = 1, minute = 58), "", true, null),
            AlarmRow(AlarmItem(id = 2, groupId = 1, hour = 2, minute = 0), "", true, null),
            AlarmRow(AlarmItem(id = 3, groupId = 1, hour = 4, minute = 5), "", true, null),
        )
        composeRule.setContent {
            GalaxyAlarmTheme {
                LazyColumn(Modifier.testTag("hours")) {
                    alarmHourSections(rows) { Text("${it.alarm.hour}:${it.alarm.minute}") }
                }
            }
        }
        listOf("1時台", "1:58", "2時台", "2:0", "4時台", "4:5").forEach { text ->
            composeRule.onNodeWithTag("hours").performScrollToNode(hasText(text))
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }
}
