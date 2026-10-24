package com.audiobookdownloader;
import java.util.*;
import java.util.regex.*;

/** One first-success baseline; stable URL/title/volume aliases suppress alternate narrations. */
public final class ContinuationRules {
 public static final class Book {
  public final String title,url,author;public final Double order;
  public Book(String title,String url,String author,Double order){this.title=title;this.url=url;this.author=author;this.order=order;}
 }
 public static final class State {
  public final Set<String> seen;public final Double highest;
  public State(Set<String> seen,Double highest){this.seen=new LinkedHashSet<>(seen);this.highest=highest;}
 }
 public static final class Update {
  public final State state;public final List<Book> additions;
  Update(State state,List<Book> additions){this.state=state;this.additions=additions;}
 }
 public static String author(String value){return SearchBookIndex.keys("",value).author;}
 public static String seriesName(String value){return value.replaceFirst("(?iu)^\\s*(?:серия|цикл)\\s*[:—–-]?\\s*","").replaceFirst("\\s*(?:\\(?#\\s*[0-9]+(?:[.,][0-9]+)?\\)?)\\s*$","").trim();}
 public static Double order(String title){
  Matcher m=Pattern.compile("(?:том|книга|часть|vol|book)\\s+([0-9]+(?:[.,][0-9]+)?)").matcher(SearchBookIndex.titleKey(title));
  if(!m.find())return null;try{return Double.valueOf(m.group(1).replace(',','.'));}catch(Exception e){return null;}
 }
 public static Set<String> aliases(Book book){
  Set<String> ids=new LinkedHashSet<>();ids.add("u:"+book.url);ids.add("t:"+SearchBookIndex.titleKey(book.title));
  if(book.order!=null&&Double.isFinite(book.order)&&book.order>0)ids.add("v:"+Double.toString(book.order));return ids;
 }
 public static Update evaluate(State previous,List<Book> books,String expectedAuthor){
  Set<String> seen=previous==null?new LinkedHashSet<>():new LinkedHashSet<>(previous.seen);
  Double highest=previous==null?null:previous.highest;List<Book> added=new ArrayList<>();String wanted=author(expectedAuthor);
  for(Book book:books){
   if(book.title.trim().isEmpty()||book.url.isEmpty())continue;
   String actual=author(book.author);if(!wanted.isEmpty()&&!actual.isEmpty()&&!wanted.equals(actual))continue;
   Set<String> ids=aliases(book);boolean known=!Collections.disjoint(ids,seen);
   // Newly re-listed old volumes never masquerade as new continuations.
   boolean later=book.order==null||previous==null||previous.highest==null||book.order>previous.highest;
   if(previous!=null&&!known&&later)added.add(book);
   seen.addAll(ids);if(book.order!=null&&(highest==null||book.order>highest))highest=book.order;
  }
  return new Update(new State(seen,highest),added);
 }
}
