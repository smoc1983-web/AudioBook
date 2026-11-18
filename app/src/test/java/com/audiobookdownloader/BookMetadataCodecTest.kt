package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class BookMetadataCodecTest {
 private val id="b3-"+"a".repeat(64)
 private val value=BookDisplay("Моё название","Автор","Чтец","Моя серия","2.5")
 @Test fun roundTripIncludesExplicitEmptyFields() {
  for(v in listOf(value,BookDisplay("Имя")))assertEquals(v,BookMetadata.decode(id,BookMetadata.encode(id,v)))
 }
 @Test fun corruptAndWrongIdentityRecordsAreRejected() {
  val raw=BookMetadata.encode(id,value)
  val bad=listOf("{}","{bad",raw+" trailing",JSONObject(raw).put("schema",2).toString(),JSONObject(raw).put("id","l3-"+"b".repeat(64)).toString(),JSONObject(raw).put("title",8).toString(),JSONObject(raw).put("author",JSONObject.NULL).toString(),JSONObject(raw).put("volume","0").toString(),JSONObject(raw).put("series","").toString())
  for(row in bad)try{BookMetadata.decode(id,row);fail("Invalid metadata accepted")}catch(_:IllegalArgumentException){}catch(_:org.json.JSONException){}catch(_:IllegalStateException){}
 }
}
