package com.audiobookdownloader

import android.webkit.WebSettings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewIsolationTest {
    @Test(timeout=30000) fun sourceBrowserCannotReadLocalFilesOrContentProviders() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                val browser=abCreateBrowser(activity,{fail(it)}) {settings.javaScriptEnabled=true}
                assertNotNull(browser)
                val settings=browser!!.settings
                assertFalse(settings.allowFileAccess)
                assertFalse(settings.allowContentAccess)
                assertFalse(settings.allowFileAccessFromFileURLs)
                assertFalse(settings.allowUniversalAccessFromFileURLs)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,settings.mixedContentMode)
                assertTrue("Network source parsing still needs JavaScript",settings.javaScriptEnabled)
            }
        }
    }
}
