package com.audiobookdownloader

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SearchLabReliabilityTest {
    companion object {
        @BeforeClass @JvmStatic fun allowMediaAndNotifications() {
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            val app=instrumentation.targetContext.packageName
            if(android.os.Build.VERSION.SDK_INT>=33) {
                instrumentation.uiAutomation.grantRuntimePermission(app,"android.permission.READ_MEDIA_AUDIO")
                instrumentation.uiAutomation.grantRuntimePermission(app,"android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun typingKeepsTheSameFocusedSearchField() {
        val description="Название, автор или ссылка"
        val field=compose.onNodeWithContentDescription(description)
        compose.waitUntil(timeoutMillis=15000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        field.performClick().assertIsFocused()
        field.performTextInput("файролл.")
        field.assertIsFocused()
        field.performTextInput("слово и сталь")
        field.assertIsFocused().assertTextContains("файролл.слово и сталь")
    }

    @Test fun aSearchBrowserStillRepliesWhileTheActivityIsStopped() {
        val ready=CountDownLatch(1)
        val reply=CountDownLatch(1)
        val response=AtomicReference<String>()
        lateinit var worker:WebView
        compose.activityRule.scenario.onActivity { activity ->
            worker=checkNotNull(abCreateBrowser(activity,{},keepSearch=true) {
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient() {
                    override fun onPageFinished(view:WebView?,url:String?) {
                        ready.countDown()
                    }
                }
                loadDataWithBaseURL("https://poleknig.com/","<html><body>ready</body></html>","text/html","UTF-8",null)
            })
        }
        assertTrue("The search browser loaded its document",ready.await(8,TimeUnit.SECONDS))
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            worker.evaluateJavascript("document.body.textContent") { value ->
                response.set(value)
                reply.countDown()
            }
        }
        assertTrue("The result arrived before reopening the app",reply.await(8,TimeUnit.SECONDS))
        assertEquals("\"ready\"",response.get())
        assertEquals(Lifecycle.State.CREATED,compose.activityRule.scenario.state)
    }
}
