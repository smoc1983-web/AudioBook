package com.audiobookdownloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SourceHealthPanelTest {
    @get:Rule val compose=createComposeRule()
    private val a=SourceHealthTarget("Bookinfo","https://bookinfo.net/")
    private val b=SourceHealthTarget("Азбука веры","https://azbyka.ru/audio/")
    private class Memory:SourceHealthStore {
        val rows=ConcurrentHashMap<String,SourceHealthObservation>()
        override fun get(target:SourceHealthTarget)=rows[target.key]
        override fun put(target:SourceHealthTarget,value:SourceHealthObservation){rows[target.key]=value}
    }
    @Test fun openingDoesNotRequestSitesAndManualRefreshOnlyChecksSelectedSourcesWithCooldown() {
        val store=Memory();val calls=AtomicInteger()
        val session=SourceHealthSession(store,SourceHealthClient{assertEquals(a,it);calls.incrementAndGet();SourceHealthObservation(SourceHealthRules.Status.RESPONDED,System.currentTimeMillis(),12,200)})
        compose.setContent{Column(Modifier.verticalScroll(rememberScrollState())){
            val view=AbSourceHealthControls(listOf(a,b),setOf(a.name),abTheme(10),session)
            AbSourceHealthStatus(a.name,view,abTheme(10));AbSourceHealthStatus(b.name,view,abTheme(10))
        }}
        compose.waitUntil(5000){!session.state.value.loading};assertEquals(0,calls.get())
        compose.onNodeWithText("Проверить включённые источники").performScrollTo().performClick()
        compose.waitUntil(5000){!session.state.value.running&&session.state.value.done==1}
        compose.onNodeWithText("Сайт ответил",substring=true).assertExists()
        compose.onAllNodesWithText("Сайт не проверен").assertCountEquals(1)
        compose.onNodeWithText("Проверить включённые источники").performScrollTo().performClick()
        compose.waitUntil(5000){!session.state.value.running&&session.state.value.cached==1}
        assertEquals(1,calls.get());assertFalse(store.rows.containsKey(b.key))
    }
    @Test fun staleAndRestrictedStatesHaveReadableTextWithoutRelyingOnColour() {
        val state=SourceHealthState(values=mapOf(a.key to SourceHealthObservation(SourceHealthRules.Status.RESPONDED,100000,12,200),
            b.key to SourceHealthObservation(SourceHealthRules.Status.RESTRICTED,100000+SourceHealthRules.TTL,45,403)),loading=false)
        val view=SourceHealthView(state,mapOf(a.name to a,b.name to b),100000+SourceHealthRules.TTL)
        compose.setContent{Column{AbSourceHealthStatus(a.name,view,abTheme(10));AbSourceHealthStatus(b.name,view,abTheme(10))}}
        compose.onNodeWithText("Устарело · Сайт ответил",substring=true).assertExists()
        compose.onNodeWithText("Доступ ограничен / нужна проверка · HTTP 403").assertExists()
    }
    @Test fun cancelButtonAndClosingPanelCancelRequestsWithoutLateCacheWritesOrBusyState() {
        val store=Memory();val calls=AtomicInteger()
        val session=SourceHealthSession(store,SourceHealthClient{calls.incrementAndGet();awaitCancellation()})
        var open by mutableStateOf(true)
        compose.setContent{if(open)Column(Modifier.verticalScroll(rememberScrollState())){AbSourceHealthControls(listOf(a),setOf(a.name),abTheme(10),session)}}
        compose.waitUntil(5000){!session.state.value.loading}
        compose.onNodeWithText("Проверить включённые источники").performScrollTo().performClick()
        compose.waitUntil(5000){session.state.value.active.isNotEmpty()}
        compose.onNodeWithText("Отменить проверку сайтов").performScrollTo().performClick()
        compose.waitUntil(5000){!session.state.value.running}
        compose.onNodeWithText("Проверка отменена",substring=true).assertExists()
        compose.onNodeWithText("Проверить включённые источники").performScrollTo().performClick()
        compose.waitUntil(5000){calls.get()==2&&session.state.value.active.isNotEmpty()}
        compose.runOnIdle{open=false}
        compose.waitUntil(5000){!session.state.value.running}
        assertTrue(store.rows.isEmpty());assertTrue(session.state.value.active.isEmpty())
    }
}
