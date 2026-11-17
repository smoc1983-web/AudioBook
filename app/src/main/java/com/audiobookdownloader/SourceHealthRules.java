package com.audiobookdownloader;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Observed site response only, never a promise that an audiobook is playable. */
final class SourceHealthRules {
    static final long TTL=15*60*1000L, MIN_WAIT=60*1000L, MAX_WAIT=24*60*60*1000L;
    enum Status { RESPONDED, RESTRICTED, RATE_LIMIT, SERVER_ERROR, HTTP_ERROR, UNEXPECTED,
        TIMEOUT, DNS, CONNECTION, TLS, REDIRECT_BLOCKED, BUSY }
    static Status response(int code,boolean html,boolean challenge) {
        if(code>=200&&code<=299)return page(html,challenge);
        switch(code) {
            case 401: case 403:return Status.RESTRICTED;
            case 429:return Status.RATE_LIMIT;
            default:return code>=500 ? Status.SERVER_ERROR : Status.HTTP_ERROR;
        }
    }
    private static Status page(boolean html,boolean challenge) {
        if(challenge)return Status.RESTRICTED;
        return html ? Status.RESPONDED : Status.UNEXPECTED;
    }
    static boolean fresh(long checked,long now) {return checked>=0&&now>=checked&&now-checked<TTL;}
    static boolean mayRequest(long checked,long retryUntil,long now,boolean force) {
        if(checked<0)return true;
        if(now<checked||now<retryUntil)return false;
        return now-checked>=MIN_WAIT&&(force||!fresh(checked,now));
    }
    static long retryWait(String header,long now) {
        if(header==null||header.trim().isEmpty())return MIN_WAIT;
        String value=header.trim();
        if(value.matches("[0-9]+")) {
            try{return Math.min(Long.parseLong(value),MAX_WAIT/1000)*1000;}
            catch(NumberFormatException overflow){return MAX_WAIT;}
        }
        SimpleDateFormat format=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);format.setLenient(false);
        ParsePosition at=new ParsePosition(0);Date date=format.parse(value,at);
        if(date==null||at.getIndex()!=value.length())return MIN_WAIT;
        return date.getTime()<=now ? 0 : Math.min(date.getTime()-now,MAX_WAIT);
    }
}
