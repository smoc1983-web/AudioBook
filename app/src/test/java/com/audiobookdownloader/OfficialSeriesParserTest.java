package com.audiobookdownloader;
import org.junit.*;import static org.junit.Assert.*;
public class OfficialSeriesParserTest {
 private final String at="https://author.today/work/series/7666",lt="https://www.litres.ru/series/hranitel-kladov-714498";
 private String fixture(String name)throws Exception{return new String(getClass().getResourceAsStream("/series-metadata/"+name+".html").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
 @Test public void currentMobileAuthorTodayHasFourTextVolumesAndExplicitOrder()throws Exception {
  var evidence=SeriesAuthorityParser.detail(fixture("at-gold-mobile"),"https://author.today/work/72647");assertEquals("Хранитель кладов",evidence.seriesTitle);assertEquals(2d,evidence.order,0);
  var p=OfficialSeriesParser.page(fixture("at-series-mobile"),at,at,evidence.seriesTitle,"Андрей Васильев");assertEquals(4,p.books.size());assertEquals(4d,p.books.get(3).order,0);assertFalse(p.incomplete);
  for(var b:p.books){assertEquals("text",b.format);assertEquals("published",b.status);}assertEquals(4,SeriesAuthorityParser.members(fixture("at-series-mobile"),at,evidence.seriesTitle).size());
 }
 @Test public void litresKeepsTextAndAudioEditionsWithoutTreatingAvailableAudioAsTheCardFormat()throws Exception {
  var p=OfficialSeriesParser.page(fixture("litres-series-formats"),lt,lt,"Хранитель кладов","Андрей Васильев");assertEquals(8,p.books.size());assertEquals(4,p.books.stream().filter(b->b.format.equals("text")).count());assertEquals(4,p.books.stream().filter(b->b.format.equals("audio")).count());assertTrue(p.books.stream().allMatch(b->b.order!=null&&b.status.equals("published")));
 }
 @Test(expected=IllegalArgumentException.class)public void differentDirectSeriesIsRejected()throws Exception {OfficialSeriesParser.page(fixture("litres-series-formats"),lt,lt,"Отдел 15-К","Андрей Васильев");}
 @Test(expected=IllegalArgumentException.class)public void anotherAuthorCannotBecomeAContinuation()throws Exception {OfficialSeriesParser.page(fixture("litres-series-formats"),lt,lt,"Хранитель кладов","Другой Автор");}
 @Test public void paginationStaysOnTheExactOfficialCycleAndFlagsInteractivePartialLists()throws Exception {
  String html=fixture("at-series-mobile").replace("</body>","<nav><a rel='next' href='?page=2'>Далее</a><a rel='next' href='https://evil.invalid/?page=2'>Чужой</a></nav><button>Показать ещё</button></body>");
  var p=OfficialSeriesParser.page(html,at,at,"Хранитель кладов","Андрей Васильев");assertEquals(1,p.next.size());assertEquals(at+"?page=2",p.next.get(0));assertTrue(p.incomplete);
  assertEquals("",OfficialSeriesParser.pageUrl(at+"?redirect=https://evil.invalid",at));assertEquals("",OfficialSeriesParser.pageUrl(at.replace("7666","1234")+"?page=2",at));assertEquals("",OfficialSeriesParser.pageUrl("http://author.today/work/series/7666",at));
 }
 @Test public void announcementsAndOngoingTextAreDifferentFromPublishedBooks(){
  String prefix="<h1>Путь</h1><div class='work-row-content'><div class='work-title'><span class='label-row-index'>2</span><a href='/work/2'>Второй том</a></div><div class='work-author'>Тест Автор</div><div class='work-info'><i class='icon-book2'></i>10K зн. ";
  String u="https://author.today/work/series/2";var planned=OfficialSeriesParser.page(prefix+"<span class='preorder'>Предзаказ</span></div></div>",u,u,"Путь","Тест Автор");assertEquals("planned",planned.books.get(0).status);
  var writing=OfficialSeriesParser.page(prefix+"В процессе</div></div>",u,u,"Путь","Тест Автор");assertEquals("ongoing",writing.books.get(0).status);assertEquals("text",writing.books.get(0).format);
 }
}
