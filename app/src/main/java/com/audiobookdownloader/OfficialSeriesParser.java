package com.audiobookdownloader;

import java.net.*;import java.util.*;import java.util.regex.*;
import org.jsoup.*;import org.jsoup.nodes.*;

/** Official series catalogues only. A product listing proves the format, not ownership or free access. */
public final class OfficialSeriesParser {
 public static final class Edition {
  public final String title,url,author,series,provider,format,status;public final Double order;
  Edition(String t,String u,String a,String s,String p,String f,String st,Double n){title=t;url=u;author=a;series=s;provider=p;format=f;status=st;order=n;}
 }
 public static final class Page {
  public final List<Edition> books;public final List<String> next;public final boolean incomplete;
  Page(List<Edition>b,List<String>n,boolean i){books=b;next=n;incomplete=i;}
 }
 private static Double number(Element card){Element e=card.selectFirst(".label-row-index,[data-testid='book__seriesCounter--text']");if(e==null)return null;String s=e.text().trim().replace(',','.');if(!s.matches("\\d+(?:\\.\\d+)?"))return null;double n=Double.parseDouble(s);return n>0&&n<100000?n:null;}
 public static String pageUrl(String address,String series){try{URI u=URI.create(address),s=URI.create(series);if(!SeriesAuthorityRules.cycle(address)||!SeriesAuthorityRules.provider(u.toString()).equals(SeriesAuthorityRules.provider(series))||!u.getRawPath().replaceAll("/+$","").equals(s.getRawPath().replaceAll("/+$",""))||u.getFragment()!=null)return "";String q=u.getRawQuery();if(q!=null&&!q.matches("page=[1-9]\\d{0,2}"))return "";return SeriesAuthorityRules.canonical(address)+(q==null?"":"?"+q);}catch(Exception e){return "";}}
 public static Page page(String html,String address,String seriesUrl,String series,String author){
  if(pageUrl(address,seriesUrl).isEmpty()||author.isBlank())throw new IllegalArgumentException("Неверный адрес или автор серии");
  Document d=Jsoup.parse(html,address);d.select("header,footer,aside,[class*=recommend],[class*=comment],[class*=similar],.annotation,.card-description").remove();
  if(!SeriesAuthorityRules.series(SeriesAuthorityRules.seriesLabel(d.select("h1").text())).equals(SeriesAuthorityRules.series(series)))throw new IllegalArgumentException("Справочник вернул другую серию");
  String provider=SeriesAuthorityRules.provider(address);boolean at=provider.equals("Author.Today");
  List<SeriesAuthorityParser.Record> records=SeriesAuthorityParser.search(html,address);List<Edition> books=new ArrayList<>();
  for(SeriesAuthorityParser.Record r:records){if(!SeriesAuthorityRules.sameAuthor(author,r.authors)||!r.seriesTitle.isBlank()&&!SeriesAuthorityRules.series(r.seriesTitle).equals(SeriesAuthorityRules.series(series)))continue;
   Element card=null;for(Element a:d.select(at?".book-title a[href],.work-title a[href]":"[data-testid=art__title][href]"))if(SeriesAuthorityRules.canonical(a.absUrl("href")).equals(r.bookUrl)){card=a.closest(at?".book-row-content,.work-row-content":"[data-testid=art__wrapper]");break;}
   if(card==null)continue;String format=at?"unknown":r.bookUrl.contains("/audiobook/")?"audio":"text";
   Element info=at?card.selectFirst(".book-stats,.book-info,.work-info"):card;String text=info==null?"":info.text().toLowerCase(Locale.ROOT);
   if(at&&info!=null){if(!info.select(".icon-headphones,.icon-headphones2,[data-work-type=audiobook]").isEmpty()||text.matches("(?s).*(?:^|\\s)аудиокнига(?:$|\\s).*"))format="audio";
    else if(!info.select(".icon-book2,.icon-book").isEmpty()||text.matches("(?s).*\\d[\\d\\s.,]*[kк]?\\s*(?:зн\\.|знаков).*"))format="text";}
   String status="catalog";String own=card.text().toLowerCase(Locale.ROOT);
   if(!card.select("[data-testid*=preorder],[class*=preorder],[class*=comingSoon]").isEmpty()||own.matches("(?s).*(?:предзаказ|скоро в продаже|ожидается выход).*"))status="planned";
   else if(at&&(text.contains("в процессе")||text.contains("пишется")||info!=null&&!info.select(".icon-pencil").isEmpty()))status="ongoing";
   else if(at&&(text.contains("завершен")||text.contains("завершён")||text.contains("весь текст")||info!=null&&!info.select(".icon-check-bold,.icon-check").isEmpty())||!at&&!card.select("[data-testid=art__finalPrice],[data-testid=subscription_available--text]").isEmpty())status="published";
   books.add(new Edition(r.title,r.bookUrl,author,series,provider,format,status,number(card)));
  }
  if(books.isEmpty())throw new IllegalArgumentException("Не прочитан список книг указанного автора");
  LinkedHashSet<String> next=new LinkedHashSet<>();boolean rejectedPage=false;for(Element a:d.select("a[rel=next],.pagination a[href],.paginator a[href],[data-testid*=pagination] a[href]")){if(!a.hasAttr("href")||a.attr("href").isBlank()||a.attr("rel").equals("prev"))continue;String u=pageUrl(a.absUrl("href"),seriesUrl);if(u.isEmpty())rejectedPage=true;else if(!u.equals(pageUrl(address,seriesUrl)))next.add(u);}
  boolean more=records.size()>=80||rejectedPage;for(Element b:d.select("button,[role=button]"))if(!b.hasAttr("disabled")&&b.text().matches("(?iu).*(?:показать|загрузить)\\s+(?:ещё|еще).*"))more=true;
  return new Page(books,new ArrayList<>(next),more);
 }
}
