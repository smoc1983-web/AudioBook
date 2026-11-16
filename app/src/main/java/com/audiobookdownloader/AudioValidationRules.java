package com.audiobookdownloader;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Content and container bounds, independent of suffix and HTTP Content-Type. */
public final class AudioValidationRules {
    private AudioValidationRules() {}
    public static String sizeError(long actual, long expected) {
        if(actual<=0)return "Файл пуст или недоступен для чтения";
        if(expected>0 && actual!=expected)return "Размер файла не совпадает с размером загрузки. Повторите скачивание главы.";
        return "";
    }
    public static String contentError(byte[] prefix, long actual) {
        String text=new String(prefix,StandardCharsets.UTF_8).replace("\uFEFF","").trim().toLowerCase(Locale.ROOT);
        if(isPage(text))return "Источник вернул страницу ошибки вместо аудио. Повторите загрузку или выберите другой источник.";
        if(isPlaylist(text))return "Источник вернул плейлист HLS/DASH вместо аудиофайла. Этот способ скачивания пока не поддерживается.";
        if(matches(prefix,0,"RIFF") && matches(prefix,8,"WAVE") && prefix.length>=12) {
            long declared=unsigned32(prefix,4)+8;
            if(declared>actual)return "Аудиофайл WAV не загружен полностью. Повторите скачивание главы.";
        }
        return "";
    }
    private static boolean isPage(String text) {
        return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head")
            || text.startsWith("<body") || text.startsWith("<!--") || text.startsWith("{\"error")
            || text.startsWith("<?xml") && !text.contains("<mpd");
    }
    private static boolean isPlaylist(String text) {
        return text.startsWith("#extm3u") || text.startsWith("<mpd") || text.startsWith("<?xml") && text.contains("<mpd");
    }
    private static boolean matches(byte[] bytes,int offset,String value) {
        if(offset+value.length()>bytes.length)return false;
        for(int i=0;i<value.length();i++)if(bytes[offset+i]!=(byte)value.charAt(i))return false;
        return true;
    }
    private static long unsigned32(byte[] bytes,int offset) {
        long value=0;
        for(int i=0;i<4;i++)value|=(long)(bytes[offset+i]&255)<<(i*8);
        return value;
    }
}
