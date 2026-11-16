package com.audiobookdownloader;
import org.junit.Test;import static org.junit.Assert.*;
public class DiagnosticPrivacyTest {
 @Test public void removesSecretsAndPrivateAddresses() {
  String s=DiagnosticPrivacy.redact("Authorization: Bearer fixture-secret\nCookie: SID=fixture-cookie\nURL https://user:fixture-pass@cdn.example/chapter/private-book?token=fixture-token#secret\nfile:///storage/emulated/0/PrivateBook/a.mp3\ncontent://provider/document/private\nemail fixture@example.org");
  for(String secret:new String[]{"fixture-secret","fixture-cookie","fixture-pass","private-book","fixture-token","PrivateBook","provider/document","fixture@example.org"})assertFalse(secret,s.contains(secret));
  assertTrue(s.contains("cdn.example"));
 }
 @Test public void removesQuotedAndEscapedSecrets() {
  String s=DiagnosticPrivacy.redact("{\"access_token\":\"fixture token with spaces\",\"password\":\"fixture-pass\",\"query\":\"private search\",\"url\":\"https:\\/\\/cdn.example/book?sig=fixture-signature\"}");
  for(String secret:new String[]{"fixture token","fixture-pass","private search","fixture-signature"})assertFalse(secret,s.contains(secret));
 }
 @Test public void keepsTechnicalEvidenceAndIsIdempotent() {
  String s="java.net.SocketTimeoutException: timeout HTTP 503\n at com.audiobookdownloader.Source.load(Source.kt:42)\nAndroid 36; source=Aume; count=2";
  assertEquals(s,DiagnosticPrivacy.redact(s));assertEquals("",DiagnosticPrivacy.redact(null));
  String once=DiagnosticPrivacy.redact("https://cdn.example/private?token=fixture");assertEquals(once,DiagnosticPrivacy.redact(once));
 }
}
