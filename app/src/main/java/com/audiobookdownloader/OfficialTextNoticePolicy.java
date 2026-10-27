package com.audiobookdownloader;

import java.util.ArrayList;
import java.util.List;

/** All books of one verified series share a baseline, independently of the selected seed. */
public final class OfficialTextNoticePolicy {
 public static ContinuationRules.Update evaluate(ContinuationRules.State previous,
   List<ContinuationRules.Book> published,double seedOrder,String author){
  ContinuationRules.Update all=ContinuationRules.evaluate(previous,published,author);
  List<ContinuationRules.Book> later=new ArrayList<>();
  for(ContinuationRules.Book book:all.additions)if(book.order!=null&&book.order>seedOrder)later.add(book);
  return new ContinuationRules.Update(all.state,later);
 }
}
