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
  for(String s:new String[]{"http://cdn.example/audio","https://127.0.0.1/a","https://[::1]/a","https://192.168.1.2/a","https://169.254.169.254/a","https://localhost/a","https://router.local/a","https://2130706433/a","https://0x7f000001/a"})rejected(()->NetworkAddressRules.redirect(from,s));
  rejected(()->NetworkAddressRules.redirect(from,null));
 }
 @Test public void acceptsRelativeAndSignedCdnWithoutChangingQuery()throws Exception {
  URL from=new URL("https://books.example/book");assertEquals("https://books.example/audio",NetworkAddressRules.redirect(from,"/audio").toString());
  String cdn="https://media.cdn.example:8443/chapter?sig=a%2Fb&expires=999";assertEquals(cdn,NetworkAddressRules.redirect(from,cdn).toString());
 }
}
