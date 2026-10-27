package com.audiobookdownloader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OfficialTextNoticePolicyTest {
 private ContinuationRules.Book book(int order){
  return new ContinuationRules.Book("Том "+order,"https://author.today/work/"+order,"Тест Автор",(double)order);
 }
 @Test public void twoWatchedBooksOfOneSeriesDoNotNotifyAboutExistingVolumes(){
  var all=List.of(book(1),book(2));
  var second=OfficialTextNoticePolicy.evaluate(null,all,2,"Тест Автор");
  assertTrue(second.additions.isEmpty());
  var first=OfficialTextNoticePolicy.evaluate(second.state,all,1,"Тест Автор");
  assertTrue("Changing watched book must not announce an already published volume",first.additions.isEmpty());
  var next=OfficialTextNoticePolicy.evaluate(first.state,List.of(book(1),book(2),book(3)),1,"Тест Автор");
  assertEquals(1,next.additions.size());assertEquals(3d,next.additions.get(0).order,0);
 }
 @Test public void aNewlyPublishedVolumeAtOrBeforeTheSelectedBookIsNotItsContinuation(){
  var base=OfficialTextNoticePolicy.evaluate(null,List.of(book(1)),2,"Тест Автор").state;
  var same=OfficialTextNoticePolicy.evaluate(base,List.of(book(1),book(2)),2,"Тест Автор");
  assertTrue(same.additions.isEmpty());
  var later=OfficialTextNoticePolicy.evaluate(same.state,List.of(book(1),book(2),book(3)),2,"Тест Автор");
  assertEquals(1,later.additions.size());
 }
 @Test public void repeatedCheckRemainsSilent(){
  var base=OfficialTextNoticePolicy.evaluate(null,List.of(book(1)),1,"Тест Автор").state;
  var next=OfficialTextNoticePolicy.evaluate(base,List.of(book(1),book(2)),1,"Тест Автор");
  assertEquals(1,next.additions.size());
  assertTrue(OfficialTextNoticePolicy.evaluate(next.state,List.of(book(1),book(2)),1,"Тест Автор").additions.isEmpty());
 }
}
