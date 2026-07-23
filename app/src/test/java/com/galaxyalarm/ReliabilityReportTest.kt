package com.galaxyalarm

import com.galaxyalarm.reliability.CheckItem
import com.galaxyalarm.reliability.ReliabilityReport
import com.galaxyalarm.reliability.TITLE_FULL_SCREEN
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityReportTest {
    @Test fun disabledFullScreenAlertIsCritical() {
        val report = ReliabilityReport(
            items = listOf(CheckItem(TITLE_FULL_SCREEN, false, "OFF")),
            lastCheckAt = 0L,
            lastRepairAt = 0L,
            lastRepairResult = "",
            lastEventLogSummary = "",
        )

        assertTrue(report.hasCritical)
    }
}
