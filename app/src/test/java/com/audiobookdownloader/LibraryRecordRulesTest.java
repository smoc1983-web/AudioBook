package com.audiobookdownloader;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class LibraryRecordRulesTest {
 private final String a=ChapterIdentity.edition("Old","https://source.example/a"),b=ChapterIdentity.edition("Old","https://source.example/b");
 private String revision(String id,List<String> titles){List<String> out=new ArrayList<>();for(String t:titles)out.add(ChapterIdentity.chapter(id,t));return ChapterIdentity.revision(out);}
 @Test public void libraryEntryIdsAreStableAcrossDisplayTitleChanges() {
  assertEquals(LibraryRecordRules.bookId(a),LibraryRecordRules.bookId(ChapterIdentity.edition("Renamed","https://source.example/a/")));
  assertNotEquals(LibraryRecordRules.bookId(a),LibraryRecordRules.bookId(b));assertTrue(LibraryRecordRules.bookId(a).matches("b3-[a-f0-9]{64}"));
 }
 @Test public void missingEditionCannotBecomeSharedLibraryId() {
  for(String s:new String[]{null,"","   "})try{LibraryRecordRules.bookId(s);fail("Missing identity accepted");}catch(IllegalArgumentException expected){}
 }
 @Test public void titleCatalogueCanOnlyBeMigratedToItsExactEdition() {
  List<String> names=Arrays.asList("01","02");String rev=revision(a,names);
  assertTrue(LibraryRecordRules.provesCatalogue(a,names,rev));assertFalse(LibraryRecordRules.provesCatalogue(b,names,rev));
  assertFalse(LibraryRecordRules.provesCatalogue(a,names,"damaged"));assertFalse(LibraryRecordRules.provesCatalogue(a,Collections.emptyList(),revision(a,Collections.emptyList())));
 }
 @Test public void duplicateAndMissingChapterNamesCannotProveLegacyOwnership() {
  for(List<String> names:Arrays.asList(Arrays.asList("01","01"),Arrays.asList("01"," "),Arrays.asList("01","")))assertFalse(LibraryRecordRules.provesCatalogue(a,names,revision(a,names)));
  assertFalse(LibraryRecordRules.provesCatalogue(a,Arrays.asList("01",null),""));
 }
 @Test public void existingChapterIdsAndRevisionSemanticsStayUnchanged() {
  List<String> names=Arrays.asList("01","02");String rev=revision(a,names);assertTrue(LibraryRecordRules.provesCatalogue(a,Arrays.asList("02","01"),rev));
  assertEquals("c2-"+ChapterIdentity.hash((a.length()+":"+a)+(2+":"+"01")),ChapterIdentity.chapter(a,"01"));
 }
}
