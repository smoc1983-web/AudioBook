package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AdditionalBookSourcesTest {
    @Test fun registryPreservesDefaultsAndAddsOptInSources() {
        assertEquals(24,AbExtraSources.names.size)
        assertEquals(24,AbExtraSources.names.toSet().size)
        assertEquals(10,AbExtraSources.defaults.size)
        for(name in AbExtraSources.names){assertEquals(name,AbExtraSources.name(AbExtraSources.home(name)));assertTrue(AbExtraSources.status(name).isNotBlank())}
        for((name,url) in listOf("Lis10Book" to "https://lis10book.com/", "Слушкин Всем" to "https://slushkinvsem.ru/", "Audio-Lib" to "https://audio-lib.club/")) {
            assertFalse(name in AbExtraSources.names)
            assertEquals("",AbExtraSources.home(name))
            assertEquals("",AbExtraSources.name(url))
            assertFalse(AbExtraSources.book(url+"book/test/"))
        }
    }
    @Test fun urlIdentityRejectsCredentialsImpostorsAndOtherPorts() {
        for(name in AbExtraSources.names){val host=java.net.URI(AbExtraSources.home(name)).host
            for(address in listOf("https://$host.evil.invalid/","https://evil-$host/","https://user:pass@$host/","file://$host/test","https://$host:9999/"))assertEquals(address,"",AbExtraSources.name(address))
        }
        assertEquals("",AbExtraSources.name("https://127.0.0.1/"))
        assertEquals("",AbExtraSources.name("not a URL"))
    }
    @Test fun manualSitesHaveExactHttpsIdentityAndStayOutOfAutomaticSearch() {
        for((name,home,_) in AbExtraSources.browserOnly) {
            assertEquals(name,AbExtraSources.manualName(home+"book/example"))
            assertFalse(name in AbExtraSources.names)
            val host=java.net.URI(home).host
            for(url in listOf("http://$host/","https://$host.evil.invalid/","https://user@$host/","https://$host:9000/","file://$host/"))
                assertEquals(url,"",AbExtraSources.manualName(url))
        }
    }
    @Test fun mobileAliasesRemainTheSameSource() {
        assertEquals("Книга в Ухе",AbExtraSources.name("https://knigavuhe.org/book/a/"))
        assertEquals("Книга в Ухе",AbExtraSources.name("https://m.knigavuhe.org/book/a/"))
        assertEquals("Izibuk",AbExtraSources.name("https://pda.izib.uk/art137768"))
        assertEquals("Отруб",AbExtraSources.name("https://m.otrub.in/test.html"))
    }
    @Test fun detailRoutesExcludeSearchGenresAndInfrastructure() {
        for(url in listOf("https://predanie.ru/leonov-leonid-maksimovich/piramida-roman-navazhdenie/","https://uknig.com/books/718702","https://pda.izib.uk/art137768","https://m.otrub.in/taras-bulba.html","https://akniga.org/gogol-nikolay-shinel-4","https://baza-knig.info/audio-124589-book","https://audioknigi.pro/audioknigi/62903-book.html"))assertTrue(url,AbExtraSources.book(url))
        for(url in listOf("https://predanie.ru/rubric-page/russkaya-literatura/","https://predanie.ru/fond/about/","https://uknig.com/books/popular","https://uknig.com/?q=test","https://m.otrub.in/search.html","https://akniga.org/search/books","https://baza-knig.info/genre-1-fiction","https://audioknigi.pro/engine/ajax/controller.php"))assertFalse(url,AbExtraSources.book(url))
    }
}
