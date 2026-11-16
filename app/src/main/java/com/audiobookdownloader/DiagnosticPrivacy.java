package com.audiobookdownloader;

import java.net.URI;
import java.util.regex.*;

/** Conservative export boundary; never reconstruct credentials, paths or URL parameters. */
public final class DiagnosticPrivacy {
    private DiagnosticPrivacy() {}
    private static final String HIDDEN="[скрыто]";
    private static final Pattern URLS=Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\r\\n\\\"'<>\\\\]+");
    private static final String KEYS="authorization|proxy[-_ ]?authorization|cookies?|set[-_ ]?cookie|(?:access[-_ ]?|refresh[-_ ]?|id[-_ ]?)?token|password|passwd|(?:client[-_ ]?)?secret|api[-_ ]?key|signature|sig|credential|sessions?(?:id)?|query|search(?:Text)?|книга|запрос|пароль|title|author|reader|note|serial|android[-_ ]?id|ssid";
    private static final Pattern FIELDS=Pattern.compile("(?i)([\\\"']?(?:"+KEYS+")[\\\"']?\\s*[:=]\\s*)(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'|[^\\r\\n|;,}]+)");
    private static final Pattern EMAIL=Pattern.compile("(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PATH=Pattern.compile("(?i)(?:/(?:storage|sdcard|data|mnt|home)/|[a-z]:\\\\)[^\\r\\n|]*");
    private static final Pattern IP=Pattern.compile("(?<![\\w.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![\\w.])");
    private static final Pattern BEARER=Pattern.compile("(?i)\\bBearer\\s+[^\\s\\\"'|,;]+");
    private static final Pattern HEADERS=Pattern.compile("(?im)^(\\s*(?:Authorization|Proxy-Authorization|Cookie|Set-Cookie)\\s*:\\s*)[^\\r\\n]*");
    public static String redact(String value) {
        if(value==null)return "";
        String safe=urls(HEADERS.matcher(value).replaceAll("$1"+HIDDEN).replace("\\/","/"));
        safe=FIELDS.matcher(safe).replaceAll("$1"+HIDDEN);
        safe=BEARER.matcher(safe).replaceAll("Bearer "+HIDDEN);
        safe=EMAIL.matcher(safe).replaceAll(HIDDEN);
        safe=PATH.matcher(safe).replaceAll(HIDDEN);
        return IP.matcher(safe).replaceAll(HIDDEN);
    }
    private static final Pattern EXCEPTION=Pattern.compile("(?:Caused by: |Suppressed: )?[\\w.$]+(?:Exception|Error|Throwable)\\b");
    public static String stack(String value) {
        if(value==null)return "";
        StringBuilder safe=new StringBuilder();
        for(String line:value.split("\\r?\\n")) {
            String selected=stackLine(line);
            if(!selected.isEmpty())safe.append(selected).append('\n');
        }
        return redact(safe.toString().trim());
    }
    private static String stackLine(String line) {
        Matcher type=EXCEPTION.matcher(line);
        if(type.find())return type.group()+": [сообщение скрыто]";
        if(line.matches("\\s*at [\\w.$/]+\\([^()]*\\)\\s*")||line.matches("\\s*\\.\\.\\. [0-9]+ more\\s*"))return line;
        return "";
    }
    private static String urls(String value) {
        Matcher m=URLS.matcher(value);StringBuffer out=new StringBuffer();
        while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(origin(m.group())));
        m.appendTail(out);return out.toString();
    }
    private static String origin(String value) {
        try {
            URI u=new URI(value);String scheme=u.getScheme().toLowerCase(java.util.Locale.ROOT);
            if(!(scheme.equals("http")||scheme.equals("https"))||u.getHost()==null)return HIDDEN;
            if(NetworkAddressRules.localHost(u.getHost()))return HIDDEN;
            return scheme+"://"+u.getHost().toLowerCase(java.util.Locale.ROOT);
        }catch(Exception bad){return HIDDEN;}
    }
}
