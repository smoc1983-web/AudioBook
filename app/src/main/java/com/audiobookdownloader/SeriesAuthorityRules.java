package com.audiobookdownloader;
import java.net.*;import java.util.*;import java.util.regex.*;
/** Bibliographic identity is title + explicit author; narrator/format suffixes do not change the work. */
public final class SeriesAuthorityRules {
 private static final Pattern NARRATOR=Pattern.compile("(?iu)\\s*(?:\\(|\\[)\\s*(?:в\\s+исполнении|читает|озвучивает|чтец)(?=\\s|:).*?[)\\]]");
 public static String clean(String value){return (value==null?"":value).trim().replaceAll("(?iu)^\\s*аудиокнига\\s*[:—–-]?\\s*","").replaceAll("(?iu)\\s*(?:[—–|]\\s*)?(?:слушать(?:\\s+онлайн)?|скачать(?:\\s+бесплатно)?)(?:\\s*\\|.*)?$","").trim();}
 public static String queryTitle(String value){return NARRATOR.matcher(clean(value)).replaceAll("").replaceFirst("^\\s*\\d+(?:[.,]\\d+)?[.)]\\s+", "").trim();}
 public static String title(String value){return SearchBookIndex.titleKey(queryTitle(value));}
 public static String author(String value){return SearchBookIndex.keys("",value==null?"":value).author;}
 public static String series(String value){return SearchBookIndex.normal((value==null?"":value).replaceFirst("(?iu)^\\s*(?:цикл|серия)\\s*[:—–-]?\\s*", ""));}
 public static String seriesLabel(String value){return (value==null?"":value).trim().replaceFirst("(?iu)^\\s*(?:цикл|серия)\\s*[:—–-]?\\s*", "").replaceAll("^[\\s«»\"“”]+|[\\s«»\"“”]+$","").trim();}
 public static boolean plausibleSeriesTitle(String value){String s=seriesLabel(value);return !s.isEmpty()&&s.length()<=300&&!s.matches("(?iu).*(?:^|[\\s(])(?:в\\s+исполнении|читает|озвучивает|чтец)(?:[\\s:)]|$).*")&&!s.matches("^\\d+(?:[.,]\\d+)?[.)]\\s+.*");}
 public static boolean sameTitle(String wanted,String actual,String cycle){String a=title(wanted),b=title(actual),c=series(cycle);if(a.isEmpty()||b.isEmpty())return false;if(a.equals(b))return true;if(!c.isEmpty()){if(a.startsWith(c+" "))a=a.substring(c.length()+1);if(b.startsWith(c+" "))b=b.substring(c.length()+1);}return !a.isEmpty()&&a.equals(b);}
 public static boolean sameAuthor(String wanted,Collection<String> actual){String key=author(wanted);return !key.isEmpty()&&actual.stream().anyMatch(a->key.equals(author(a)));}
 public static String provider(String address){try{URI u=URI.create(address);if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)return "";String h=u.getHost().toLowerCase(Locale.ROOT);if(h.equals("author.today")||h.equals("www.author.today"))return "Author.Today";if(h.equals("litres.ru")||h.equals("www.litres.ru"))return "Литрес";}catch(Exception ignored){}return "";}
 public static String canonical(String address){try{URI u=URI.create(address);String p=provider(address);if(p.isEmpty())return "";String h=p.equals("Author.Today")?"author.today":"www.litres.ru";return "https://"+h+u.getRawPath().replaceAll("/+$","");}catch(Exception e){return "";}}
 public static boolean work(String address){String p=provider(address);String u=canonical(address);return p.equals("Author.Today")?u.matches("https://author\\.today/work/\\d+"):p.equals("Литрес")&&u.matches("https://www\\.litres\\.ru/(?:book|audiobook)/[^/]+/[^/]+-\\d+");}
 public static boolean cycle(String address){String p=provider(address);String u=canonical(address);return p.equals("Author.Today")?u.matches("https://author\\.today/work/series/\\d+"):p.equals("Литрес")&&u.matches("https://www\\.litres\\.ru/series/[^/]+-\\d+");}
}
