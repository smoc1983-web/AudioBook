package com.audiobookdownloader
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesRecoveryTest {
 @Test(timeout=60000) fun legacyOriginRecoveryShowsExistingSecondVolumeAndExcludesAnotherDirectSeries()=runBlocking {
    val context=ApplicationProvider.getApplicationContext<Context>();val prefs=context.getSharedPreferences("audiobook_downloader",0)
    val store=ContinuationStore(context);val original=prefs.getString("shelves_v1",null)
    val one="https://knigoblud.club/11111111-1111-1111-1111-111111111111";val two="https://knigoblud.club/22222222-2222-2222-2222-222222222222"
    val other="https://knigoblud.club/33333333-3333-3333-3333-333333333333";val third="https://knigoblud.club/44444444-4444-4444-4444-444444444444"
    val cycle="https://author.today/work/series/999991";var latest=2;var broken=false
    val titles=mapOf(one to "Отель Перекресток",two to "Отель Перекресток. Том 2",third to "Отель Перекресток. Том 3",other to "Золото мертвых")
    fun kbItem(url:String,index:Int)="<a class='BookDescriptionSeriesItem' href='$url'><span class='BookDescriptionSeriesItemIndex'>$index.</span><span class='BookDescriptionSeriesItemName'>${titles[url]}</span></a>"
    val fake=ContinuationFetcher{url->if(broken)error("offline");when {
        url.startsWith("https://knigoblud.club/search?")->"<div class='bookListItem'><a class='bookListItemCover' href='$one'><span class='bookListItemCoverNameText'>Отель Перекресток</span></a><div class='bookListItemMetaBlock'>✍️ <a href='/080dde46-8ccc-4df1-8bd8-cfc1c04aed99'>Андрей Васильев</a></div></div>"
        url==cycle->"<h1>Отель Перекресток</h1>"+(1..latest).joinToString(""){i->"<div class='book-row-content'><div class='book-title'><span class='label-row-index'>$i</span><a href='/work/${999990+i}'>${if(i==1)titles[one] else if(i==2)titles[two] else titles[third]}</a></div><div class='book-author'><a href='/080dde46-8ccc-4df1-8bd8-cfc1c04aed99'>Андрей Васильев</a></div></div>"}
        titles.containsKey(url)->"<h1>${titles[url]}</h1><span itemprop='author'>Андрей Васильев</span><div class='BookMetaBlockLine'>✍️ <a href='/080dde46-8ccc-4df1-8bd8-cfc1c04aed99'>Андрей Васильев</a></div><div class='BookDescription BookSeries'><div class='BookDescriptionLabel'>📚 Цикл <a href='https://knigoblud.club/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'>Общий мир</a></div>"+kbItem(one,1)+kbItem(two,2)+kbItem(other,2)+(if(latest==3)kbItem(third,3) else "")+"</div><script>KB.playerInit({});</script>"
        else->error("Unexpected metadata request: $url")}}
    try {
        store.preferences.edit().clear().putBoolean("enabled",true).commit();java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
        prefs.edit().putString("shelves_v1","""[{"n":"Regression","b":[{"t":"Отель Перекресток","u":"","c":""}]}]""").commit()
        prefs.edit().remove(bookUrlKey("Отель Перекресток")).commit()
        SeriesAuthorityCache.save(prefs,"Отель Перекресток","Андрей Васильев",SeriesEvidence("Отель Перекресток","Андрей Васильев","Отель Перекресток",cycle,"https://author.today/work/999991","Author.Today",1.0),"confirmed")
        val initialStatus=AbContinuationAlerts.check(context,fake);assertTrue(initialStatus,initialStatus.contains("Проверено циклов: 1"))
        assertEquals(setOf("Отель Перекресток","Отель Перекресток. Том 2"),store.available().map{it.title}.toSet())
        assertTrue("Already released second volume is visible without a false new-release alert",store.notices().isEmpty())
        broken=true;AbContinuationAlerts.check(context,fake);assertEquals(2,store.available().size)
        broken=false;latest=3;AbContinuationAlerts.check(context,fake);assertEquals("Отель Перекресток. Том 3",store.notices().single().title)
        AbContinuationAlerts.check(context,fake);assertEquals(1,store.notices().size)
        store.preferences.edit().putStringSet("muted_titles",setOf(SeriesAuthorityRules.title("Отель Перекресток"))).commit();assertTrue(store.seeds().isEmpty())
    }finally{if(original==null)prefs.edit().remove("shelves_v1").commit() else prefs.edit().putString("shelves_v1",original).commit();store.preferences.edit().clear().commit();java.io.File(context.filesDir,"continuations-v1").deleteRecursively()}
 }
 @Test(timeout=60000) fun explicitSourceCycleWorksWhenReferenceSearchHasNoMatch()=runBlocking {
    val context=ApplicationProvider.getApplicationContext<Context>();val prefs=context.getSharedPreferences("audiobook_downloader",0);val store=ContinuationStore(context)
    val original=prefs.getString("shelves_v1",null);val title="Гостиница тестового цикла"
    val one="https://knigoblud.club/55555555-5555-5555-5555-555555555555";val two="https://knigoblud.club/66666666-6666-6666-6666-666666666666"
    val cycle="https://knigoblud.club/77777777-7777-7777-7777-777777777777"
    val fake=ContinuationFetcher{url->if(url==one||url==two){"<h1>"+title+(if(url==two)". Том 2" else "")+"</h1><span itemprop='author'>Тест Автор</span><div class='BookMetaBlockLine'>✍️ <a href='/88888888-8888-8888-8888-888888888888'>Тест Автор</a></div><div class='BookDescription BookSeries'><div class='BookDescriptionLabel'>📚 Цикл <a href='$cycle'>$title</a></div><a class='BookDescriptionSeriesItem' href='$one'><span class='BookDescriptionSeriesItemIndex'>1.</span><b>$title</b></a><a class='BookDescriptionSeriesItem' href='$two'><span class='BookDescriptionSeriesItemIndex'>2.</span><span class='BookDescriptionSeriesItemName'>$title. Том 2</span></a></div><script>KB.playerInit({});</script>"}else "<h1>Поиск</h1>"}
    try{store.preferences.edit().clear().putBoolean("enabled",true).commit();java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
        prefs.edit().putString("shelves_v1","""[{"n":"Test","b":[{"t":"$title","u":"$one","c":""}]}]""").commit()
        val seed=store.seeds().single();assertEquals(1,SeriesContinuationRecovery(context,fake).checkKnigoblud(seed,one).first)
        assertEquals(2,store.available(seed.id).size);assertTrue(store.notices().isEmpty());assertEquals(cycle,abStoredCatalog(prefs,title).cycles.single().url)
    }finally{if(original==null)prefs.edit().remove("shelves_v1").commit() else prefs.edit().putString("shelves_v1",original).commit();store.preferences.edit().clear().commit();java.io.File(context.filesDir,"continuations-v1").deleteRecursively()}
 }
}
