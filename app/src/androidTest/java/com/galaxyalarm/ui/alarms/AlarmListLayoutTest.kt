package com.galaxyalarm.ui.alarms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import org.junit.Rule
import org.junit.Test

class AlarmListLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerAndAddActionStayVisibleAfterScrollingToEnd() {
        composeRule.setContent {
            GalaxyAlarmTheme {
                AlarmListLayout(
                    header = {
                        AlarmListHeader(
                            title = "アラーム",
                            subtitle = "グループなしのアラーム",
                            allEnabled = false,
                            showEnableSwitch = false,
                            onToggleAll = {},
                            onAddAlarm = {},
                        )
                    },
                ) {
                    items((1..40).toList()) { index ->
                        Text(
                            text = "テストアラーム $index",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("alarm_scroll_content")
            .performScrollToNode(hasText("テストアラーム 40"))

        composeRule.onNodeWithTag("alarm_fixed_header").assertIsDisplayed()
        composeRule.onNodeWithText("アラーム").assertIsDisplayed()
        composeRule.onNodeWithText("+ 追加").assertIsDisplayed()
    }
}
