package com.galaxyalarm.ui.alarms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import org.junit.Rule
import org.junit.Test

class AlarmHourTabsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun hourAndAllTabsCanBeSelected() {
        var selected by mutableStateOf<Int?>(1)
        composeRule.setContent {
            GalaxyAlarmTheme { AlarmHourTabs(listOf(1, 2, 4), selected) { selected = it } }
        }
        composeRule.onNodeWithText("1時台").assertIsSelected()
        composeRule.onNodeWithText("2時台").performClick().assertIsSelected()
        composeRule.onNodeWithText("1時台").assertIsNotSelected()
        composeRule.onNodeWithText("すべて").performClick().assertIsSelected()
        composeRule.onNodeWithText("2時台").assertIsNotSelected()
    }
}
