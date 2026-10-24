package com.audiobookdownloader;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Reads explicit source series and book cards; recommendations and unknown pages are excluded. */
public final class ContinuationParser {
 public static final class Cycle {public final String title,url;public final Double order;Cycle(String t,String u,Double o){title=t;url=u;order=o;}}
 public static final class Detail {
  public final String title,author;public final List<Cycle> cycles;public final boolean announced,playable;
  Detail(String t,String a,List<Cycle> c,boolean pending,boolean audio){title=t;author=a;cycles=c;announced=pending;playable=audio;}
 }
 public static final class Page {
  public final List<ContinuationRules.Book> books;public final List<String> next;
  Page(List<ContinuationRules.Book> b,List<String> n){books=b;next=n;}
 }
 private final ContinuationPolicy policy;
 public ContinuationParser(ContinuationPolicy policy){this.policy=policy;}
 private Document document(String html,String url){
  Document d=Jsoup.parse(html,url);String title=d.title().toLowerCase(Locale.ROOT);
  if(title.matches(".*(?:just a moment|access denied|captcha|checking your browser|проверка соединения).*"))throw new IllegalArgumentException("Сайт требует проверки в браузере");
  if(d.select("input[type=password]").size()>0&&d.select("h1").text().matches("(?iu).*(?:вход|авторизация).*"))throw new IllegalArgumentException("Источник требует входа");
  d.select("header,footer,aside,[class*=recommend],[class*=related],[class*=similar],[class*=comment],[class*=advert]").remove();
  for(Element nav:d.select("nav"))if(!nav.className().matches("(?i).*pagin.*")&&nav.select("a[rel=next]").isEmpty())nav.remove();
  return d;
 }
 private static String text(Element root,String selector){Element e=root.selectFirst(selector);return e==null?"":e.hasAttr("content")?e.attr("content"):e.text();}
 private static Element scope(Document d){Element e=d.selectFirst("main");if(e==null)e=d.selectFirst("article,.book-page,.book-details,.fullstory,.full-story");return e==null?d.body():e;}
 private String author(Element scope){
  String value=text(scope,"[itemprop=author]");
  if(value.isEmpty())for(Element a:scope.select("a[href]"))if(a.attr("href").matches("(?i).*/(?:author|authors|avtor|avtory)(?:/|[0-9]).*")){value=a.text();break;}
  return value.replaceFirst("(?iu)^автор\\s*[:—-]\\s*","").trim();
 }
 private static boolean announced(Element e){String s=text(e,".coming-soon,.book-status,[data-status]");return s.matches("(?iu).*(?:скоро|анонс|ожидается|готовится к выходу).*")||e.text().matches("(?iu)^анонс\\s*[:—-].*");}
 public Detail detail(String html,String url){
  if(!policy.book(url))throw new IllegalArgumentException("Неподдерживаемая страница книги");
  Document d=document(html,url);Element body=scope(d);String name=text(d,"h1"),writer=author(body);List<Cycle> cycles=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(Element a:body.select("a[href]")){
   String address=policy.canonical(a.absUrl("href"));if(!policy.same(url,address)||!policy.series(address)||!seen.add(address))continue;
   String label=ContinuationRules.seriesName(a.text().replaceFirst("(?iu)^серия\\s+","").trim());
   if(label.isEmpty())continue;Double order=ContinuationRules.order(name);
   Matcher number=Pattern.compile("#\\s*([0-9]+(?:[.,][0-9]+)?)").matcher(a.text()+(a.nextSibling()==null?"":a.nextSibling().toString()));
   if(number.find())order=Double.valueOf(number.group(1).replace(',','.'));
   cycles.add(new Cycle(label,address,order));
  }
  for(Element script:d.select("script[type=application/ld+json]"))try{
   JSONObject data=new JSONObject(script.data());if(data.optString("@type").matches("(?i)Book|Audiobook")){
    if(!data.optString("name").isEmpty())name=data.optString("name");Object a=data.opt("author");if(a instanceof JSONArray)a=((JSONArray)a).opt(0);
    if(a instanceof JSONObject)writer=((JSONObject)a).optString("name",writer);else if(a instanceof String)writer=(String)a;
   }
  }catch(Exception ignored){}
  boolean audio=!body.select("audio[src],audio source[src],[data-ap-encoded],[data-playlist]").isEmpty();
  for(Element script:d.select("script"))if(Pattern.compile("\\bKB\\s*\\.\\s*playerInit\\s*\\(|new\\s+(?:Playerjs|PlayerJS|XSPlayer|BookPlayer)\\s*\\(|window\\.XSPlayer\\s*\\(").matcher(script.data()).find())audio=true;
  // Nuxt pages may render a global empty <audio>; only this book's nonempty audioFiles count.
  Element payload=d.selectFirst("script#__NUXT_DATA__");
  if(payload!=null)try {
   JSONArray data=new JSONArray(payload.data());
   for(int i=0;i<data.length();i++)if(data.opt(i) instanceof JSONObject){
    JSONObject obj=data.getJSONObject(i);int nameIndex=obj.optInt("title",-1),fileIndex=obj.optInt("audioFiles",-1);
    if(nameIndex>=0&&fileIndex>=0&&SearchBookIndex.titleKey(data.optString(nameIndex)).equals(SearchBookIndex.titleKey(name))
       &&data.opt(fileIndex) instanceof JSONArray&&data.getJSONArray(fileIndex).length()>0)audio=true;
   }
  }catch(Exception ignored){}
  return new Detail(name,writer,cycles,announced(body),audio);
 }
 private Element card(Element a,String url){
  Element best=a;Element p=a.parent();for(int i=0;p!=null&&i<6;i++,p=p.parent()){
   if(p.tagName().equals("main")||p.tagName().equals("body"))break;
   Set<String> addresses=new HashSet<>();for(Element link:p.select("a[href]")){String x=policy.canonical(link.absUrl("href"));if(policy.book(x))addresses.add(x);}
   if(addresses.size()>1)break;best=p;
  }return best;
 }
 public Page page(String html,String url,String rootUrl,String expectedTitle,String seedUrl){
  if(!policy.series(rootUrl)||!policy.nextPage(rootUrl,url))throw new IllegalArgumentException("Это не страница выбранного цикла");
  Document d=document(html,url);Element body=d.selectFirst("main");if(body==null)body=d.body();Map<String,ContinuationRules.Book> found=new LinkedHashMap<>();
  for(Element a:body.select("a[href]")){
   String target=policy.canonical(a.absUrl("href"));if(!policy.same(rootUrl,target)||!policy.book(target))continue;
   String title=a.text().trim();if(title.isEmpty())continue;Element card=card(a,target);if(announced(card))continue;
   String heading=text(card,"h2,h3,.book-title,[itemprop=name]");if(!heading.isEmpty())title=heading;
   if(title.length()>400)continue;String writer=author(card);Double order=ContinuationRules.order(title);
   String declared=card.attr("data-order");if(declared.isEmpty())declared=text(card,"[itemprop=position],[data-position]");
   if(declared.matches("[0-9]+(?:[.,][0-9]+)?"))order=Double.valueOf(declared.replace(',','.'));
   ContinuationRules.Book old=found.get(target);
   if(old==null||old.author.isEmpty()&&!writer.isEmpty())found.put(target,new ContinuationRules.Book(title,target,writer,order));
  }
  String title=SearchBookIndex.normal(text(d,"h1")),wanted=SearchBookIndex.normal(ContinuationRules.seriesName(expectedTitle));
  boolean identified=!wanted.isEmpty()&&title.contains(wanted)||found.containsKey(policy.canonical(seedUrl));
  if(!identified||found.isEmpty())throw new IllegalArgumentException("Не удалось подтвердить список книг этого цикла");
  Set<String> next=new LinkedHashSet<>();for(Element a:body.select("a[rel=next],.pagination a[href],.paginator a[href],.pages a[href],.navigation a[href],[class*=pagin] a[href]")){
   String target=policy.canonical(a.absUrl("href"));if(policy.nextPage(rootUrl,target)&&!target.equals(policy.canonical(url)))next.add(target);
  }
  for(Element b:body.select("button,[role=button]"))if(b.text().matches("(?iu).*(?:показать|загрузить)\\s+(?:ещё|еще).*"))throw new IllegalArgumentException("Источник раскрывает цикл через JavaScript; список неполный");
  return new Page(new ArrayList<>(found.values()),new ArrayList<>(next));
 }
}
