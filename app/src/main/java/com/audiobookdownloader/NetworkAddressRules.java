package com.audiobookdownloader;

import java.net.*;
import java.io.*;
import java.util.*;

/** Shared syntax/redirect rules. Source allowlists and the updater's stricter rules remain additional gates. */
public final class NetworkAddressRules {
    private NetworkAddressRules() {}
    public static URL checked(String value)throws IOException {
        try {
            if(unsafeRaw(value))throw new IllegalArgumentException();
            URI u=new URI(value);
            if(!validAuthority(u))throw new IllegalArgumentException();
            return u.toURL();
        }catch(Exception bad){throw new IOException("Недопустимый сетевой адрес");}
    }
    private static boolean unsafeRaw(String value) {
        return value.toLowerCase(Locale.ROOT).matches(".*%(?:0[0-9a-f]|1[0-9a-f]|7f).*");
    }
    private static boolean validAuthority(URI u) {
        return ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&u.getRawUserInfo()==null&&u.getPort()<=65535&&u.getPort()!=0;
    }
    public static String referrer(String value)throws IOException {
        checked(value);return value.split("[?#]",2)[0];
    }
    public static boolean accepts(String value) {try{checked(value);return true;}catch(IOException bad){return false;}}
    public static boolean publicAddress(String value) {try{return !localHost(checked(value).getHost());}catch(IOException bad){return false;}}
    public static URL redirect(URL from,String location)throws IOException {
        if(location==null||location.trim().isEmpty()||location.matches("(?s).*[\\p{Cntrl}\\\\].*"))throw new IOException("Нет адреса перенаправления");
        URL next=checked(new URL(from,location).toString());
        if("https".equalsIgnoreCase(from.getProtocol())&&!"https".equalsIgnoreCase(next.getProtocol()))throw new IOException("Небезопасное перенаправление HTTPS");
        if(!localHost(from.getHost())&&localHost(next.getHost()))throw new IOException("Перенаправление на локальный адрес запрещено");
        return next;
    }
    static boolean localHost(String host) {
        String h=host.toLowerCase(Locale.ROOT).replace("[","").replace("]","").replaceFirst("\\.$","");
        if(h.contains(":"))return privateLiteral(h);
        if(h.matches("[0-9.]+"))return unusualIpv4(h)||privateLiteral(h);
        return namedLocal(h);
    }
    private static boolean namedLocal(String h) {
        return h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||!h.contains(".");
    }
    private static boolean unusualIpv4(String h) {
        if(!h.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return true;
        for(String part:h.split("\\."))if(part.length()>1&&part.startsWith("0"))return true;
        return false;
    }
    private static boolean privateLiteral(String h) {
        try {
            InetAddress a=InetAddress.getByName(h);byte[] b=a.getAddress();
            return a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||uniqueLocal(b);
        }catch(Exception invalid){return true;}
    }
    private static boolean uniqueLocal(byte[] b) {return b.length==16&&(b[0]&0xfe)==0xfc;}
    public static boolean sameOrigin(URL a,URL b) {
        return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);
    }
    public static boolean forwardHeader(URL original,URL next,String name) {
        return sameOrigin(original,next)||Arrays.asList("user-agent","accept","accept-language","accept-encoding","range","if-range").contains(name.toLowerCase(Locale.ROOT));
    }
    private static int port(URL u) {return u.getPort()==-1?u.getDefaultPort():u.getPort();}
}
