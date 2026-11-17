package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import android.provider.Settings

// Extracted unchanged selection/launch behavior to reproduce the compatibility bug.
internal fun openAppNotificationSettings(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    true
} catch (_: Exception) {
    false
}
