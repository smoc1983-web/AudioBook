package com.audiobookdownloader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SeekHistoryCodecTest {
    private val edition=ChapterIdentity.edition("Book","https://source.example/book")
    private fun point(n:Int=1)=SeekHistoryRules.Entry(n.toString(16).padStart(32,'0'),edition,ChapterIdentity.chapter(edition,"01"),0,Int.MAX_VALUE,0)
    private fun rejects(raw:String) {
        try{SeekHistory.decode(raw);fail("Malformed history accepted")}catch(_:IllegalArgumentException){}catch(_:org.json.JSONException){}
    }
    @Test fun exactPositionsAndInsertionOrderSurviveEncodingWithoutSourcesOrCredentials() {
        val rows=listOf(point(2),point(1));val raw=SeekHistory.encode(rows);val read=SeekHistory.decode(raw)
        assertEquals(rows.map{it.id},read.map{it.id});assertEquals(0,read[0].from);assertEquals(Int.MAX_VALUE,read[0].to)
        assertEquals(0L,read[0].time);assertFalse(raw.contains("https://"));assertFalse(raw.contains("token"));assertTrue(SeekHistory.decode(SeekHistory.encode(emptyList())).isEmpty())
    }
    @Test fun corruptSchemaFractionalPositionsTrailingDataAndDuplicateRowsAreRejected() {
        val good=SeekHistory.encode(listOf(point()))
        for(raw in listOf("broken",good+" junk",good+'\u0000',"x".repeat(65537),
            good.replace("\"schema\":1","\"schema\":1.5"),good.replace("\"from\":0","\"from\":0.5"),
            good.replace("\"from\":0","\"from\":-1"),good.replace("\"to\":2147483647","\"to\":2147483648")))rejects(raw)
        rejects(SeekHistory.encode(listOf(point(),point())))
        rejects(SeekHistory.encode((1..51).map{point(it)}))
        val root=JSONObject(good);root.getJSONArray("entries").getJSONObject(0).put("chapter","missing");rejects(root.toString())
    }
}
