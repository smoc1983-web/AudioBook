package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookBindingSplashTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsTheBookAndHandsOffOnceWithoutAPostAnimationDelay() {
        compose.mainClock.autoAdvance = false
        var finished = 0
        compose.setContent {
            BookBindingSplash(abTheme(2)) { finished++ }
        }

        compose.onNodeWithText("Audiobook").assertExists()
        compose.onNodeWithContentDescription("Переплёт книги").assertExists()
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertEquals(0, finished) }
        compose.mainClock.advanceTimeBy(288)
        compose.runOnIdle { assertEquals(1, finished) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun readsTheSavedThemeIncludingGraphiteAndLightThemes() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefs = base.getSharedPreferences("binding-launch-theme-test", Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
        try {
            prefs.edit().putInt("glass_theme", 10).commit()
            assertTrue(bookLaunchTheme(context).flat)
            prefs.edit().putInt("glass_theme", 1).commit()
            assertTrue(bookLaunchTheme(context).light)
            prefs.edit().putString("glass_theme", "broken").commit()
            assertEquals("Ледяная ночь", bookLaunchTheme(context).name)
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
