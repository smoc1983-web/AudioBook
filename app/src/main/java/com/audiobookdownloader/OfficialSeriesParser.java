package com.audiobookdownloader;

import java.net.URI;
import java.util.*;
import org.jsoup.nodes.*;

/** Official catalogue formats and publication status; listing never implies free access. */
public final class OfficialSeriesParser {
 public static final class Edition {
  public final String title,url,author,series,provider,format,status;public final Double order;
  Edition(String t,String u,String a,String s,String p,String f,String st,Double n){
   title=t;url=u;author=a;series=s;provider=p;format=f;status=st;order=n;
  }
 }
 public static final class Page {
  public final List<Edition> books;public final List<String> next;public final boolean incomplete;
  Page(List<Edition>b,List<String>n,boolean i){books=b;next=n;incomplete=i;}
 }
 public static String pageUrl(String address,String series){
  try{
   if(!SeriesAuthorityRules.cycle(address)||!SeriesAuthorityRules.cycle(series))return "";
   URI uri=URI.create(address);String canonical=SeriesAuthorityRules.canonical(address);
   if(!canonical.equals(SeriesAuthorityRules.canonical(series))||uri.getRawFragment()!=null)return "";
   String query=uri.getRawQuery();
   if(query==null)return canonical;
   return query.matches("page=[1-9]\\d{0,2}")?canonical+"?"+query:"";
  }catch(Exception error){return "";}
 }
 private static boolean belongs(SeriesAuthorityParser.Record row,String author,String series){
  return SeriesAuthorityRules.sameAuthor(author,row.authors)&&(row.seriesTitle.isBlank()||
   SeriesAuthorityRules.series(row.seriesTitle).equals(SeriesAuthorityRules.series(series)));
 }
 private static String format(boolean at,String url,Element info){
  if(!at)return url.contains("/audiobook/")?"audio":"text";
  if(info==null)return "unknown";
  String text=info.text().toLowerCase(Locale.ROOT);
  if(!info.select(".icon-headphones,.icon-headphones2,[data-work-type=audiobook]").isEmpty()||
   text.matches("(?s).*(?:^|\\s)аудиокнига(?:$|\\s).*"))return "audio";
  if(!info.select(".icon-book2,.icon-book").isEmpty()||
   text.matches("(?s).*\\d[\\d\\s.,]*[kк]?\\s*(?:зн\\.|знаков).*"))return "text";
  return "unknown";
 }
 private static boolean planned(Element card){
  return !card.select("[data-testid*=preorder],[class*=preorder],[class*=comingSoon]").isEmpty()||
   card.text().toLowerCase(Locale.ROOT).matches("(?s).*(?:предзаказ|скоро в продаже|ожидается выход).*");
 }
 private static boolean ongoing(String text,Element info){
  return text.contains("в процессе")||text.contains("пишется")||!info.select(".icon-pencil").isEmpty();
 }
 private static boolean published(String text,Element info){
  return text.contains("завершен")||text.contains("завершён")||text.contains("весь текст")||
   !info.select(".icon-check-bold,.icon-check").isEmpty();
 }
 private static String status(boolean at,Element card,Element info){
  if(planned(card))return "planned";
  if(!at)return card.select("[data-testid=art__finalPrice],[data-testid=subscription_available--text]").isEmpty()?"catalog":"published";
  if(info==null)return "catalog";
  String text=info.text().toLowerCase(Locale.ROOT);
  if(ongoing(text,info))return "ongoing";
  return published(text,info)?"published":"catalog";
 }
 private static boolean interactiveMore(Document d){
  for(Element button:d.select("button,[role=button]")){
   if(button.hasAttr("disabled"))continue;
   if(button.text().matches("(?iu).*(?:показать|загрузить)\\s+(?:ещё|еще).*"))return true;
  }
  return false;
 }
 private static boolean skipLink(Element link){
  return !link.hasAttr("href")||link.attr("href").isBlank()||link.attr("rel").equals("prev");
 }
 private static Page pagination(Document d,String address,String series,List<Edition> books,boolean capped){
  Set<String> next=new LinkedHashSet<>();boolean partial=capped||interactiveMore(d);
  String current=pageUrl(address,series);
  for(Element link:d.select("a[rel=next],.pagination a[href],.paginator a[href],[data-testid*=pagination] a[href]")){
   if(skipLink(link))continue;
   String url=pageUrl(link.absUrl("href"),series);
   if(url.isEmpty())partial=true;else if(!url.equals(current))next.add(url);
  }
  return new Page(books,new ArrayList<>(next),partial);
 }
 public static Page page(String html,String address,String seriesUrl,String series,String author){
  if(pageUrl(address,seriesUrl).isEmpty()||author.isBlank())
   throw new IllegalArgumentException("Неверный адрес или автор серии");
  Document d=SeriesAuthorityParser.document(html,address,true);
  d.select(".annotation,.card-description").remove();
  if(!SeriesAuthorityRules.series(SeriesAuthorityRules.seriesLabel(d.select("h1").text())).equals(SeriesAuthorityRules.series(series)))
   throw new IllegalArgumentException("Справочник вернул другую серию");
  String provider=SeriesAuthorityRules.provider(address);boolean at=provider.equals("Author.Today");
  List<SeriesAuthorityParser.Listed> records=SeriesAuthorityParser.listed(d,address);
  List<Edition> books=new ArrayList<>();
  for(SeriesAuthorityParser.Listed listed:records){
   SeriesAuthorityParser.Record row=listed.record;if(!belongs(row,author,series))continue;
   Element card=listed.card;Element info=at?card.selectFirst(".book-stats,.book-info,.work-info"):card;
   books.add(new Edition(row.title,row.bookUrl,author,series,provider,format(at,row.bookUrl,info),status(at,card,info),row.order));
  }
  if(books.isEmpty())throw new IllegalArgumentException("Не прочитан список книг указанного автора");
  return pagination(d,address,seriesUrl,books,records.size()>=80);
 }
}
