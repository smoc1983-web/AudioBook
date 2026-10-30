package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionSeriesTest {
    @get:Rule val compose=createComposeRule()
    @Test(timeout=30000) fun continuationButtonExpandsInlineWithoutOpeningPlayerAndCanCollapse() {
        val ref=BookRef("Отель Перекресток","","")
        var expanded by mutableStateOf(false);var opens=0;var menus=0
        val before=PlaybackService.state.value
        compose.setContent {Column {
            AbCollectionBookCard(ref,null,abTheme(2),expanded,{opens++},{menus++},{expanded=!expanded})
            if(expanded) androidx.compose.material3.Text("Отель Перекресток. Том 2")
        }}
        compose.onNodeWithText("Продолжения").performClick()
        compose.onNodeWithText("Отель Перекресток. Том 2").assertIsDisplayed()
        assertEquals(0,opens);assertEquals(before,PlaybackService.state.value)
        compose.onNodeWithText("Скрыть").performClick()
        compose.onNodeWithText("Отель Перекресток. Том 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Действия: Отель Перекресток").performClick()
        assertEquals(1,menus);assertEquals(0,opens)
        compose.onNodeWithText("Отель Перекресток",substring=false).performClick()
        assertEquals(1,opens)
    }

    @Test(timeout=30000) fun shelfShowsCachedTextAudioAndOwnedVolumeInlineButNeverUnrelatedFeed() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("collection-inline-test",0)
        val store=ContinuationStore(context)
        val official=OfficialSeriesStore(context)
        val title="Отель Перекресток";val next="Отель Перекресток. Том 2";val author="Андрей Васильев"
        val ref=BookRef(title,"https://bookinfo.net/author/vasiliev/hotel","")
        val now=System.currentTimeMillis();var opens=0
        val old=prefs.all
        val oldAvailable=store.preferences.getString("available",null)
        val muted=store.preferences.getStringSet("muted_titles",null)?.toSet()
        val sourceNext="https://bookinfo.net/fantastika/andrey-vasiliev/hotel-2"
        try {
            prefs.edit().clear().putString("shelves_v1",JSONArray().put(JSONObject().put("n","Отель").put("b",
                JSONArray().put(JSONObject().put("t",title).put("u",ref.url).put("c","")))) .toString()).commit()
            abRememberCatalog(prefs,BookLink(title,ref.url,author=author))
            abRememberCatalog(prefs,BookLink(next,sourceNext,author=author))
            SeriesAuthorityCache.save(prefs,title,author,SeriesEvidence(title,author,"Отель Перекресток",
                "https://author.today/work/series/990011","https://author.today/work/990011","Author.Today",1.0),"confirmed")
            val second=OfficialEdition("Официальное продолжение","https://author.today/work/990012",author,"Отель Перекресток","Author.Today","text","published",2.0)
            official.save(OfficialSnapshot(title,author,listOf(
                OfficialSource("Author.Today","Отель Перекресток","https://author.today/work/series/990011",1.0,now,now,true,"",listOf(second)),
                OfficialSource("Литрес","Отель Перекресток","https://www.litres.ru/series/hotel-990011/",1.0,now,now,true,"",listOf(second.copy(url="https://www.litres.ru/audiobook/vasiliev/next-990012/",provider="Литрес",format="audio")))
            )),author)
            val seed=ContinuationStore.hash(ref.url)
            store.preferences.edit().putStringSet("muted_titles",setOf(SeriesAuthorityRules.title(title)))
                .putString("available",JSONArray().put(ContinuationNotice("own",seed,"Книга из аудиоисточника",sourceNext,"Отель Перекресток",author,now).json())
                    .put(ContinuationNotice("wrong-cycle",seed,"Другой цикл того же автора",sourceNext,"Хранители кладов",author,now).json())
                    .put(ContinuationNotice("wrong-author",seed,"Однофамилец",sourceNext,"Отель Перекресток","Другой Автор",now).json())
                    .put(ContinuationNotice("wrong","litres","Чужая новинка","https://bookinfo.net/author/other/unrelated","Чужой цикл","Другой автор",now).json()).toString()).commit()
            val books=listOf(LibraryBook(title,File("/unused/hotel"),emptyList()),LibraryBook(next,File("/unused/hotel2"),emptyList()),
                LibraryBook("Чужая книга",File("/unused/other"),emptyList()))
            val before=PlaybackService.state.value
            compose.setContent {Column(Modifier.fillMaxSize().testTag("collection-scroll").verticalScroll(rememberScrollState())) {
                AbCollectionShelf(Shelf("Отель",listOf(ref)),books,prefs,0,abTheme(2),{opens++},{},{},{},{},{})
            }}
            compose.onNodeWithText("Официальное продолжение").assertDoesNotExist()
            compose.onNodeWithText("Продолжения").performScrollTo().performClick()
            compose.waitUntil(5000){compose.onAllNodesWithText("Официальное продолжение").fetchSemanticsNodes().size==1}
            compose.onNodeWithText(next+" · на устройстве").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Книга из аудиоисточника").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Официальное продолжение").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Открыть · Author.Today · Текст").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Открыть · Литрес · Аудио").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Чужая новинка").assertDoesNotExist()
            compose.onNodeWithText("Другой цикл того же автора").assertDoesNotExist()
            compose.onNodeWithText("Однофамилец").assertDoesNotExist()
            compose.onNodeWithText("Чужая книга · на устройстве").assertDoesNotExist()
            assertEquals(0,opens);assertEquals(before,PlaybackService.state.value)
            compose.onNodeWithTag("collection-scroll").performScrollToNode(hasText("Скрыть"))
            compose.onNodeWithText("Скрыть").performClick()
            compose.onNodeWithText("Официальное продолжение").assertDoesNotExist()
        } finally {
            prefs.edit().clear().commit()
            check(old.isEmpty()) {"Fixture preferences unexpectedly contained user values"}
            val restore=store.preferences.edit()
            if(oldAvailable==null)restore.remove("available") else restore.putString("available",oldAvailable)
            if(muted==null)restore.remove("muted_titles") else restore.putStringSet("muted_titles",muted)
            restore.commit()
        }
    }
    @Test(timeout=30000) fun collectionMenuUsesNamedActionsWithoutDeletingAudioOrOpeningPlayer() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("collection-menu-test",0)
        var renames=0;var deletes=0;var opens=0
        compose.setContent {AbCollectionShelf(Shelf("Моя полка",emptyList()),emptyList(),prefs,0,abTheme(2),{opens++},{},{},{},{renames++},{deletes++})}
        compose.onNodeWithContentDescription("Меню коллекции: Моя полка").performClick()
        compose.onNodeWithText("Переименовать").performClick()
        assertEquals(1,renames);assertEquals(0,deletes);assertEquals(0,opens)
        compose.onNodeWithContentDescription("Меню коллекции: Моя полка").performClick()
        compose.onNodeWithText("Удалить коллекцию").performClick()
        assertEquals(1,deletes);assertEquals(0,opens)
    }
    @Test(timeout=30000) fun collectionDoesNotHideBooksAfterTheFifthAndScrollDoesNotOpenPlayer() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("collection-scroll-test",0)
        val refs=(1..12).map{BookRef("Тестовая книга $it","","")};var opens=0
        compose.setContent {AbCollectionShelf(Shelf("Длинная полка",refs),emptyList(),prefs,0,abTheme(2),{opens++},{},{},{},{},{})}
        compose.onNodeWithContentDescription("Книги коллекции: Длинная полка").performScrollToIndex(11)
        compose.onNodeWithText("Тестовая книга 12").assertIsDisplayed()
        compose.onNodeWithText("Продолжения · Тестовая книга 12").assertDoesNotExist()
        assertEquals(0,opens)
    }
}
