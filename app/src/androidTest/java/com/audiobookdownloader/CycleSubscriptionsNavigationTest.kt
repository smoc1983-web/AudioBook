package com.audiobookdownloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run against the actual Android activity in the emulator.
 * Prevents a unit-only green check from approving broken bottom navigation or subscriptions.
 */
@RunWith(AndroidJUnit4::class)
class CycleSubscriptionsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 120000)
    fun settingsAndSubscriptionsAreReachableFromBottomNavigation() {
        compose.waitUntil(60000) {
            compose.onAllNodesWithText("Настройки", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Настройки", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Выберите блок настроек", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Коллекции", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Мои коллекции", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Подписки на циклы", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Мои подписки на циклы", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Автоматическое отслеживание коллекций", useUnmergedTree = true).assertExists()
    }
}
