package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AbCatalogIndexTest {
    @Test fun russianMetadataAndIndependentNarrationsSurviveReopening() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="catalog-test-"+UUID.randomUUID()+".db"
        val now=System.currentTimeMillis()
        val a=BookLink("Ёжик. Том II","https://akniga.org/ezhik-2",author="Андрей Васильев",
            narrator="Иван Петров",cycles=listOf(BookCycle("Лесные истории",order=2.0)),genres=listOf("Фантастика"))
        val b=a.copy(url="https://akniga.org/ezhik-2-other",narrator="Пётр Иванов")
        try {
            AbCatalogIndex(context,name).use {db->
                db.record(listOf(a,b),now,now)
                db.record(listOf(a.copy(narrator="Устаревшая запись")),now-1000,now)
                db.markImported()
            }
            AbCatalogIndex(context,name).use {db->
                val actual=bookSiteName(a.url)
                assertTrue(actual.isNotBlank())
                assertEquals(setOf(a.url,b.url),db.find("ежик том 2",setOf(actual),now=now).map{it.url}.toSet())
                assertEquals(listOf(a.url),db.find("Петров",setOf(actual),now=now).map{it.url})
                assertEquals(2,db.find("Лесные истории",setOf(actual),now=now).size)
                assertTrue(db.find("ежик",setOf("another site"),now=now).isEmpty())
                assertTrue(db.find("устаревшая",setOf(actual),now=now).isEmpty())
                assertTrue(db.find("ежик",setOf(actual),now=now+AbCatalogIndex.MAX_AGE+1).isEmpty())
                assertTrue(db.imported())
            }
        }finally{context.deleteDatabase(name)}
    }

    @Test fun thinDiscoveryCardDoesNotErasePreviouslyObservedAuthorOrNarration() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="catalog-test-"+UUID.randomUUID()+".db"
        val now=System.currentTimeMillis()
        val full=BookLink("Архил. Книга 4","https://akniga.org/fixture-arhil",
            author="Павел Кожевников",narrator="Иван Петров",cycles=listOf(BookCycle("Архил...?")))
        try {
            AbCatalogIndex(context,name).use{db->
                db.record(listOf(full),now-1,now)
                db.record(listOf(full.copy(author="",narrator="",cycles=emptyList())),now,now)
                val found=db.find("Архил",setOf(bookSiteName(full.url)),now=now).single()
                assertEquals(full.author,found.author)
                assertEquals(full.narrator,found.narrator)
                assertEquals(full.cycles,found.cycles)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun upsertReplacesTermsAndRejectsUnsupportedAddresses() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="catalog-test-"+UUID.randomUUID()+".db"
        val now=System.currentTimeMillis()
        val a=BookLink("Первая книга","https://akniga.org/book-one",narrator="Старый Чтец")
        try{
            AbCatalogIndex(context,name).use{db->
                val site=setOf(bookSiteName(a.url))
                db.record(listOf(a),now-1,now)
                db.record(listOf(a.copy(narrator="Новый Чтец"),a.copy(url="https://unconfigured.invalid/book")),now,now)
                assertTrue(db.find("Старый",site,now=now).isEmpty())
                assertEquals(listOf(a.url),db.find("Новый",site,now=now).map{it.url})
                assertEquals(1,db.find("Первая",site,now=now).size)
                assertTrue(db.find("\" OR *",site,now=now).isEmpty())
            }
        }finally{context.deleteDatabase(name)}
    }
}
