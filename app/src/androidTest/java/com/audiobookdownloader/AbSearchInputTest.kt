package com.audiobookdownloader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AbSearchInputTest {
    @Test fun recreationKeepsTheQueryAndAnExplicitAllSourcesSelection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var before: AbSearchInputModel
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[AbSearchInputModel::class.java]
                before.query = "Файролл 8"
                before.source = ""
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[AbSearchInputModel::class.java]
                assertSame(before, after)
                assertEquals("Файролл 8", after.query)
                assertEquals("", after.source)
            }
        }
    }

    @Test fun restoredSavedStateTakesPrecedenceOverTheDefaultSource() {
        val saved = SavedStateHandle()
        AbSearchInputModel(saved).apply {
            initializeSource("Поле книг")
            assertEquals("Поле книг", source)
            query = "Путь строителя"
            source = ""
        }
        val restored = AbSearchInputModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        restored.initializeSource("Другой источник")
        assertEquals("Путь строителя", restored.query)
        assertEquals("", restored.source)
    }
}
