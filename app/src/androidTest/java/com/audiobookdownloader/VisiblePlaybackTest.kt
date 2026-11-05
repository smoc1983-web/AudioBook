package com.audiobookdownloader

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisiblePlaybackTest {
    @get:Rule val compose=createComposeRule()
    @Test fun hiddenScreenStopsCollectingAndReturnsToCurrentPosition() {
        val service=MutableStateFlow(ServicePlaybackState(bookTitle="Книга",positionMs=1000))
        var visible by mutableStateOf(true)
        var screen=ServicePlaybackState()
        compose.setContent {screen=abVisiblePlayback(visible,service)}
        compose.waitUntil {service.subscriptionCount.value==1}
        compose.runOnIdle{assertEquals(1000,screen.positionMs);visible=false}
        compose.waitUntil {service.subscriptionCount.value==0}
        compose.runOnIdle {service.value=service.value.copy(positionMs=9000,isPlaying=true)}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(1000,screen.positionMs);assertEquals(9000,service.value.positionMs);visible=true}
        compose.waitUntil {screen.positionMs==9000}
        compose.runOnIdle {assertTrue(screen.isPlaying)}
    }
}
