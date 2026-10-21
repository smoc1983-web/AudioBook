package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AdditionalBookSourcesTest {
    @Test fun registryHasUniqueNamesAndConservativeDefaults() {
        assertEquals(12,AbExtraSources.names.size)
        assertEquals(12,AbExtraSources.names.toSet().size)
        assertEquals(9,AbExtraSources.defaults.size)
        for(name in AbExtraSources.names){assertEquals(name,AbExtraSources.name(AbExtraSources.home(name)));assertTrue(AbExtraSources.status(name).isNotBlank())}
        assertFalse("Unverified challenge site must not block global search", "Lis10Book" in AbExtraSources.defaults)
        assertFalse("HTTP 403 must not be labelled available", "Audioboo" in AbExtraSources.defaults)
    }
    @Test fun urlIdentityRejectsCredentialsImpostorsAndOtherPorts() {
        for(name in AbExtraSources.names){val host=java.net.URI(AbExtraSources.home(name)).host
            for(address in listOf("https://$host.evil.invalid/","https://evil-$host/","https://user:pass@$host/","file://$host/test","https://$host:9999/"))assertEquals(address,"",AbExtraSources.name(address))
        }
        assertEquals("",AbExtraSources.name("https://127.0.0.1/"))
        assertEquals("",AbExtraSources.name("not a URL"))
    }
    @Test fun mobileAliasesRemainTheSameSource() {
        assertEquals("Книга в Ухе",AbExtraSources.name("https://knigavuhe.org/book/a/"))
        assertEquals("Книга в Ухе",AbExtraSources.name("https://m.knigavuhe.org/book/a/"))
        assertEquals("Izibuk",AbExtraSources.name("https://pda.izib.uk/art137768"))
        assertEquals("Отруб",AbExtraSources.name("https://m.otrub.in/test.html"))
    }
    @Test fun detailRoutesExcludeSearchGenresAndInfrastructure() {
        for(url in listOf("https://uknig.com/books/718702","https://pda.izib.uk/art137768","https://m.otrub.in/taras-bulba.html","https://akniga.org/gogol-nikolay-shinel-4","https://baza-knig.info/audio-124589-book","https://audioknigi.pro/audioknigi/62903-book.html"))assertTrue(url,AbExtraSources.book(url))
        for(url in listOf("https://uknig.com/books/popular","https://uknig.com/?q=test","https://m.otrub.in/search.html","https://akniga.org/search/books","https://baza-knig.info/genre-1-fiction","https://audioknigi.pro/engine/ajax/controller.php"))assertFalse(url,AbExtraSources.book(url))
    }
}
