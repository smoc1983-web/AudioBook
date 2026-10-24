package com.audiobookdownloader;
import org.junit.Test;import java.nio.charset.StandardCharsets;import static org.junit.Assert.*;
public class ContinuationParserTest {
 private final ContinuationPolicy policy=new ContinuationPolicy("[]");
 private final ContinuationParser parser=new ContinuationParser(policy);
 private final String root="https://bookinfo.net/series/stone",book="https://bookinfo.net/fantasy/author/stone-1";
 private String card="<article><h2><a href='"+book+"'>Камень. Том 1</a></h2><a href='/author/writer'>Лев Белин</a></article>";
 @Test public void recordedBookinfoSeriesHasFourVolumes()throws Exception{
  String html=new String(getClass().getResourceAsStream("/continuations/bookinfo-series.html").readAllBytes(),StandardCharsets.UTF_8);
  var page=parser.page(html,"https://bookinfo.net/series/kamennyy-vek-belin","https://bookinfo.net/series/kamennyy-vek-belin","Каменный век [Белин]","");
  assertEquals(4,page.books.size());assertEquals(6d,page.books.get(0).order,0);assertEquals("Лев Белин",page.books.get(0).author);assertTrue(page.next.isEmpty());
 }
 @Test public void detailRequiresExplicitSeriesAndAudio(){var d=parser.detail("<h1>Камень. Том 2</h1><a href='/author/writer'>Лев Белин</a><a href='/series/stone'>Камень #2</a><script>new Playerjs({file:'audio.mp3'});</script>",book);assertEquals(1,d.cycles.size());assertTrue(d.playable);assertEquals(2d,d.cycles.get(0).order,0);}
 @Test public void emptyGlobalAudioDoesNotProvePlayable(){assertFalse(parser.detail("<h1>Камень</h1><audio></audio>",book).playable);}
 @Test public void unrelatedRecommendationsAreExcluded(){var p=parser.page("<h1>Камень</h1>"+card+"<div class='related'><a href='/fantasy/other/unrelated'>Чужой. Том 9</a></div>",root,root,"Камень",book);assertEquals(1,p.books.size());}
 @Test public void paginationIsRetainedAndOffsiteRejected(){var p=parser.page("<h1>Камень</h1>"+card+"<nav class='pagination'><a href='?page=2'>2</a><a href='https://evil.test/series/stone?page=3'>3</a></nav>",root,root,"Камень",book);assertEquals(1,p.next.size());assertTrue(p.next.get(0).endsWith("?page=2"));}
 @Test(expected=IllegalArgumentException.class)public void challengeIsFailure(){parser.page("<title>Just a moment</title><h1>Камень</h1>"+card,root,root,"Камень",book);}
 @Test(expected=IllegalArgumentException.class)public void incompleteJavascriptListIsFailure(){parser.page("<h1>Камень</h1>"+card+"<button>Показать ещё</button>",root,root,"Камень",book);}
 @Test public void exactHostAndUrlPolicy(){assertFalse(policy.book("https://bookinfo.net.evil.test/fantasy/author/book"));assertFalse(policy.series("https://127.0.0.1/series/stone"));assertFalse(policy.book("https://user@bookinfo.net/fantasy/author/book"));assertFalse(policy.nextPage(root,"https://bookinfo.net/author/someone"));assertEquals(root,policy.canonical(root+"/?utm_source=test#frag"));}
}
