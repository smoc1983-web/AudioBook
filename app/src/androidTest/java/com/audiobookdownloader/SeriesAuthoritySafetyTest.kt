package com.audiobookdownloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesAuthoritySafetyTest {
    @Test(timeout=15000) fun differentAuthorBeyondFirstThreeResultsStillMakesUnknownAuthorAmbiguous()=runBlocking {
        var details=0
        val fetcher=SeriesAuthorityFetcher {url->
            if(url.contains("/search"))(1..4).joinToString("") {i->
                "<div class='work-row-content'><div class='work-title'><a href='/work/"+i+"'>Одинаковое название</a></div><div class='work-author'>"+(if(i==4)"Другой Автор" else "Тест Автор")+"</div></div>"
            } else {
                details++
                "<h1>Одинаковое название</h1><div class='work-header-content'><div class='card-author'><a>Тест Автор</a></div><div><a href='/work/series/7'>Путь</a> #1</div></div>"
            }
        }
        val result=SeriesAuthorityResolver(fetcher).resolveFrom("Author.Today","Одинаковое название","")
        assertNull(result.first);assertEquals("ambiguous",result.second)
        assertEquals("Do not verify a guessed author from a truncated candidate list",0,details)
    }
}
