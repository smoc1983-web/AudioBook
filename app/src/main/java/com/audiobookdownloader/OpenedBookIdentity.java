package com.audiobookdownloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict work identity for an opened audio page; ignores only known presentation wrappers. */
public final class OpenedBookIdentity {
    private static final Pattern PREFIX=Pattern.compile("(?iu)^\\s*аудиокнига\\s*[:—–-]?\\s+");
    private static final Pattern SEO=Pattern.compile("(?iu)\\s+(?:заблудись\\s+бесплатно|аудиокнига\\s+бесплатно|слушать\\s+онлайн\\s+бесплатно|слушать\\s+бесплатно)[! .…]*$");
    private static final Pattern AUTHOR=Pattern.compile("(?iu)\\s+[-—–|]\\s*автор\\s*:?\\s+(.+?)\\s*$");
    private static final Pattern VOLUME=Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*[.\\-–—:]\\s*(.+)$");
    private OpenedBookIdentity(){}
    private static String trim(String s){return s.trim().replaceAll("^[\\s«»\"“”]+|[\\s«»\"“”]+$","").trim();}
    private static String unwrap(String raw,String author){
        String s=trim(raw==null?"":raw);
        Matcher m=AUTHOR.matcher(s);
        if(m.find()&&!SeriesAuthorityRules.author(author).isEmpty()
                &&SeriesAuthorityRules.author(author).equals(SeriesAuthorityRules.author(m.group(1))))
            s=s.substring(0,m.start());
        s=PREFIX.matcher(s).replaceFirst("");
        s=SEO.matcher(s).replaceFirst("");
        return trim(s);
    }
    private static final class Part {
        final String key; final Double number;
        Part(String key,Double number){this.key=key;this.number=number;}
    }
    private static Part part(String raw,String author,String cycle){
        String s=unwrap(raw,author);
        String c=trim(cycle==null?"":cycle);
        if(!c.isEmpty()&&s.length()>c.length()&&s.regionMatches(true,0,c,0,c.length())
                &&!Character.isLetterOrDigit(s.charAt(c.length()))){
            String rest=s.substring(c.length()).trim().replaceFirst("^[\\s.\\-–—:]+","");
            Matcher m=VOLUME.matcher(rest);
            if(m.matches()){
                Double n=null;
                try{n=Double.valueOf(m.group(1).replace(',','.'));}catch(NumberFormatException ignored){}
                return new Part(SeriesAuthorityRules.title(m.group(2)),n);
            }
            if(!rest.isEmpty())return new Part(SeriesAuthorityRules.title(rest),null);
        }
        return new Part(SeriesAuthorityRules.title(s),null);
    }
    public static boolean matches(String wanted,String actual,String wantedAuthor,String actualAuthor,String cycle,Double expectedOrder){
        String wa=SeriesAuthorityRules.author(wantedAuthor),aa=SeriesAuthorityRules.author(actualAuthor);
        if(!wa.isEmpty()&&!aa.isEmpty()&&!wa.equals(aa))return false;
        String author=wa.isEmpty()?actualAuthor:wantedAuthor;
        Part a=part(wanted,author,cycle),b=part(actual,author,cycle);
        if(a.key.isEmpty()||!a.key.equals(b.key))return false;
        if(a.number!=null&&b.number!=null&&!a.number.equals(b.number))return false;
        if(expectedOrder!=null&&((a.number!=null&&!expectedOrder.equals(a.number))||(b.number!=null&&!expectedOrder.equals(b.number))))return false;
        return true;
    }
}
