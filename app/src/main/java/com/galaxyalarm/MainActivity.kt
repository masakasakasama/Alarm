package com.galaxyalarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.galaxyalarm.ui.AppNavigation
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            GalaxyAlarmTheme {
                Surface(Modifier.fillMaxSize()) { AppNavigation() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 権限が変わっている可能性があるため、戻ってきたら再チェック&再スケジュール。
        val app = application as AlarmApplication
        val container = app.container
        if (container.permissions.canScheduleExactAlarms()) {
            app.appScope.launch {
                container.repository.rescheduleAll("activity-resume")
                container.reliabilityChecker.runCheck()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
