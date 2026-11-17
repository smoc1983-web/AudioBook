package com.audiobookdownloader;

import java.net.URI;
import java.util.*;

/** File names never choose a destination directory; content identifies an imported edition. */
final class SharedAudioRules {
    static final int MAX_FILES=128;
    static final long MAX_FILE=2L*1024*1024*1024,MAX_BOOK=8L*1024*1024*1024;
    static boolean accepts(String value) {
        try {
            if(value.length()>8192)return false;
            URI uri=new URI(value);
            return "content".equals(uri.getScheme())&&uri.getRawAuthority()!=null&&
                uri.getRawAuthority().matches("[A-Za-z0-9._-]+")&&uri.getRawFragment()==null;
        }catch(Exception bad){return false;}
    }
    static String label(String value) {
        String text=value.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]","_").trim().replaceAll("^[. ]+|[. ]+$","");
        return text.isEmpty() ? "Глава" : text.substring(0,Math.min(100,text.length()));
    }
    static String filename(int index,String label,String extension) {
        if(index<0||index>=MAX_FILES||!Arrays.asList("mp3","m4a","aac","ogg","opus","wav","flac").contains(extension))throw new IllegalArgumentException();
        return String.format(Locale.ROOT,"%04d - %s.%s",index+1,label(label),extension);
    }
    static String bundle(List<String> hashes) {
        if(hashes.isEmpty()||hashes.size()>MAX_FILES)throw new IllegalArgumentException();
        for(String hash:hashes)if(!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        StringBuilder joined=new StringBuilder();for(String hash:hashes)joined.append(hash).append('\n');
        return ChapterIdentity.hash(joined.toString());
    }
    static int compare(String a,String b) {
        List<String> left=parts(a),right=parts(b);
        for(int i=0;i<Math.min(left.size(),right.size());i++) {
            int order=part(left.get(i),right.get(i));if(order!=0)return order;
        }
        int size=Integer.compare(left.size(),right.size());return size!=0 ? size : a.compareTo(b);
    }
    private static List<String> parts(String value) {
        List<String> result=new ArrayList<>();
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("[0-9]+|[^0-9]+").matcher(value.replaceFirst("\\.[A-Za-z0-9]{1,12}$",""));
        while(matcher.find())result.add(matcher.group());return result;
    }
    private static int part(String a,String b) {
        if(!a.matches("[0-9]+")||!b.matches("[0-9]+"))return a.compareToIgnoreCase(b);
        String x=a.replaceFirst("^0+(?!$)",""),y=b.replaceFirst("^0+(?!$)","");
        int size=Integer.compare(x.length(),y.length());return size!=0 ? size : x.compareTo(y);
    }
    static String extension(byte[] bytes,String mime) {
        if(bytes.length>=12 && new String(bytes,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"))return "m4a";
        return formatExtension(mime);
    }
    private static String formatExtension(String mime) {
        switch(mime) {
            case "audio/raw":return "wav";
            case "audio/mpeg":return "mp3";
            case "audio/flac":return "flac";
            case "audio/vorbis":return "ogg";
            case "audio/opus":return "opus";
            case "audio/mp4a-latm":case "audio/aac":return "aac";
            default:throw new IllegalArgumentException("Этот аудиоформат пока не поддерживается импортом");
        }
    }
}
