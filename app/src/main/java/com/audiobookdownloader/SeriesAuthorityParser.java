package com.audiobookdownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.util.*;
import java.util.regex.*;

/** Visible identity and explicit direct-series membership, never recommendation titles. */
public final class SeriesAuthorityParser {
 public static final class Record {
  public final String title,bookUrl,seriesTitle,seriesUrl,parentTitle,parentUrl;
  public final List<String> authors;public final Double order;
  public Record(String t,String u,List<String>a,String s,String su,Double n,String pt,String pu){
   title=t;bookUrl=u;authors=a;seriesTitle=s;seriesUrl=su;order=n;parentTitle=pt;parentUrl=pu;
  }
 }
 static final class Listed {
  final Record record;final Element card;
  Listed(Record record,Element card){this.record=record;this.card=card;}
 }
 private static String text(Element root,String selector){
  Element node=root.selectFirst(selector);if(node==null)return "";
  return node.hasAttr("content")?node.attr("content").trim():node.text().trim();
 }
 private static String label(Element link){
  String text=link.text();Matcher m=Pattern.compile("[«\"]([^«»\"]+)[»\"]").matcher(text);
  return SeriesAuthorityRules.seriesLabel(m.find()?m.group(1):text);
 }
 private static Double order(String text){
  Matcher m=Pattern.compile("(?iu)(?:#\\s*|^\\s*)(\\d+(?:[.,]\\d+)?)\\s*(?:книга|$)").matcher(text.trim());
  if(!m.find()){m=Pattern.compile("#\\s*(\\d+(?:[.,]\\d+)?)").matcher(text);if(!m.find())return null;}
  double n=Double.parseDouble(m.group(1).replace(',','.'));return n>0&&n<100000?n:null;
 }
 static Document document(String html,String url,boolean navigation){
  if(html==null||html.length()>3*1024*1024)throw new IllegalArgumentException("Слишком большая страница метаданных");
  Document d=Jsoup.parse(html,url);
  if(d.title().matches("(?iu).*(?:captcha|access denied|just a moment|проверка браузера).*"))
   throw new IllegalArgumentException("Справочник временно недоступен");
  d.select("header,footer,aside,[class*=recommend],[class*=comment],[class*=similar]").remove();
  if(!navigation)d.select("nav").remove();
  return d;
 }
 private static List<String> authors(Element root,String selector){
  Set<String> out=new LinkedHashSet<>();
  for(Element e:root.select(selector)){
   String value=e.hasAttr("content")?e.attr("content"):e.text();
   if(!value.trim().isEmpty())out.add(value.trim());
  }
  return new ArrayList<>(out);
 }
 private static Element seriesLink(Element root,String selector,String provider){
  for(Element a:root.select(selector)){
   String address=a.absUrl("href");
   if(SeriesAuthorityRules.cycle(address)&&SeriesAuthorityRules.provider(address).equals(provider))return a;
  }
  return null;
 }
 public static Record detail(String html,String url){
  if(!SeriesAuthorityRules.work(url))throw new IllegalArgumentException("Не страница книги справочника");
  Document d=document(html,url,false);String title=text(d,"h1");
  if(title.isEmpty())throw new IllegalArgumentException("Нет названия книги");
  String provider=SeriesAuthorityRules.provider(url);
  Element scope=detailScope(d,provider);List<String> writers=detailAuthors(scope,provider);
  Element cycle=seriesLink(scope,provider.equals("Author.Today")?"a[href]":
   "[data-testid='book__series--title'] a[href],[data-testid='book__seriesSlider--title'] a[href]",provider);
  if(writers.isEmpty())throw new IllegalArgumentException("Нет автора произведения");
  if(cycle==null)throw new IllegalArgumentException("Справочник не указал прямую серию");
  String name=label(cycle);
  if(!SeriesAuthorityRules.plausibleSeriesTitle(name))throw new IllegalArgumentException("Нет названия серии");
  return new Record(title,SeriesAuthorityRules.canonical(url),writers,name,
   SeriesAuthorityRules.canonical(cycle.absUrl("href")),order(cycle.parent().text()),"","");
 }
 private static Element detailScope(Document d,String provider){
  if(!provider.equals("Author.Today"))return d;
  Element scope=d.selectFirst(".book-meta-panel,.work-header-content");
  if(scope==null)throw new IllegalArgumentException("Нет метаданных произведения");
  return scope;
 }
 private static List<String> detailAuthors(Element scope,String provider){
  return authors(scope,provider.equals("Author.Today")?
   ".book-authors [itemprop=name][content],.book-author a,.book-authors a,.card-author [itemprop=name][content],.card-author a":
   "[data-testid='book__author--link'],[data-testid='book__author'] a,[data-testid='book__authors'] a,[itemprop=author] [itemprop=name],a[data-testid*=author][href^=/author/]");
 }
 private static Element[] titleAndLink(Element card,boolean at){
  Element title=card.selectFirst(at?".book-title a[href],.work-title a[href]":"[data-testid='art__title'][href]");
  if(title!=null)return new Element[]{title,title};
  if(!at)return new Element[]{null,null};
  return new Element[]{card.selectFirst(".work-title"),card.closest("a.work-row[href]")};
 }
 private static boolean ownWork(Element link,String provider){
  if(link==null)return false;String address=link.absUrl("href");
  return SeriesAuthorityRules.work(address)&&SeriesAuthorityRules.provider(address).equals(provider);
 }
 private static String cardTitle(Element node){
  Element copy=node.clone();copy.select(".label-row-index").remove();return copy.text().trim();
 }
 private static Double cardOrder(Element card){
  String value=text(card,".label-row-index,[data-testid='book__seriesCounter--text']").replace(',','.');
  if(!value.matches("\\d+(?:\\.\\d+)?"))return null;
  double n=Double.parseDouble(value);return n>0&&n<100000?n:null;
 }
 private static Listed listing(Element card,String provider){
  boolean at=provider.equals("Author.Today");Element[] nodes=titleAndLink(card,at);
  if(nodes[0]==null||!ownWork(nodes[1],provider))return null;
  List<String> writers=authors(card,at?".book-author a,.work-author":"[data-testid='art__authorName--link']");
  if(writers.isEmpty())return null;
  Element series=seriesLink(card,"a[href]",provider);
  Record r=new Record(cardTitle(nodes[0]),SeriesAuthorityRules.canonical(nodes[1].absUrl("href")),writers,
   series==null?"":label(series),series==null?"":SeriesAuthorityRules.canonical(series.absUrl("href")),
   cardOrder(card),"","");
  return new Listed(r,card);
 }
 static List<Listed> listed(Document d,String url){
  String provider=SeriesAuthorityRules.provider(url);Map<String,Listed> out=new LinkedHashMap<>();
  String selector=provider.equals("Author.Today")?".book-row-content,.work-row-content":"[data-testid='art__wrapper']";
  for(Element card:d.select(selector)){
   if(card.closest("nav")!=null)continue;
   Listed row=listing(card,provider);if(row==null)continue;
   if(!out.containsKey(row.record.bookUrl))out.put(row.record.bookUrl,row);
   if(out.size()>=80)break;
  }
  return new ArrayList<>(out.values());
 }
 public static List<Record> search(String html,String url){
  List<Record> out=new ArrayList<>();for(Listed row:listed(document(html,url,false),url))out.add(row.record);
  return out;
 }
 public static Record parent(String html,String url,Record book){
  if(!SeriesAuthorityRules.cycle(url)||!SeriesAuthorityRules.provider(url).equals("Литрес"))return book;
  Document d=document(html,url,false);String heading=SeriesAuthorityRules.seriesLabel(text(d,"h1"));
  if(!SeriesAuthorityRules.series(heading).equals(SeriesAuthorityRules.series(book.seriesTitle)))return book;
  Element a=seriesLink(d,"[data-testid='series__header--parent'] a[href]","Литрес");if(a==null)return book;
  return new Record(book.title,book.bookUrl,book.authors,book.seriesTitle,book.seriesUrl,book.order,
   label(a),SeriesAuthorityRules.canonical(a.absUrl("href")));
 }
 private static void requireComplete(Document d){
  if(!d.select("a[rel=next],.pagination a,.paginator a,[data-testid*=pagination] a[href]").isEmpty())
   throw new IllegalArgumentException("Список серии содержит дополнительные страницы");
  for(Element button:d.select("button,[role=button]"))
   if(button.text().matches("(?iu).*(?:показать|загрузить)\\s+(?:ещё|еще).*"))
    throw new IllegalArgumentException("Справочник не раскрыл полный список серии");
 }
 public static List<Record> members(String html,String url,String expected){
  if(!SeriesAuthorityRules.cycle(url))throw new IllegalArgumentException("Не серия справочника");
  Document d=document(html,url,true);String heading=SeriesAuthorityRules.seriesLabel(text(d,"h1"));
  if(!SeriesAuthorityRules.series(heading).equals(SeriesAuthorityRules.series(expected)))
   throw new IllegalArgumentException("Справочник вернул другую серию");
  requireComplete(d);List<Record> out=new ArrayList<>();
  for(Listed row:listed(d,url)){
   Record r=row.record;out.add(new Record(r.title,r.bookUrl,r.authors,expected,url,r.order,"",""));
  }
  if(out.isEmpty()||out.size()>=80)throw new IllegalArgumentException("Не удалось прочитать полный список серии");
  return out;
 }
 public static boolean matches(String title,String author,Record r){
  return SeriesAuthorityRules.sameAuthor(author,r.authors)&&SeriesAuthorityRules.sameTitle(title,r.title,r.seriesTitle);
 }
}
