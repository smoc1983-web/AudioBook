package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;

public class BookMetadataRulesTest {
 @Test public void acceptsOnlyStableBookIdentities() {
  assertTrue(BookMetadataRules.validId("b3-"+"a".repeat(64)));
  assertTrue(BookMetadataRules.validId("l3-"+"0".repeat(64)));
  for(String bad:new String[]{null,"","title","../book","b3-"+"g".repeat(64),"b3-"+"a".repeat(63),"e2-"+"a".repeat(64)})assertFalse(BookMetadataRules.validId(bad));
 }
 @Test public void titlesAreRequiredAndBoundedButOtherFieldsMayBeEmpty() {
  assertTrue(BookMetadataRules.validText("Книга № 1",true));
  assertTrue(BookMetadataRules.validText("",false));
  assertTrue(BookMetadataRules.validText("a".repeat(512),true));
  for(String bad:new String[]{null,"","  ","a".repeat(513),"title\nchapter","tab\t","nul\u0000"})assertFalse(BookMetadataRules.validText(bad,true));
  assertFalse(BookMetadataRules.validText(null,false));
  assertFalse(BookMetadataRules.validText("\nfirst",false));
 }
 @Test public void volumeIsOptionalPositiveDecimalAndNeverSpecialFloatingPoint() {
  for(String ok:new String[]{"","1","0.5","12.25","9999.99"})assertTrue(BookMetadataRules.validVolume(ok));
  for(String bad:new String[]{null,"0","-1","NaN","Infinity","1e3","1,5","1.","10000","1.123"," "})assertFalse(BookMetadataRules.validVolume(bad));
 }
}
