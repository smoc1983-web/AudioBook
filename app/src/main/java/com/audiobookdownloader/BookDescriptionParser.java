package com.audiobookdownloader;

import java.net.URI;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** A bounded, plain-text annotation from this work's own page, never recommendations. */
public final class BookDescriptionParser {
    private static final int LIMIT=20000;
    private BookDescriptionParser() {}
    public static String parse(String html,String url,String title,String author) {
        if(html==null||html.length()>1048576||title==null||title.trim().isEmpty()||pageKey(url).isEmpty())return "";
        Document doc=Jsoup.parse(html,url);
        if(!canonicalMatches(doc,url)||!identityMatches(doc,title,author))return "";
        String json=jsonDescription(doc,title,author,url);
        doc.select("script,style,nav,aside,footer,.related,.recommendations,.BookSeries").remove();
        String body=bodyDescription(doc,url);
        if(!body.isEmpty())return body;
        if(!json.isEmpty())return json;
        return metaDescription(doc);
    }
    private static boolean canonicalMatches(Document doc,String url) {
        Element canonical=doc.selectFirst("link[rel=canonical][href]");
        Element og=doc.selectFirst("meta[property=og:url][content]");
        return samePage(url,canonical==null?url:canonical.absUrl("href"))&&samePage(url,og==null?url:og.attr("content"));
    }
    private static boolean samePage(String url,String address) {
        try {return pageKey(url).equals(pageKey(URI.create(url).resolve(address).toString()));}
        catch(IllegalArgumentException error){return false;}
    }
    private static String pageKey(String address) {
        if(address==null)return "";
        try {
            URI u=URI.create(address);
            if(!validPage(u))return "";
            return u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^(?:www|mob)\\.","")+"|"+
                String.valueOf(u.getRawPath()).replaceFirst("/+$","");
        }catch(IllegalArgumentException error){return "";}
    }
    private static boolean validPage(URI uri) {
        return ("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))&&uri.getHost()!=null&&
            uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==80||uri.getPort()==443);
    }
    private static boolean identityMatches(Document doc,String title,String author) {
        if(!doc.select("h1").isEmpty())return doc.select("h1").stream().anyMatch(e->sameTitle(title,e.text(),author));
        Element og=doc.selectFirst("meta[property=og:title][content]");
        return og!=null&&sameTitle(title,og.attr("content"),author);
    }
    private static boolean sameTitle(String title,String actual,String author) {
        String value=actual.replaceFirst("(?iu)\\s*[|—–]\\s*(?:слушать|читать|скачать).*$","").trim();
        if(SeriesAuthorityRules.sameTitle(title,value,""))return true;
        String target=SeriesAuthorityRules.title(title),writer=SeriesAuthorityRules.author(author),key=SeriesAuthorityRules.title(value);
        return !writer.isEmpty()&&(key.equals(writer+" "+target)||key.equals(target+" "+writer));
    }
    private static String bodyDescription(Document doc,String url) {
        String[] selectors={"[itemprop=description]","#book-description",".book-description",".book_description",".work-description__text",".work-description",".full-description",".full-desc",".full-text",".fdesc",".entry-content"};
        for(String selector:selectors)for(Element e:doc.select(selector)) {
            String value=plain(e.tagName().equals("meta")?e.attr("content"):e.html());
            if(usable(value))return value;
        }
        String labelled=labelledDescription(doc);
        if(!labelled.isEmpty())return labelled;
        if(URI.create(url).getHost().endsWith("bookinfo.net")) {
            Element e=doc.selectFirst("p.text-theme-muted.text-xl.font-medium");
            if(e!=null){String value=plain(e.html());if(usable(value))return value;}
        }
        return "";
    }
    private static String labelledDescription(Document doc) {
        for(Element e:doc.select(".BookDescription:not(.BookSeries)")) {
            Element label=e.selectFirst(".BookDescriptionLabel");
            if(label==null||!label.text().matches("(?iu).*(?:описание|аннотация).*"))continue;
            Element copy=e.clone();copy.select(".BookDescriptionLabel").remove();String value=plain(copy.html());
            if(usable(value))return value;
        }
        return "";
    }
    private static String metaDescription(Document doc) {
        for(Element e:doc.select("meta[property=og:description],meta[name=description]")) {
            String value=plain(e.attr("content"));
            if(usable(value)&&!value.matches("(?iu)^(?:слушайте аудиокниги|бесплатные аудиокниги|слушать онлайн).*$"))return value;
        }
        return "";
    }
    private static boolean usable(String text){return text.length()>=32;}
    private static String plain(String html) {
        Document d=Jsoup.parseBodyFragment(html);d.select("script,style,nav,aside,button,iframe").remove();
        for(Element e:d.select("p,div,li,br"))e.before("\n\n");
        String value=d.body().wholeText().replaceAll("[\\p{Zs}\\t\\x0B\\f\\r]+"," ")
            .replaceAll("[\\x00-\\x08\\x0E-\\x1F]","").replaceAll(" *\\n *","\n").replaceAll("\\n{3,}","\n\n").trim();
        return value.length()>LIMIT?value.substring(0,LIMIT)+"…":value;
    }
    private static String jsonDescription(Document doc,String title,String author,String url) {
        int count=0;
        for(Element e:doc.select("script[type=application/ld+json]")) {
            if(++count>16)break;
            if(e.data().length()>262144||!boundedJson(e.data()))continue;
            try {String value=jsonNode(new JSONTokener(e.data()).nextValue(),title,author,url,0);if(!value.isEmpty())return value;}
            catch(JSONException ignored){}
        }
        return "";
    }
    private static boolean boundedJson(String text) {
        int structures=0;
        for(int i=0;i<text.length();i++)if((text.charAt(i)=='{'||text.charAt(i)=='[')&&++structures>64)return false;
        return true;
    }
    private static String jsonNode(Object value,String title,String author,String url,int depth) {
        if(depth>4)return "";
        if(value instanceof JSONArray)return jsonArray((JSONArray)value,title,author,url,depth);
        if(!(value instanceof JSONObject))return "";
        JSONObject node=(JSONObject)value;
        String own=jsonBook(node,title,author,url);
        return own.isEmpty()?jsonNode(node.opt("@graph"),title,author,url,depth+1):own;
    }
    private static String jsonArray(JSONArray rows,String title,String author,String url,int depth) {
        for(int i=0;i<Math.min(rows.length(),64);i++) {
            String result=jsonNode(rows.opt(i),title,author,url,depth+1);if(!result.isEmpty())return result;
        }
        return "";
    }
    private static String jsonBook(JSONObject node,String title,String author,String url) {
        if(!node.optString("@type").matches("(?i)(?:https?://schema.org/)?(?:Book|Audiobook)"))return "";
        if(!sameTitle(title,node.optString("name"),author)||!samePage(url,node.optString("url",url)))return "";
        if(!authorMatches(author,node.opt("author")))return "";
        String value=plain(node.optString("description"));return usable(value)?value:"";
    }
    private static boolean authorMatches(String wanted,Object value) {
        if(wanted.isEmpty()||value==null)return true;
        if(value instanceof JSONArray) {
            JSONArray a=(JSONArray)value;for(int i=0;i<Math.min(a.length(),16);i++)if(authorMatches(wanted,a.opt(i)))return true;
            return false;
        }
        String actual=value instanceof JSONObject?((JSONObject)value).optString("name"):String.valueOf(value);
        return SeriesAuthorityRules.author(wanted).equals(SeriesAuthorityRules.author(actual));
    }
}
