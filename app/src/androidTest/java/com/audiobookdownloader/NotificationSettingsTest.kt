package com.audiobookdownloader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsTest {
    private class LaunchContext(val reject: (Int) -> Unit = {}) :
        ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempts += Intent(intent)
            reject(attempts.size)
        }
    }
    private fun assertDetails(intent: Intent, pkg: String) {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.action)
        assertEquals("package:$pkg",intent.dataString)
    }
    @Test fun selectsSettingsSupportedByThisAndroidAndTargetsOnlyOurPackage() {
        val context=LaunchContext()
        assertTrue(openAppNotificationSettings(context))
        val intent=context.attempts.single()
        if(Build.VERSION.SDK_INT>=26) {
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS,intent.action)
            assertEquals(context.packageName,intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        } else assertDetails(intent,context.packageName)
    }
    @Test fun unavailableNotificationScreenFallsBackToAppDetails() {
        val context=LaunchContext{if(it==1)throw ActivityNotFoundException("No notification screen")}
        val opened=openAppNotificationSettings(context)
        if(Build.VERSION.SDK_INT>=26) {
            assertTrue(opened);assertEquals(2,context.attempts.size)
            assertDetails(context.attempts.last(),context.packageName)
        } else {
            assertFalse(opened);assertEquals(1,context.attempts.size)
        }
    }
    @Test fun restrictedNotificationScreenAlsoFallsBack() {
        val context=LaunchContext{if(it==1)throw SecurityException("Restricted notification screen")}
        val opened=openAppNotificationSettings(context)
        if(Build.VERSION.SDK_INT>=26) {
            assertTrue(opened);assertEquals(2,context.attempts.size)
            assertDetails(context.attempts.last(),context.packageName)
        } else {
            assertFalse(opened);assertEquals(1,context.attempts.size)
        }
    }
    @Test fun absenceOfBothSettingsScreensReturnsFailureWithoutLooping() {
        val context=LaunchContext{throw ActivityNotFoundException("No settings screen")}
        assertFalse(openAppNotificationSettings(context))
        assertEquals(if(Build.VERSION.SDK_INT>=26)2 else 1,context.attempts.size)
        assertDetails(context.attempts.last(),context.packageName)
    }
}
