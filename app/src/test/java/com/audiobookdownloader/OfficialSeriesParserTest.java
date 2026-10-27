package com.audiobookdownloader;
import org.junit.*;import static org.junit.Assert.*;
public class OfficialSeriesParserTest {
 private final String at="https://author.today/work/series/7666",lt="https://www.litres.ru/series/hranitel-kladov-714498";
 private String fixture(String name)throws Exception{return new String(getClass().getResourceAsStream("/series-metadata/"+name+".html").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
 private String mobileCard(String info,String extra){
  return "<div class='work-row-content'><div class='work-title'><span class='label-row-index'>2</span><a href='/work/11'>Продолжение</a></div><div class='work-author'>Тест Автор</div>"+
   (info==null?"":"<div class='work-info'>"+info+"</div>")+extra+"</div>";
 }
 private OfficialSeriesParser.Page mobilePage(String body){return OfficialSeriesParser.page("<h1>Путь</h1>"+body,at,at,"Путь","Тест Автор");}
 @Test public void independentFormatAndPublicationSignalsAreReadWithoutGuessing(){
  String[][] cases={
   {"<i class='icon-headphones'></i>","audio","catalog"},
   {"аудиокнига","audio","catalog"},
   {"<i class='icon-book2'></i>","text","catalog"},
   {"100K зн.","text","catalog"},
   {"Неизвестный формат","unknown","catalog"},
   {"пишется","unknown","ongoing"},
   {"<i class='icon-pencil'></i>","unknown","ongoing"},
   {"завершен","unknown","published"},
   {"завершён","unknown","published"},
   {"весь текст","unknown","published"},
   {"<i class='icon-check'></i>","unknown","published"}
  };
  for(String[] row:cases){var b=mobilePage(mobileCard(row[0],"")).books.get(0);assertEquals(row[0],row[1],b.format);assertEquals(row[0],row[2],b.status);}
  assertEquals("unknown",mobilePage(mobileCard(null,"")).books.get(0).format);
  assertEquals("planned",mobilePage(mobileCard("весь текст","<span class='preorder'>Анонс</span>")).books.get(0).status);
  assertEquals("planned",mobilePage(mobileCard("весь текст","Предзаказ")).books.get(0).status);
 }
 @Test public void paginationControlsAreIndependentAndExactlyEightyCardsArePartial(){
  String card=mobileCard("100K зн.","");
  assertTrue(mobilePage(card+"<button>Показать ещё</button>").incomplete);
  assertFalse(mobilePage(card+"<button disabled>Показать ещё</button>").incomplete);
  assertFalse(mobilePage(card+"<a rel='next'>Далее</a><a rel='next' href=' '>Далее</a><nav class='pagination'><a rel='prev' href='?page=0'>Назад</a></nav>").incomplete);
  StringBuilder cards=new StringBuilder();for(int i=0;i<80;i++)cards.append(card.replace("/work/11","/work/"+(1000+i)));
  var capped=mobilePage(cards.toString());assertEquals(80,capped.books.size());assertTrue(capped.incomplete);
 }
 @Test public void fragmentsAndOtherCyclePagesCannotBecomeVerifiedCatalogues(){
  assertEquals("",OfficialSeriesParser.pageUrl(at+"#fragment",at));
  try{OfficialSeriesParser.page("<h1>Путь</h1>"+mobileCard("100K зн.",""),at,at.replace("7666","1234"),"Путь","Тест Автор");fail("Wrong cycle must be rejected");}catch(IllegalArgumentException expected){}
 }
 @Test public void mobileSeriesWithEnclosingWorkLinksKeepsAllVolumesAndOrders(){
  String html="<h1>Путь</h1><a class='work-row' href='/work/11'><div class='work-row-content'><h4 class='work-title'><span class='label-row-index'>2</span>Продолжение</h4><div class='work-author'>Тест Автор</div><div class='work-info'><i class='icon-book2'></i><i class='icon-check-bold'></i>100K зн.</div></div></a>";
  String cycle="https://author.today/work/series/7";
  var page=OfficialSeriesParser.page(html,cycle,cycle,"Путь","Тест Автор");
  assertEquals(1,page.books.size());assertEquals("https://author.today/work/11",page.books.get(0).url);
  assertEquals("Продолжение",page.books.get(0).title);
  assertEquals(2d,page.books.get(0).order,0);assertEquals("text",page.books.get(0).format);
  assertEquals(2d,SeriesAuthorityParser.members(html,cycle,"Путь").get(0).order,0);
 }
 @Test(expected=IllegalArgumentException.class)public void legacyMembersCannotCallAPaginatedNavigationListComplete()throws Exception{
  SeriesAuthorityParser.members(fixture("at-series-mobile")+"<nav class='pagination'><a href='?page=2'>2</a></nav>",at,"Хранитель кладов");
 }
 @Test public void absoluteOwnSeriesLinksAreAcceptedButForeignProvidersAreRejected(){
  String html="<h1>Первая книга</h1><div class='work-header-content'><div class='card-author'><a>Тест Автор</a></div><div><a href='https://author.today/work/series/7'>Путь</a><span>#1</span></div></div>";
  assertEquals("Путь",SeriesAuthorityParser.detail(html,"https://author.today/work/7").seriesTitle);
  try{SeriesAuthorityParser.detail(html.replace("https://author.today/work/series/7","https://www.litres.ru/series/path-7"),"https://author.today/work/7");fail("Foreign provider must not become an Author.Today series");}catch(IllegalArgumentException expected){}
 }
 @Test public void mobileSearchUsesTheEnclosingWorkLinkAndDoesNotGuessFromSimilarWords()throws Exception {
  var records=SeriesAuthorityParser.search(fixture("at-search-mobile"),"https://author.today/search?category=works&q=test");
  assertEquals(1,records.size());var book=records.get(0);assertEquals("https://author.today/work/98164",book.bookUrl);assertEquals("Золотая кобра",book.title);
  assertFalse(SeriesAuthorityParser.matches("Золото мертвых","Андрей Васильев",book));
 }
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
  var p=OfficialSeriesParser.page(html,at,at,"Хранитель кладов","Андрей Васильев");assertEquals(1,p.next.size());assertEquals(at+"?page=2",p.next.get(0));assertTrue(p.incomplete);assertTrue(OfficialSeriesParser.page(html.replace("<button>Показать ещё</button>",""),at,at,"Хранитель кладов","Андрей Васильев").incomplete);
  assertEquals("",OfficialSeriesParser.pageUrl(at+"?redirect=https://evil.invalid",at));assertEquals("",OfficialSeriesParser.pageUrl(at.replace("7666","1234")+"?page=2",at));assertEquals("",OfficialSeriesParser.pageUrl("http://author.today/work/series/7666",at));
 }
 @Test public void announcementsAndOngoingTextAreDifferentFromPublishedBooks(){
  String prefix="<h1>Путь</h1><div class='work-row-content'><div class='work-title'><span class='label-row-index'>2</span><a href='/work/2'>Второй том</a></div><div class='work-author'>Тест Автор</div><div class='work-info'><i class='icon-book2'></i>10K зн. ";
  String u="https://author.today/work/series/2";var planned=OfficialSeriesParser.page(prefix+"<span class='preorder'>Предзаказ</span></div></div>",u,u,"Путь","Тест Автор");assertEquals("planned",planned.books.get(0).status);
  var writing=OfficialSeriesParser.page(prefix+"В процессе</div></div>",u,u,"Путь","Тест Автор");assertEquals("ongoing",writing.books.get(0).status);assertEquals("text",writing.books.get(0).format);
 }
}
