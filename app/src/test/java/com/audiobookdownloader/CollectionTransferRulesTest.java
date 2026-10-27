package com.audiobookdownloader;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class CollectionTransferRulesTest {
 private String shelf(String name,String title,String url)throws Exception{return new JSONArray().put(new JSONObject().put("n",name).put("b",new JSONArray().put(new JSONObject().put("t",title).put("u",url).put("c","")))).toString();}
 @Test public void actualLegacyFormatAndXmlAreRead()throws Exception{String raw=shelf("Любимое","Книга","https://bookinfo.net/a/b");String xml="<?xml version=\"1.0\"?><map><string name=\"private_token\">never-import</string><string name=\"shelves_v1\">"+raw.replace("&","&amp;").replace("<","&lt;")+"</string></map>";assertEquals(CollectionTransferRules.parse(raw).toString(),CollectionTransferRules.parse(xml).toString());assertFalse(CollectionTransferRules.export(xml).contains("never-import"));}
 @Test public void exportRoundTripIsVersionedAndOnlyCollections()throws Exception{String raw=shelf("Циклы","Том 1","");JSONObject out=new JSONObject(CollectionTransferRules.export(raw));assertEquals("AudiobookCollections",out.getString("format"));assertEquals(1,out.getInt("version"));assertEquals(3,out.length());assertEquals(CollectionTransferRules.parse(raw).toString(),CollectionTransferRules.parse(out.toString()).toString());}
 @Test public void mergeKeepsExistingAndAddsNewCollection()throws Exception{var r=CollectionTransferRules.merge(shelf("Мои","Первая","https://bookinfo.net/a/b"),shelf("Старые","Вторая",""));assertEquals(1,r.addedShelves);assertEquals(1,r.addedBooks);assertEquals(2,new JSONArray(r.collections).length());assertTrue(r.collections.contains("Первая"));}
 @Test public void sameNamedShelfMergesBooksWithoutDuplicates()throws Exception{var r=CollectionTransferRules.merge(shelf(" Фантастика ","Дюна",""),shelf("фантастика","Другой том",""));assertEquals(0,r.addedShelves);assertEquals(1,r.addedBooks);assertEquals(2,new JSONArray(r.collections).getJSONObject(0).getJSONArray("b").length());}
 @Test public void repeatImportIsIdempotent()throws Exception{String incoming=shelf("История","Книга","");var first=CollectionTransferRules.merge("[]",incoming);var again=CollectionTransferRules.merge(first.collections,incoming);assertEquals(0,again.addedBooks);assertEquals(0,again.addedShelves);assertEquals(first.collections,again.collections);}
 @Test public void missingReferenceFilledButExistingSourceKept()throws Exception{var fill=CollectionTransferRules.merge(shelf("Мои","Книга",""),shelf("Мои","Книга","https://bookinfo.net/old/source"));assertEquals(1,fill.filledReferences);var keep=CollectionTransferRules.merge(fill.collections,shelf("Мои","Книга","https://other.example/new"));assertEquals(0,keep.filledReferences);assertTrue(keep.collections.contains("https://bookinfo.net/old/source"));}
 @Test public void unicodeNamesAndRepeatedInputShelvesStayUnique()throws Exception{String a=shelf("Cafe\u0301","Книга",""),b=shelf("Café","книга","");var r=CollectionTransferRules.merge(a,b);assertEquals(0,r.addedShelves);assertEquals(0,r.addedBooks);}
 @Test public void emptyCollectionsAreRetained()throws Exception{String raw="[{\"n\":\"Пока пусто\",\"b\":[]}]";assertEquals(1,new JSONArray(CollectionTransferRules.merge("[]",raw).collections).length());assertEquals("[]",CollectionTransferRules.merge("[]","[]").collections);}
 @Test public void unsafeUrlsAndNonStringFieldsAreRejected()throws Exception{for(String raw:new String[]{shelf("Мои","Книга","javascript:alert(1)"),shelf("Мои","Книга","file:///data/private"),shelf("Мои","Книга","https://user:pass@bookinfo.net/a"),"[{\"n\":123,\"b\":[]}]","[{\"n\":\"x\",\"b\":[{\"t\":{}}]}]"})reject(raw);}
 @Test public void corruptUnrelatedAndUnsupportedFilesAreRejected()throws Exception{for(String raw:new String[]{"broken","[] trailing","{}","[{\"n\":\"x\"}]","{\"format\":\"AudiobookCollections\",\"version\":2,\"collections\":[]}","<map><string name=\"other\">[]</string></map>","<!DOCTYPE x><map><string name=\"shelves_v1\">[]</string></map>"})reject(raw);}
 @Test public void duplicatedXmlKeyIsRejected()throws Exception{reject("<map><string name=\"shelves_v1\">[]</string><string name=\"shelves_v1\">[]</string></map>");}
 @Test public void documentAndStructuralBoundsAreEnforced()throws Exception{reject(" ".repeat(CollectionTransferRules.MAX_BYTES+1));reject("[".repeat(80)+"]".repeat(80));JSONArray a=new JSONArray();for(int i=0;i<257;i++)a.put(new JSONObject().put("n","x"+i).put("b",new JSONArray()));reject(a.toString());}
 @Test(timeout=3000) public void deeplyNestedXmlWithUniqueNamespacesIsRejectedBeforeBuildingATree()throws Exception{
  StringBuilder xml=new StringBuilder("<map><string name='shelves_v1'>[]</string>");
  for(int i=0;i<100;i++)xml.append("<n xmlns:p").append(i).append("='urn:").append(i).append("'>");
  xml.append("x").append("</n>".repeat(100)).append("</map>");reject(xml.toString());
 }
 @Test public void malformedXmlAndNestedMarkupInPreferencesAreRejected()throws Exception{
  reject("<map><string name='shelves_v1'>[]</string>");
  reject("<map><string name='shelves_v1'><hidden>[]</hidden></string></map>");
 }
 @Test public void exportedPreferencesJsonFieldIsAccepted()throws Exception{String s=shelf("Мои","Книга","");assertEquals(CollectionTransferRules.parse(s).toString(),CollectionTransferRules.parse(new JSONObject().put("shelves_v1",s).put("unrelated",true).toString()).toString());}
 @Test public void bomAndEscapedNamesSurviveRoundTrip()throws Exception{String s=shelf("Книги & \"цитаты\"","Название <том>","");assertEquals(CollectionTransferRules.parse(s).toString(),CollectionTransferRules.parse("\ufeff"+CollectionTransferRules.export(s)).toString());}
 @Test public void invalidNamesAndOversizedReferencesNeverReachCollections()throws Exception{
  for(Object name:new Object[]{JSONObject.NULL,""," \t ","x".repeat(201),"x\u0000y"}){
   JSONArray rows=new JSONArray(shelf("Мои","Книга",""));rows.getJSONObject(0).put("n",name);reject(rows.toString());
  }
  for(Object title:new Object[]{JSONObject.NULL,"","x".repeat(1001),"x\u0000y"}){
   JSONArray rows=new JSONArray(shelf("Мои","Книга",""));rows.getJSONObject(0).getJSONArray("b").getJSONObject(0).put("t",title);reject(rows.toString());
  }
  JSONArray rows=new JSONArray(shelf("Мои","Книга",""));rows.getJSONObject(0).getJSONArray("b").getJSONObject(0).put("u","https://bookinfo.net/"+"x".repeat(8192));reject(rows.toString());
 }
 @Test public void optionalNullReferencesAreEmptyAndXmlCdataPreservesNames()throws Exception{
  JSONArray rows=new JSONArray(shelf("Мои","Книга",""));rows.getJSONObject(0).getJSONArray("b").getJSONObject(0).put("u",JSONObject.NULL).put("c",JSONObject.NULL);
  assertEquals("",CollectionTransferRules.parse(rows.toString()).getJSONObject(0).getJSONArray("b").getJSONObject(0).getString("u"));
  String raw=shelf("Книги & цитаты","Название <том>","");
  assertEquals(CollectionTransferRules.parse(raw).toString(),CollectionTransferRules.parse("<map><string name='shelves_v1'><![CDATA["+raw+"]]></string></map>").toString());
  reject("<wrong><string name='shelves_v1'>[]</string></wrong>");
  reject("<map xmlns='urn:unexpected'><string name='shelves_v1'>[]</string></map>");
 }
 @Test public void unrelatedXmlPreferencesAfterCollectionsAreIgnoredAndDepthBudgetIsExact()throws Exception{
  String pref="<map><string name='shelves_v1'>[]</string><string name='private_key'>must-not-import</string>";
  assertEquals("[]",CollectionTransferRules.parse(pref+"<x>".repeat(31)+"</x>".repeat(31)+"</map>").toString());
  reject(pref+"<x>".repeat(32)+"</x>".repeat(32)+"</map>");
 }
 private void reject(String s)throws Exception{try{CollectionTransferRules.parse(s);fail("Invalid input accepted");}catch(Exception expected){}}
}
