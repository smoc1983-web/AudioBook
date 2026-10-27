package com.audiobookdownloader;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class ContinuationRulesTest {
 private ContinuationRules.Book b(int n){return new ContinuationRules.Book("Цикл. Том "+n,"https://bookinfo.net/fantasy/author/volume-"+n,"Лев Белин",(double)n);}
 @Test public void baselineIsSilentAndNextVolumeAppearsOnce(){
  var first=ContinuationRules.evaluate(null,List.of(b(1),b(2)),"Белин Лев");assertTrue(first.additions.isEmpty());
  var next=ContinuationRules.evaluate(first.state,List.of(b(1),b(2),b(3)),"Лев Белин");assertEquals(1,next.additions.size());assertEquals(3d,next.additions.get(0).order,0);
  assertTrue(ContinuationRules.evaluate(next.state,List.of(b(3)),"Лев Белин").additions.isEmpty());
 }
 @Test public void alternateNarrationDoesNotNotify(){
  var first=ContinuationRules.evaluate(null,List.of(b(2)),"");
  var alternate=new ContinuationRules.Book("Цикл книга II (другой чтец)","https://bookinfo.net/fantasy/author/other", "Лев Белин",2d);
  assertTrue(ContinuationRules.evaluate(first.state,List.of(alternate),"").additions.isEmpty());
 }
 @Test public void oldBackfillsAreSilent(){var state=ContinuationRules.evaluate(null,List.of(b(5)),"").state;assertTrue(ContinuationRules.evaluate(state,List.of(b(1),b(4)),"").additions.isEmpty());}
 @Test public void existingHighWaterMarkStaysSilentWhenSeenAliasesAreMissing(){
  var recovered=new ContinuationRules.State(Set.of(),2d);
  var update=ContinuationRules.evaluate(recovered,List.of(b(2),b(3)),"Лев Белин");
  assertEquals(1,update.additions.size());assertEquals(3d,update.additions.get(0).order,0);
 }
 @Test public void differentAuthorIsExcluded(){var book=new ContinuationRules.Book("Том 9","url","Другой Автор",9d);assertTrue(ContinuationRules.evaluate(new ContinuationRules.State(Set.of(),2d),List.of(book),"Лев Белин").additions.isEmpty());}
 @Test public void unknownNumberUsesTitleAndUrl(){var book=new ContinuationRules.Book("Другая история","url","",null);var update=ContinuationRules.evaluate(new ContinuationRules.State(Set.of(),null),List.of(book),"");assertEquals(1,update.additions.size());assertTrue(ContinuationRules.evaluate(update.state,List.of(book),"").additions.isEmpty());}
 @Test public void romanVolumeAndSeriesSuffix(){assertEquals(6d,ContinuationRules.order("Новый каменный век. Том VI"),0);assertEquals("Каменный век",ContinuationRules.seriesName("Серия: Каменный век #6"));}
}
