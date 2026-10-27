package com.audiobookdownloader;
import org.jsoup.*;import org.jsoup.nodes.*;import java.util.*;import java.util.regex.*;
/** Reads only visible book identity and explicit series membership from public bibliographic pages. */
public final class SeriesAuthorityParser {
 public static final class Record {
  public final String title,bookUrl,seriesTitle,seriesUrl,parentTitle,parentUrl;public final List<String> authors;public final Double order;
  public Record(String t,String u,List<String> a,String s,String su,Double n,String pt,String pu){title=t;bookUrl=u;authors=a;seriesTitle=s;seriesUrl=su;order=n;parentTitle=pt;parentUrl=pu;}
 }
 private static String text(Element n,String selector){Element e=n.selectFirst(selector);return e==null?"":e.hasAttr("content")?e.attr("content").trim():e.text().trim();}
 private static String label(Element link){String s=link.text();Matcher m=Pattern.compile("[«\"]([^«»\"]+)[»\"]").matcher(s);return SeriesAuthorityRules.seriesLabel(m.find()?m.group(1):s);}
 private static Double order(String text){Matcher m=Pattern.compile("(?iu)(?:#\\s*|^\\s*)(\\d+(?:[.,]\\d+)?)\\s*(?:книга|$)").matcher(text.trim());if(!m.find()){m=Pattern.compile("#\\s*(\\d+(?:[.,]\\d+)?)").matcher(text);if(!m.find())return null;}double d=Double.parseDouble(m.group(1).replace(',','.'));return d>0&&d<100000?d:null;}
 private static Document document(String html,String url){if(html==null||html.length()>3*1024*1024)throw new IllegalArgumentException("Слишком большая страница метаданных");Document d=Jsoup.parse(html,url);if(d.title().matches("(?iu).*(?:captcha|access denied|just a moment|проверка браузера).*"))throw new IllegalArgumentException("Справочник временно недоступен");d.select("header,footer,nav,aside,[class*=recommend],[class*=comment],[class*=similar]").remove();return d;}
 private static List<String> authors(Element root,String selector){LinkedHashSet<String> out=new LinkedHashSet<>();for(Element e:root.select(selector)){String a=e.hasAttr("content")?e.attr("content"):e.text();if(!a.trim().isEmpty())out.add(a.trim());}return new ArrayList<>(out);}
 public static Record detail(String html,String url){if(!SeriesAuthorityRules.work(url))throw new IllegalArgumentException("Не страница книги справочника");Document d=document(html,url);String title=text(d,"h1");if(title.isEmpty())throw new IllegalArgumentException("Нет названия книги");String provider=SeriesAuthorityRules.provider(url);Element scope=d;List<String> authors;Element cycle;
  if(provider.equals("Author.Today")){Element panel=d.selectFirst(".book-meta-panel,.work-header-content");if(panel==null)throw new IllegalArgumentException("Нет метаданных произведения");scope=panel;authors=authors(scope,".book-authors [itemprop=name][content],.book-author a,.book-authors a,.card-author [itemprop=name][content],.card-author a");cycle=scope.selectFirst("a[href^=/work/series/]");}
  else {authors=authors(d,"[data-testid='book__author--link'],[data-testid='book__author'] a,[data-testid='book__authors'] a,[itemprop=author] [itemprop=name],a[data-testid*=author][href^=/author/]");cycle=d.selectFirst("[data-testid='book__series--title'] a[href^=/series/],[data-testid='book__seriesSlider--title'] a[href^=/series/]");}
  if(authors.isEmpty())throw new IllegalArgumentException("Нет автора произведения");if(cycle==null||!SeriesAuthorityRules.cycle(cycle.absUrl("href")))throw new IllegalArgumentException("Справочник не указал прямую серию");String name=label(cycle);if(!SeriesAuthorityRules.plausibleSeriesTitle(name))throw new IllegalArgumentException("Нет названия серии");Double number=order(cycle.parent().text());
  return new Record(title,SeriesAuthorityRules.canonical(url),authors,name,SeriesAuthorityRules.canonical(cycle.absUrl("href")),number,"","");
 }
 public static List<Record> search(String html,String url){Document d=document(html,url);String provider=SeriesAuthorityRules.provider(url);LinkedHashMap<String,Record> out=new LinkedHashMap<>();String cards=provider.equals("Author.Today")?".book-row-content,.work-row-content":"[data-testid='art__wrapper']";
  for(Element card:d.select(cards)){Element title=card.selectFirst(provider.equals("Author.Today")?".book-title a[href],.work-title a[href]":"[data-testid='art__title'][href]");if(title==null||!SeriesAuthorityRules.work(title.absUrl("href")))continue;List<String> a=authors(card,provider.equals("Author.Today")?".book-author a,.work-author":"[data-testid='art__authorName--link']");if(a.isEmpty())continue;Element series=card.selectFirst(provider.equals("Author.Today")?"a[href^=/work/series/]":"a[href^=/series/]");String name=series==null?"":label(series);String address=SeriesAuthorityRules.canonical(title.absUrl("href"));out.putIfAbsent(address,new Record(title.text(),address,a,name,series==null?"":SeriesAuthorityRules.canonical(series.absUrl("href")),null,"",""));if(out.size()>=80)break;}
  return new ArrayList<>(out.values());
 }
 public static Record parent(String html,String url,Record book){if(!SeriesAuthorityRules.cycle(url)||!SeriesAuthorityRules.provider(url).equals("Литрес"))return book;Document d=document(html,url);String heading=SeriesAuthorityRules.seriesLabel(text(d,"h1"));if(!SeriesAuthorityRules.series(heading).equals(SeriesAuthorityRules.series(book.seriesTitle)))return book;Element a=d.selectFirst("[data-testid='series__header--parent'] a[href^=/series/]");if(a==null||!SeriesAuthorityRules.cycle(a.absUrl("href")))return book;return new Record(book.title,book.bookUrl,book.authors,book.seriesTitle,book.seriesUrl,book.order,label(a),SeriesAuthorityRules.canonical(a.absUrl("href")));}
 public static List<Record> members(String html,String url,String expected){
  if(!SeriesAuthorityRules.cycle(url))throw new IllegalArgumentException("Не серия справочника");
  Document d=document(html,url);String heading=SeriesAuthorityRules.seriesLabel(text(d,"h1"));
  if(!SeriesAuthorityRules.series(heading).equals(SeriesAuthorityRules.series(expected)))throw new IllegalArgumentException("Справочник вернул другую серию");
  if(!d.select("a[rel=next],.pagination a,.paginator a").isEmpty())throw new IllegalArgumentException("Список серии содержит дополнительные страницы");
  for(Element b:d.select("button,[role=button]"))if(b.text().matches("(?iu).*(?:показать|загрузить)\\s+(?:ещё|еще).*"))throw new IllegalArgumentException("Справочник не раскрыл полный список серии");
  List<Record> found=search(html,url);List<Record> out=new ArrayList<>();
  for(Record r:found){Element card=null;for(Element a:d.select(".book-row-content .book-title a[href],.work-row-content .work-title a[href],[data-testid='art__title'][href]"))if(SeriesAuthorityRules.canonical(a.absUrl("href")).equals(r.bookUrl)){card=a.closest(SeriesAuthorityRules.provider(url).equals("Author.Today")?".book-row-content,.work-row-content":"[data-testid='art__wrapper']");break;}
   Double n=card==null?null:order(text(card,".label-row-index"));out.add(new Record(r.title,r.bookUrl,r.authors,expected,url,n,"",""));}
  if(out.isEmpty()||out.size()>=80)throw new IllegalArgumentException("Не удалось прочитать полный список серии");return out;
 }
 public static boolean matches(String title,String author,Record r){return SeriesAuthorityRules.sameAuthor(author,r.authors)&&SeriesAuthorityRules.sameTitle(title,r.title,r.seriesTitle);}
}
