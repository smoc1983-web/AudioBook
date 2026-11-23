package com.audiobookdownloader;

import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchPaginationPolicyTest {
    private final SearchPaginationPolicy p=new SearchPaginationPolicy("[]");
    @Test public void nextSearchPageIsSelectedWithoutLeavingSource() {
        String root="https://bookinfo.net/search/?s=fiction";
        JSONArray links=new JSONArray().put("https://bookinfo.net/search/?s=fiction")
            .put("https://bookinfo.net/search/?s=fiction&page=2");
        Set<String> seen=new HashSet<>(Set.of(p.canonical(root)));
        assertEquals("https://bookinfo.net/search/?s=fiction&page=2",p.next(root,links,seen));
        seen.add(p.canonical("https://bookinfo.net/search/?s=fiction&page=2"));
        assertEquals("",p.next(root,links,seen));
    }
    @Test public void unrelatedSitesProtocolAndPrivateAddressesAreRejected() {
        String root="https://bookinfo.net/search/";
        JSONArray unsafe=new JSONArray()
            .put("http://bookinfo.net/search/?page=2")
            .put("https://other.example/search/?page=2")
            .put("https://127.0.0.1/search/?page=2")
            .put("https://bookinfo.net/other-category/?page=2")
            .put("https://bookinfo.net:444/search/?page=2")
            .put("https://user:password@bookinfo.net/search/?page=2")
            .put("https://bookinfo.net/search/?page=2#fragment");
        assertEquals("",p.next(root,unsafe,new HashSet<>()));
    }
    @Test public void numberedPathsAreAcceptedOnlyWithinSearchRoot() {
        String root="https://bookinfo.net/search";
        JSONArray pages=new JSONArray().put("https://bookinfo.net/search/page/2")
            .put("https://bookinfo.net/books/page/2");
        assertEquals("https://bookinfo.net/search/page/2",p.next(root,pages,new HashSet<>()));
        assertEquals("",p.next(root,null,new HashSet<>()));
    }
}
