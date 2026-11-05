package com.audiobookdownloader
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackOptionsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun buttonsUseSelectedStepAndStopAtFourTimes() {
        var speed by mutableStateOf(1f)
        var fine by mutableStateOf(true)
        compose.setContent{AbSpeedControls(speed,fine,abTheme(2),{speed=it},{fine=it})}
        compose.onNodeWithText("+").performClick()
        compose.runOnIdle{assertEquals(1.05f,speed,0f)}
        compose.onNodeWithText("Шаг 0,1×").performClick()
        compose.onNodeWithText("−").performClick()
        compose.runOnIdle{assertEquals(.95f,speed,0f);assertFalse(fine)}
        compose.onNodeWithText("4.0×").performClick()
        compose.onNodeWithText("+").assertIsNotEnabled()
        compose.runOnIdle{assertEquals(4f,speed,0f)}
    }
}
