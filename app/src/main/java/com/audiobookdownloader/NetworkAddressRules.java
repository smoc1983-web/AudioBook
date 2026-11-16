package com.audiobookdownloader;
import java.net.*;import java.io.*;
/** Shared source URL boundary; preserves source-specific allowlists. */
public final class NetworkAddressRules {
    private NetworkAddressRules() {}
    public static URL checked(String value)throws IOException {
        URL u=new URL(value);
        if(!("http".equals(u.getProtocol())||"https".equals(u.getProtocol()))||u.getUserInfo()!=null)throw new IOException("Недопустимый сетевой адрес");
        return u;
    }
    public static URL redirect(URL from,String location)throws IOException {return checked(new URL(from,location).toString());}
}
