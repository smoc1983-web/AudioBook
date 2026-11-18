package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class LibraryRootRulesTest {
 @Test public void onlyBoundedRealContentTreeUrisAreAccepted() {
  assertEquals("provider\u0000primary:Books",LibraryRootRules.treeKey("content://provider/tree/primary%3ABooks"));
  for(String s:new String[]{null,"","file:///Books","https://provider/tree/x","content://user@provider/tree/x","content://provider:80/tree/x","content://provider/tree/x?token=secret","content://provider/tree/x#fragment","content://provider/document/x","content://provider/tree/","content://provider/tree/x/extra","content://provider/tree/root/","content://provider/tree/root/document/child/extra","content://provider/tree/root/other/child","content://provider/tree/%00","content://provider/tree/%ZZ","content://provider/tree/root/document/","content://provider/tree//document/child"})assertEquals(String.valueOf(s),"",LibraryRootRules.treeKey(s));
  String prefix="content://provider/tree/";assertFalse(LibraryRootRules.treeKey(prefix+String.join("",Collections.nCopies(8192-prefix.length(),"x"))).isEmpty());
  assertEquals("",LibraryRootRules.treeKey("content://provider/tree/"+String.join("",Collections.nCopies(8192,"x"))));
 }
 @Test public void encodedAliasesIdentifyOneTreeButRawPathsDoNotEscapeTheTreeSegment() {
  assertEquals(LibraryRootRules.treeKey("content://provider/tree/root%2Fbooks"),LibraryRootRules.treeKey("content://provider/tree/root%2fbooks"));
  assertEquals("provider\u0000root+books",LibraryRootRules.treeKey("content://provider/tree/root+books"));
  assertEquals("",LibraryRootRules.treeKey("content://provider/tree/root/books"));
  assertEquals("provider\u0000root",LibraryRootRules.treeKey("content://provider/tree/root/document/child"));
 }
 @Test public void documentIdentityIsIndependentOfTheGrantRootAndNotOfTheAuthority() {
  String a="content://provider/tree/root/document/root%2Fbook";
  String b="content://provider/tree/root%2Fbook/document/root%2Fbook";
  assertEquals(LibraryRootRules.documentKey(a),LibraryRootRules.documentKey(b));
  assertNotEquals(LibraryRootRules.documentKey(a),LibraryRootRules.documentKey(a.replace("provider","another")));
  assertEquals("",LibraryRootRules.documentKey("content://provider/tree/root"));
  assertNotEquals(LibraryRootRules.documentKey("content://provider/tree/root/document/Aa"),LibraryRootRules.documentKey("content://provider/tree/root/document/BB"));
 }
 @Test public void additionIsIdempotentPreservesOrderAndDoesNotReplaceExistingRoots() {
  String a="content://provider/tree/a",b="content://provider/tree/b";
  assertEquals(Arrays.asList(a,b),LibraryRootRules.add(Arrays.asList(a),b));
  assertEquals(Arrays.asList(a,b),LibraryRootRules.add(Arrays.asList(a,b),a));
  assertEquals(Arrays.asList("content://provider/tree/%61"),LibraryRootRules.add(Arrays.asList("content://provider/tree/%61"),a));
  try{LibraryRootRules.add(Arrays.asList(a),"file:///bad");fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void rootLimitDoesNotEvictEarlierFoldersAndDuplicateAtLimitIsAllowed() {
  List<String> rows=new ArrayList<>();for(int n=0;n<32;n++)rows.add("content://provider/tree/"+n);
  assertEquals(rows,LibraryRootRules.add(rows,rows.get(0)));
  try{LibraryRootRules.add(rows,"content://provider/tree/extra");fail();}catch(IllegalArgumentException expected){}
  assertEquals(32,rows.size());
 }
}
