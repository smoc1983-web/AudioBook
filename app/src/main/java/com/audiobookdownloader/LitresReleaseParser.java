package com.audiobookdownloader;
import org.jsoup.*;import org.jsoup.nodes.*;import java.util.*;import java.text.*;
/** Audio-only public catalogue metadata. No credentials or paid audio access. */
public final class LitresReleaseParser {
 public static final String FEED="https://www.litres.ru/new/?art_types=audiobook";
 public static List<SeriesAuthorityParser.Record> feed(String html){Document d=Jsoup.parse(html,FEED);String h=d.select("h1").text().toLowerCase(Locale.ROOT);if(!h.contains("аудиокниг")||!h.contains("нов"))throw new IllegalArgumentException("Литрес не вернул каталог новых аудиокниг");List<SeriesAuthorityParser.Record> out=new ArrayList<>();for(SeriesAuthorityParser.Record r:SeriesAuthorityParser.search(html,FEED))if(audio(r.bookUrl))out.add(r);if(out.isEmpty())throw new IllegalArgumentException("Список новинок Литрес не загружен");return out;}
 public static boolean audio(String url){return SeriesAuthorityRules.work(url)&&SeriesAuthorityRules.canonical(url).startsWith("https://www.litres.ru/audiobook/");}
 public static long releaseDate(String html,String url,String title,String author){if(!audio(url))return 0;Document d=Jsoup.parse(html,url);if(!SeriesAuthorityRules.sameTitle(title,d.select("h1").text(),""))return 0;List<String> authors=new ArrayList<>();for(Element a:d.select("[data-testid='book__author--link'],[data-testid='book__author'] a,[data-testid='book__authors'] a,a[data-testid*=author][href^=/author/]"))authors.add(a.text());if(!SeriesAuthorityRules.sameAuthor(author,authors))return 0;
  if(d.select("[data-testid*=preorder],[class*=preorder],[class*=comingSoon]").size()>0)return 0;
  for(Element e:d.select("div")){if(e.childrenSize()!=2||!e.child(0).text().matches("(?iu)Дата выхода на Литрес\\s*:?") )continue;String value=e.child(1).text();try{SimpleDateFormat f=new SimpleDateFormat("d MMMM yyyy",new Locale("ru"));f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));ParsePosition p=new ParsePosition(0);Date date=f.parse(value,p);if(date!=null&&p.getIndex()==value.length())return date.getTime();}catch(Exception ignored){}}
  return 0;
 }
 public static boolean newlyReleased(long date,long previous,long now){return date>0&&previous>0&&date>=previous-previous%86400000L&&date<=now;}
}
