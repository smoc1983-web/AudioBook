package com.audiobookdownloader;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared by the native player and DownloadManager. Cookies are resolved for the audio URL by Android. */
public final class BookAudioHeaders {
    private BookAudioHeaders() {}
    private static URI http(String value) {
        try {
            URI uri=new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&
                uri.getHost()!=null&&uri.getRawUserInfo()==null ? uri : null;
        } catch(Exception ignored) { return null; }
    }
    private static boolean safe(String value) { return value!=null&&!value.isBlank()&&value.length()<=16384&&value.indexOf('\r')<0&&value.indexOf('\n')<0; }
    public static Map<String,String> build(String audio,String page,String agent,String targetCookies) {
        if(http(audio)==null)return Collections.emptyMap();
        Map<String,String> result=new LinkedHashMap<>();
        if(safe(agent))result.put("User-Agent",agent);
        URI referer=http(page);
        if(referer!=null&&safe(page))result.put("Referer",page.split("#",2)[0]);
        if(safe(targetCookies))result.put("Cookie",targetCookies);
        return result;
    }
}
