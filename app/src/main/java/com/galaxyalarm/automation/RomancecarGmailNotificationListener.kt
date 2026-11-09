package com.galaxyalarm.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Optional, opt-in wake-up signal for Romancecar sync.
 *
 * Gmail notifications only trigger a fetch from the user's own Apps Script.
 * This class intentionally never reads, stores, logs or uploads the text,
 * sender, subject or other content of any notification.
 *
 * The system permission still allows a listener to observe all notifications.
 * Users must explicitly grant this permission in Android settings.
 */
class RomancecarGmailNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        requestSync()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != GMAIL_PACKAGE) return
        requestSync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName != GMAIL_PACKAGE) return
        requestSync()
    }

    private fun requestSync() {
        val context = applicationContext
        if (!RomancecarDirectSyncSettings.isInstantSyncEnabled(context)) return
        if (!RomancecarDirectSyncSettings.validUrl(RomancecarDirectSyncSettings.readUrl(context))) return

        try {
            RomancecarDirectSyncWorker.syncAfterNotificationBurst(context)
            RomancecarDirectSyncWorker.syncNow(context,
                expedited = RomancecarDirectSyncSettings.claimNotificationSync(context))
        } catch (e: Exception) {
            // No email text or notification metadata is logged.
            Log.w(TAG, "Could not request Gmail-triggered sync", e)
        }
    }

    private companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val TAG = "RomancecarGmailListener"
    }
}
