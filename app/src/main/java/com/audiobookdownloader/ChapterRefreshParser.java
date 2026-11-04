package com.audiobookdownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Conservative token refresh: unsupported page formats require reopening the book. */
public final class ChapterRefreshParser {
    private ChapterRefreshParser() {}
    private static final Pattern PLAYER = Pattern.compile("(?:(?:KB|App)\\.playerInit|new\\s+XSPlayer)\\s*\\(\\s*(?=\\{)");
    public static String refresh(String html, String chapter, String original) {
        if (html.length() > 1024*1024) return null;
        List<String> titles=new ArrayList<>(), urls=new ArrayList<>();
        for (Element script : Jsoup.parse(html).select("script:not([src])")) {
            String text=script.data();
            java.util.regex.Matcher call=PLAYER.matcher(text);
            while (call.find()) readTracks(text.substring(call.end()), titles, urls);
        }
        int at=ChapterRefreshRules.match(chapter,titles);
        if (at < 0) return null;
        String url=urls.get(at);
        return !original.equals(url) && ChapterRefreshRules.sameResource(original,url) ? url : null;
    }
    private static void readTracks(String json, List<String> titles, List<String> urls) {
        try {
            Object parsed=new JSONTokener(json).nextValue();
            if (!(parsed instanceof JSONObject)) return;
            JSONObject config=(JSONObject)parsed;
            JSONArray tracks=config.optJSONArray("tracks");
            if (tracks==null || tracks.length()>10000) return;
            for (int i=0;i<tracks.length();i++) {
                JSONObject track=tracks.optJSONObject(i);
                JSONArray compact=tracks.optJSONArray(i);
                if (compact!=null && compact.length()>=5) {
                    titles.add(compact.optString(1));
                    urls.add("https://"+config.optString("mp3_url_prefix")+"/"+compact.optString(4)+config.optString("sign"));
                }
                if (track==null) continue;
                titles.add(track.optString("title")); urls.add(track.optString("src"));
            }
        } catch (org.json.JSONException ignored) { /* Dynamic/unsupported players fail closed. */ }
    }
}
