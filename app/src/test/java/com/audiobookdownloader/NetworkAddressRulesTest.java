package com.audiobookdownloader;
import java.net.*;import java.io.*;import org.junit.Test;import static org.junit.Assert.*;
public class NetworkAddressRulesTest {
 interface Action {void run()throws Exception;}
 static void rejected(Action f)throws Exception {try{f.run();fail("Unsafe address accepted");}catch(IOException expected){}}
 @Test public void rejectsMalformedAndCredentialAddresses()throws Exception {
  for(String s:new String[]{"file:///tmp/a","https://user:pass@example.org/a","https:///missing","https://example.org/a\\b","https://example.org/a%0d%0aX:secret","https://example.org/a\nX:secret"})rejected(()->NetworkAddressRules.checked(s));
 }
 @Test public void deniesDowngradeAndLocalRedirects()throws Exception {
  URL from=new URL("https://books.example/book");
  for(String s:new String[]{"http://cdn.example/audio","https://127.0.0.1/a","https://[::1]/a","https://192.168.1.2/a","https://169.254.169.254/a","https://localhost/a","https://router.local/a","https://2130706433/a","https://0x7f000001/a","https://[fc00::1]/a","https://[fe80::1]/a","https://127.000.0.1/a","https://127.1/a","https://[::ffff:127.0.0.1]/a","https://localhost./a","https://10.1.2.3/a","https://172.31.1.1/a"})rejected(()->NetworkAddressRules.redirect(from,s));
  rejected(()->NetworkAddressRules.redirect(from,null));
 }
 @Test public void validatesReferrerAndOriginsWithoutRemovingPlaybackQueries()throws Exception {
  assertFalse(NetworkAddressRules.accepts(null));assertFalse(NetworkAddressRules.publicAddress("file:///a"));assertFalse(NetworkAddressRules.publicAddress("http://127.0.0.1/a"));assertTrue(NetworkAddressRules.publicAddress("https://cdn.example/a?sig=x"));
  assertEquals("https://books.example/path",NetworkAddressRules.referrer("https://books.example/path?token=secret#frag"));
  assertTrue(NetworkAddressRules.sameOrigin(new URL("https://books.example:443/a"),new URL("https://books.example/b")));
  assertFalse(NetworkAddressRules.sameOrigin(new URL("http://books.example/a"),new URL("https://books.example/b")));
  assertFalse(NetworkAddressRules.sameOrigin(new URL("https://books.example:444/a"),new URL("https://books.example/b")));
  assertFalse(NetworkAddressRules.sameOrigin(new URL("https://a.example/a"),new URL("https://b.example/b")));
  for(String s:new String[]{"https://books.example:0/a","https://books.example:65536/a","https://books.example/a b","https://books.example/a%7f","https://books.example/a%1f","https://books.example/a%00"})rejected(()->NetworkAddressRules.checked(s));
 }
 @Test public void acceptsRelativeAndSignedCdnWithoutChangingQuery()throws Exception {
  URL from=new URL("https://books.example/book");assertEquals("https://books.example/audio",NetworkAddressRules.redirect(from,"/audio").toString());
  assertEquals("https://[2001:4860:4860::8888]/audio",NetworkAddressRules.redirect(from,"https://[2001:4860:4860::8888]/audio").toString());
  String cdn="https://media.cdn.example:8443/chapter?sig=a%2Fb&expires=999";assertEquals(cdn,NetworkAddressRules.redirect(from,cdn).toString());
 }
 @Test public void boundaryPortsAndPublicLiteralAddressesRemainSupported()throws Exception {
  assertTrue(NetworkAddressRules.accepts("https://cdn.example:65535/audio"));
  for(String ip:new String[]{"8.8.8.8","8.0.0.8","88.88.88.88"}) {
   assertTrue(ip,NetworkAddressRules.publicAddress("https://"+ip+"/audio"));
   assertEquals("https://"+ip+"/audio",NetworkAddressRules.redirect(new URL("https://books.example/a"),"https://"+ip+"/audio").toString());
  }
 }
 @Test public void nonCanonicalLocalNamesAndNumericAliasesAreNotRedirectTargets()throws Exception {
  for(String host:new String[]{"app.localhost","router.internal","0x7f.0.0.1","127.0.0.1.","008.0.0.8","8.8","999.999.999.999"})rejected(()->NetworkAddressRules.redirect(new URL("https://books.example/a"),"https://"+host+"/audio"));
  for(String location:new String[]{"","   ","\nhttps://cdn.example/a","https://cdn.example/\rhidden"})rejected(()->NetworkAddressRules.redirect(new URL("https://books.example/a"),location));
 }
}
