package com.audiobookdownloader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MiniPlayerSwipeTest {
    @get:Rule val compose=createComposeRule()
    @Test fun leftAndRightSwipeDismissWithoutInvokingPlaybackButton() {
        var visible by mutableStateOf(true)
        var clicks=0
        var dismissed=0
        compose.setContent {
            if(visible)AbSwipeMiniPlayer(onDismiss={dismissed++;visible=false}) {
                Box(Modifier.fillMaxWidth().height(72.dp)) {
                    Button(onClick={clicks++}){Text("Воспроизведение")}
                }
            }
        }
        compose.onNodeWithText("Воспроизведение").performClick()
        compose.runOnIdle {assertEquals(1,clicks);assertEquals(0,dismissed)}
        compose.onNodeWithTag("search-mini-player").performTouchInput {swipeLeft()}
        compose.onNodeWithTag("search-mini-player").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,dismissed);assertEquals(1,clicks);visible=true}
        compose.onNodeWithTag("search-mini-player").performTouchInput {swipeRight()}
        compose.onNodeWithTag("search-mini-player").assertDoesNotExist()
        compose.runOnIdle {assertEquals(2,dismissed);assertEquals(1,clicks)}
    }
}
