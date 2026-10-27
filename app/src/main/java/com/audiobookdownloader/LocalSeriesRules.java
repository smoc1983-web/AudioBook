package com.audiobookdownloader;
import java.util.*;
/** Local filename grouping never proves a publication or creates an alert. API 23 compatible. */
public final class LocalSeriesRules {
 public static String base(String title){
  String key=SearchBookIndex.titleKey(SeriesAuthorityRules.queryTitle(title));
  return key.replaceFirst("\\s+(?:том|книга)\\s+\\d+(?:\\s.*)?$","").trim();
 }
 public static boolean sourceCycleConsistent(String cycle,String seed,Collection<ContinuationRules.Book> books){
  String group=SeriesAuthorityRules.series(cycle);
  if(group.isEmpty()||!group.equals(base(seed))||books.isEmpty()||books.size()>10)return false;
  boolean present=false;
  for(ContinuationRules.Book book:books){
   if(!group.equals(base(book.title)))return false;
   if(SeriesAuthorityRules.sameTitle(seed,book.title,cycle))present=true;
  }
  return present;
 }
 private static double volume(String title){
  Double order=ContinuationRules.order(title);return order==null?1:order;
 }
 public static List<String> siblings(String title,Collection<String> titles){
  String group=base(title);if(group.isEmpty())return Collections.emptyList();
  List<String> found=new ArrayList<>();boolean numbered=false;
  for(String candidate:titles){
   if(!group.equals(base(candidate)))continue;
   found.add(candidate);if(ContinuationRules.order(candidate)!=null)numbered=true;
  }
  if(!numbered)return Collections.emptyList();
  Collections.sort(found,(a,b)->Double.compare(volume(a),volume(b)));
  return found;
 }
}
