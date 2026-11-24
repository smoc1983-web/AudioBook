package com.audiobookdownloader;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class BackgroundUpdateTransferTest {
 @Test public void persistedReceiptRevalidatesChannelAndDigest()throws Exception{
  JSONObject release=GitHubReleaseCatalogTest.release();
  String metadata=PreviewUpdateRulesTest.valid().toString();
  String receipt=new JSONObject().put("release",release).put("metadata",metadata).toString();
  PreviewUpdater.Release restored=BackgroundUpdateTransfer.decode(receipt,"notes");
  assertEquals(100999,restored.info.versionCode);assertEquals("notes",restored.notes);
  String wrong=new JSONObject().put("release",release).put("metadata",new JSONObject(metadata).put("packageName","other").toString()).toString();
  PreviewUpdateRulesTest.rejected(()->BackgroundUpdateTransfer.decode(wrong,""));
 }
}
