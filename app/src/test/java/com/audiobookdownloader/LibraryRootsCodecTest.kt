package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import org.json.JSONArray

class LibraryRootsCodecTest {
 @Test fun multipleRootsRoundTripWithoutRewritingLegacyUriBytes() {
  val rows=listOf("content://provider/tree/%61","content://provider/tree/b")
  assertEquals(rows,LibraryRoots.decode(LibraryRoots.encode(rows)))
  assertEquals(emptyList<String>(),LibraryRoots.decode(LibraryRoots.encode(emptyList())))
 }
 @Test fun malformedOrDuplicateConfigurationIsRejectedInsteadOfSilentlyReturningEmpty() {
  val values=listOf("[]","{}","{broken}","{\"schema\":1,\"roots\":[]}tail",JSONObject().put("schema",2).put("roots",JSONArray()).toString(),
   JSONObject().put("schema",1).put("roots",JSONArray(listOf("file:///bad"))).toString(),
   JSONObject().put("schema",1).put("roots",JSONArray(listOf("content://provider/tree/a","content://provider/tree/%61"))).toString(),
   JSONObject().put("schema",1).put("roots",JSONArray(listOf(17))).toString())
  for(raw in values)try{LibraryRoots.decode(raw);fail("Invalid root configuration accepted")}catch(_:Exception){}
 }
 @Test fun rootLimitIsStrictAndDamagedSettingsAreNotTruncated() {
  val rows=(0..31).map{"content://provider/tree/$it"}
  assertEquals(rows,LibraryRoots.decode(LibraryRoots.encode(rows)))
  try{LibraryRoots.decode(LibraryRoots.encode(rows+"content://provider/tree/extra"));fail()}catch(_:Exception){}
 }
}
