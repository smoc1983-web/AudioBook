package com.audiobookdownloader;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Direct, public catalog adapters. No WebView, arbitrary scripts or embedded API keys. */
public final class OpenAudioCatalog {
    public static final String[] MUSIC={"archive_music","audius","radio"},BOOKS={"librivox","archive_books"};
    public static String label(String source){switch(source){case "audius":return "Audius";case "radio":return "Радио";case "librivox":return "LibriVox · Internet Archive";default:return "Internet Archive";}}
    public static final class Item {
        public final String id,source,title,author,cover,page;public final boolean downloadable;
        public Item(String id,String source,String title,String author,String cover,String page,boolean downloadable){this.id=id;this.source=source;this.title=title;this.author=author;this.cover=cover;this.page=page;this.downloadable=downloadable;}
    }
    public static final class Track {
        public final String title,url,download;public final int seconds;
        public Track(String title,String url,String download,int seconds){this.title=title;this.url=url;this.download=download;this.seconds=seconds;}
    }
    public static String encode(String text){try{return URLEncoder.encode(text,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
    public static boolean https(String url){try{URI u=URI.create(url);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
    public static String text(Object value){if(value instanceof JSONArray){JSONArray a=(JSONArray)value;List<String> s=new ArrayList<>();for(int i=0;i<a.length();i++)s.add(a.optString(i));return String.join(", ",s);}return value==null||value==JSONObject.NULL?"":value.toString();}
    public static String searchUrl(String source,String query,int page,boolean russian){
        int offset=Math.max(0,page)*20;
        if(source.equals("audius"))return "https://api.audius.co/v1/tracks/"+(query.trim().isEmpty()?"trending?":"search?query="+encode(query.trim())+"&")+"limit=20&offset="+offset+"&app_name=ForTanya";
        if(source.equals("radio"))return "https://de1.api.radio-browser.info/json/stations/search?name="+encode(query.trim())+"&limit=20&offset="+offset+"&hidebroken=true&is_https=true&order=votes&reverse=true";
        String collection=source.equals("librivox")?"collection:librivoxaudio":source.equals("archive_books")?"collection:audio_bookspoetry AND NOT collection:librivoxaudio":"(collection:audio_music OR collection:etree OR collection:opensource_audio)";
        String q="mediatype:audio AND "+collection+" AND NOT access-restricted-item:true AND (format:\"VBR MP3\" OR format:\"128Kbps MP3\" OR format:MP3)";
        if(russian)q+=" AND language:(rus OR Russian OR russian)";
        for(String term:query.trim().split("\\s+"))if(!term.isEmpty())q+=" AND \""+term.replace("\\","\\\\").replace("\"","\\\"")+"\"";
        return "https://archive.org/advancedsearch.php?q="+encode(q)+"&output=json&rows=20&page="+(Math.max(0,page)+1)+"&fl[]=identifier&fl[]=title&fl[]=creator&sort[]=downloads%20desc";
    }
    public static List<Item> search(String source,String query,int page,boolean russian)throws Exception{return parseSearch(source,get(searchUrl(source,query,page,russian)));}
    public static List<Item> parseSearch(String source,String body)throws Exception{
        List<Item> result=new ArrayList<>();JSONArray rows;
        if(source.equals("radio"))rows=new JSONArray(body);
        else if(source.equals("audius"))rows=new JSONObject(body).getJSONArray("data");
        else rows=new JSONObject(body).getJSONObject("response").getJSONArray("docs");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;
            if(source.equals("audius")){
                if(!o.optBoolean("is_available",true)||!o.optBoolean("is_streamable",true)||o.optBoolean("is_stream_gated")||o.optBoolean("is_delete"))continue;
                String id=o.optString("id"),author=o.optJSONObject("user")==null?"":o.optJSONObject("user").optString("name");
                boolean download=o.optBoolean("is_downloadable")&&!o.optBoolean("is_download_gated")&&o.optJSONObject("download")!=null&&https(o.optJSONObject("download").optString("url"));
                if(!id.matches("[A-Za-z0-9]+")||!seen.add(id))continue;
                result.add(new Item(id,source,o.optString("title","Композиция"),author,"","https://audius.co"+o.optString("permalink"),download));
            }else if(source.equals("radio")){
                String url=o.optString("url_resolved");if(!https(url)||o.optInt("lastcheckok",1)!=1||o.optInt("hls")!=0||!seen.add(url))continue;
                result.add(new Item(url,source,o.optString("name","Радиостанция"),o.optString("country"),"",url,false));
            }else{
                String id=o.optString("identifier");if(!id.matches("[A-Za-z0-9_.-]+")||!seen.add(id))continue;
                result.add(new Item(id,source,text(o.opt("title")),text(o.opt("creator")),"https://archive.org/services/img/"+encode(id),"https://archive.org/details/"+encode(id),true));
            }
        }return result;
    }
    public static List<Track> resolve(Item item)throws Exception{
        if(item.source.equals("radio"))return Collections.singletonList(new Track(item.title,item.id,"",0));
        if(item.source.equals("audius")){
            JSONObject o=new JSONObject(get("https://api.audius.co/v1/tracks/"+encode(item.id)+"?app_name=ForTanya")).getJSONObject("data");
            if(o.optBoolean("is_stream_gated")||!o.optBoolean("is_available",true)||!o.optBoolean("is_streamable",true))throw new IOException("Источник ограничил доступ к композиции");
            String download="";JSONObject d=o.optJSONObject("download");if(o.optBoolean("is_downloadable")&&!o.optBoolean("is_download_gated")&&d!=null&&https(d.optString("url")))download=d.optString("url");
            return Collections.singletonList(new Track(item.title,"https://api.audius.co/v1/tracks/"+encode(item.id)+"/stream?app_name=ForTanya",download,o.optInt("duration")));
        }
        return archiveTracks(item.id,new JSONObject(get("https://archive.org/metadata/"+encode(item.id))));
    }
    private static int rank(JSONObject f){return f.optString("source").equals("original")?0:f.optString("format").equals("VBR MP3")?1:f.optString("format").contains("64K")?3:2;}
    private static String order(JSONObject f){String value=f.optString("track",f.optString("name"));java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+|\\D+").matcher(value);StringBuilder b=new StringBuilder();while(m.find()){String x=m.group();try{b.append(String.format(Locale.ROOT,"%012d",Long.parseLong(x)));}catch(Exception e){b.append(x.toLowerCase(Locale.ROOT));}}return b.toString();}
    public static List<Track> archiveTracks(String id,JSONObject data)throws Exception{
        JSONObject meta=data.optJSONObject("metadata");if(meta!=null&&(meta.optBoolean("access-restricted-item")||meta.optBoolean("is_dark")))throw new IOException("Книга недоступна для свободного воспроизведения");
        JSONArray files=data.optJSONArray("files");if(files==null)throw new IOException("Источник не вернул аудиофайлы");
        LinkedHashMap<String,JSONObject> chosen=new LinkedHashMap<>();
        for(int i=0;i<files.length();i++){JSONObject f=files.optJSONObject(i);if(f==null)continue;String name=f.optString("name");if(!name.toLowerCase(Locale.ROOT).endsWith(".mp3")||f.optBoolean("private"))continue;
            String key=f.optString("original",name);JSONObject old=chosen.get(key);if(old==null||rank(f)<rank(old))chosen.put(key,f);
        }
        List<JSONObject> sorted=new ArrayList<>(chosen.values());sorted.sort(Comparator.comparing(OpenAudioCatalog::order));
        List<Track> tracks=new ArrayList<>();for(JSONObject f:sorted){String name=f.optString("name");StringBuilder path=new StringBuilder();for(String part:name.split("/")){if(path.length()>0)path.append('/');path.append(encode(part));}
            String url="https://archive.org/download/"+encode(id)+"/"+path;int seconds=0;try{seconds=(int)Double.parseDouble(f.optString("length","0"));}catch(Exception ignored){}
            tracks.add(new Track(f.optString("title",name),url,url,seconds));}
        if(tracks.isEmpty())throw new IOException("В записи нет доступных MP3-файлов");return tracks;
    }
    public static Item fromPage(String value){try{URI u=URI.create(value);if(!https(value)||!u.getHost().equals("archive.org")||!u.getPath().startsWith("/details/"))return null;String id=u.getPath().substring(9).split("/")[0];if(!id.matches("[A-Za-z0-9_.-]+"))return null;return new Item(id,"archive_books",id,"","https://archive.org/services/img/"+encode(id),"https://archive.org/details/"+id,true);}catch(Exception e){return null;}}
    public static String get(String url)throws Exception{
        if(!https(url))throw new IOException("Нужна HTTPS-ссылка");
        for(int redirects=0;redirects<6;redirects++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Запрос отменён");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","OpenAudio-Android/1.0");c.setRequestProperty("Accept","application/json,text/html");
            try{int code=c.getResponseCode();if(code>=300&&code<400){url=new URL(new URL(url),c.getHeaderField("Location")).toString();if(!https(url))throw new IOException("Источник перенаправил на небезопасный адрес");continue;}if(code!=200)throw new IOException("Источник вернул HTTP "+code);
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>8_000_000)throw new IOException("Ответ источника слишком большой");out.write(buf,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
            }finally{c.disconnect();}
        }throw new IOException("Слишком много перенаправлений");
    }
}
