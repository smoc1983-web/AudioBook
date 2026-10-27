package com.audiobookdownloader;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LocalSeriesRulesTest {
 private ContinuationRules.Book book(String title){return new ContinuationRules.Book(title,"url","Автор",null);}
 @Test public void sourceGroupingRequiresEveryBookAndTheSeed(){
  assertTrue(LocalSeriesRules.sourceCycleConsistent("Путь","Путь",List.of(book("Путь"),book("Путь. Том 2"))));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("Путь","Путь",List.of(book("Путь. Том 2"))));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("Путь","Путь",List.of(book("Путь"),book("Чужая книга"))));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("","Путь",List.of(book("Путь"))));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("Чужой цикл","Путь",List.of(book("Путь"))));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("Путь","Путь",List.of()));
 }
 @Test public void broadSourceWorldsNeverBecomeLocalSeries(){
  List<ContinuationRules.Book> books=new ArrayList<>();for(int i=0;i<11;i++)books.add(book(i==0?"Путь":"Путь. Том "+(i+1)));
  assertFalse(LocalSeriesRules.sourceCycleConsistent("Путь","Путь",books));
 }
 @Test public void numberedSiblingsKeepVolumeOrderAndPlainFirstBook(){
  assertEquals(List.of("Путь","Путь. Том 2","Путь. Том 3"),LocalSeriesRules.siblings("Путь",List.of("Чужая книга","Путь. Том 3","Путь. Том 2","Путь")));
  assertTrue(LocalSeriesRules.siblings("Путь",List.of("Путь")).isEmpty());
  assertTrue(LocalSeriesRules.siblings("",List.of("Путь. Том 2")).isEmpty());
 }
}
