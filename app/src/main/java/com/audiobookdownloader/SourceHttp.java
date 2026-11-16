package com.audiobookdownloader;

import java.io.*;
import java.net.*;
import java.util.*;

/** Explicit GET/HEAD redirects for generic source media, not a replacement for the updater policy. */
public final class SourceHttp {
    interface Connections {HttpURLConnection open(URL address)throws IOException;}
    private SourceHttp() {}
    public static HttpURLConnection open(String address,Map<String,String> headers,int connectMs,int readMs)throws IOException {
        return open(address,headers,connectMs,readMs,"GET",url->(HttpURLConnection)url.openConnection());
    }
    static HttpURLConnection open(String address,Map<String,String> headers,int connectMs,int readMs,String method,Connections factory)throws IOException {
        URL original=NetworkAddressRules.checked(address),next=original;
        Set<String> visited=new HashSet<>();
        for(int hop=0;hop<6;hop++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Запрос отменён");
            if(!visited.add(next.toString()))throw new IOException("Повторное перенаправление источника");
            HttpURLConnection c=factory.open(next);boolean returned=false;
            try {
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(connectMs);c.setReadTimeout(readMs);c.setRequestMethod(method);
                for(Map.Entry<String,String> h:headers.entrySet())if(NetworkAddressRules.forwardHeader(original,next,h.getKey())) {
                    checkHeader(h.getKey(),h.getValue());c.setRequestProperty(h.getKey(),h.getValue());
                }
                int status=c.getResponseCode();
                if(isRedirect(status)){next=NetworkAddressRules.redirect(next,c.getHeaderField("Location"));continue;}
                returned=true;return c;
            }finally{if(!returned)c.disconnect();}
        }
        throw new IOException("Слишком много перенаправлений источника");
    }
    private static void checkHeader(String name,String value)throws IOException {
        if(!name.matches("[A-Za-z0-9-]+")||value==null||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IOException("Недопустимый заголовок запроса");
    }
    public static boolean isRedirect(int code) {return Arrays.asList(301,302,303,307,308).contains(code);}
}
