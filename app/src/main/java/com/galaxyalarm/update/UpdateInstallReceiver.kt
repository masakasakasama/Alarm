package com.galaxyalarm.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    Log.e(TAG, "update session $sessionId has no confirmation intent")
                    return
                }
                runCatching {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    Log.e(TAG, "failed to open update confirmation for session $sessionId", it)
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                Log.i(TAG, "update session $sessionId completed")

            else -> Log.e(
                TAG,
                "update session $sessionId failed: status=$status, " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            )
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.galaxyalarm.extra.UPDATE_SESSION_ID"
        private const val TAG = "UpdateInstallReceiver"
    }
}
