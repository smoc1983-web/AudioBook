package com.audiobookdownloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage8FolderPanelStateTest {
    @get:Rule val compose=createComposeRule()
    @Test fun yandexSlotSelectionSurvivesSavedStateRestoreWithoutStartingAnyTransfer() {
        val state=StateRestorationTester(compose)
        state.setContent{Column(Modifier.verticalScroll(rememberScrollState())){AbProgressFolderPanel(abTheme(10),false){}}}
        compose.onNodeWithText("Яндекс Диск").performClick()
        compose.onNodeWithText("Выбрать папку Яндекс Диска").assertExists()
        state.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Выбрать папку Яндекс Диска").assertExists()
        compose.onNodeWithText("Выбрать общую папку").assertDoesNotExist()
    }
}
