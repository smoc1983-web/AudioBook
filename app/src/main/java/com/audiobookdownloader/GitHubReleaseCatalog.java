package com.audiobookdownloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Anonymous GitHub API discovery. Prerelease is not a channel: published Preview 91 is not marked prerelease. */
final class GitHubReleaseCatalog {
    static final int MAX_PAGES=10;
    static String pageUrl(int page) throws IOException {
        if(page<1||page>MAX_PAGES)throw new IOException("Слишком много выпусков. Повторите проверку позже.");
        return "https://api.github.com/repos"+BuildConfig.UPDATE_REPOSITORY+"/releases?per_page=100&page="+page;
    }
    static final class Asset {
        final String url,hash; final long size;
        Asset(JSONObject value,String tag,String name)throws Exception {
            if(value.getLong("id")<=0||!"uploaded".equals(value.getString("state"))||!name.equals(value.getString("name")))throw new IOException("Файл выпуска ещё не готов");
            url=value.getString("browser_download_url");size=value.getLong("size");
            if(!PreviewUpdateRules.assetUrl(tag,name).equals(url)||size<=0||size>PreviewUpdateRules.MAX_APK)throw new IOException("Некорректный файл выпуска GitHub");
            String digest=value.optString("digest","");
            if(!digest.matches("sha256:[0-9a-f]{64}"))throw new IOException("GitHub не предоставил SHA-256 файла выпуска. Обратитесь к разработчику.");
            hash=digest.substring(7);
        }
    }
    static final class Release {
        final String tag;final long code,id;final Asset metadata,apk;final JSONObject source;
        Release(JSONObject value)throws Exception {
            source=new JSONObject(value.toString());
            tag=value.getString("tag_name");code=PreviewUpdateRules.tagCode(tag);id=value.getLong("id");
            if(id<=0||value.getBoolean("draft")||!value.getString("html_url").equals(BuildConfig.UPDATE_ORIGIN+BuildConfig.UPDATE_REPOSITORY+"/releases/tag/"+tag))throw new IOException("Некорректный выпуск GitHub");
            metadata=asset(value,tag,BuildConfig.UPDATE_METADATA);apk=asset(value,tag,BuildConfig.UPDATE_APK);
            if(metadata.size>65536)throw new IOException("Слишком большое описание выпуска");
        }
        PreviewUpdateRules rules(String text)throws Exception {
            if(text.getBytes(StandardCharsets.UTF_8).length!=metadata.size||!PreviewUpdateRules.sha256(text.getBytes(StandardCharsets.UTF_8)).equals(metadata.hash))throw new IOException("Описание выпуска повреждено или изменилось. Повторите проверку.");
            JSONObject json=new JSONObject(text);
            if(!json.optBoolean("available",true))throw new IOException("Подписанное обновление ещё не опубликовано");
            PreviewUpdateRules rules=new PreviewUpdateRules(json,tag);
            if(rules.size!=apk.size||!rules.sha256.equals(apk.hash))throw new IOException("SHA-256 или размер APK не совпал с данными GitHub Releases");
            return rules;
        }
    }
    private static Asset asset(JSONObject value,String tag,String name)throws Exception {
        JSONArray assets=value.getJSONArray("assets");JSONObject found=null;
        for(int i=0;i<assets.length();i++)if(name.equals(assets.getJSONObject(i).optString("name"))){
            if(found!=null)throw new IOException("Дублирующийся файл выпуска");found=assets.getJSONObject(i);
        }
        if(found==null)throw new IOException("В выпуске отсутствует файл "+name);
        return new Asset(found,tag,name);
    }
    static JSONObject select(JSONArray page,JSONObject best)throws Exception {
        long highest=best==null?0:PreviewUpdateRules.tagCode(best.getString("tag_name"));
        for(int i=0;i<page.length();i++){
            JSONObject value=page.getJSONObject(i);String tag=value.optString("tag_name","");
            if(value.optBoolean("draft",false)||!tag.startsWith(BuildConfig.UPDATE_PREFIX))continue;
            // A malformed tag in our channel is a publishing error, not permission to fall back silently.
            long code=PreviewUpdateRules.tagCode(tag);
            if(code>highest){highest=code;best=value;}
            else if(code==highest&&best!=null&&value.getLong("id")!=best.getLong("id"))throw new IOException("Неоднозначный выпуск канала");
        }
        return best;
    }
    static Release latest(PreviewUpdateHttp http)throws Exception {
        JSONObject best=null;
        for(int page=1;page<=MAX_PAGES;page++){
            JSONArray values=new JSONArray(http.read(pageUrl(page),"application/vnd.github+json",2*1024*1024));
            best=select(values,best);
            if(values.length()<100){
                if(best==null)throw new IOException("Публичный выпуск этого канала не найден в GitHub Releases");
                return new Release(best);
            }
        }
        throw new IOException("Список выпусков слишком велик. Не удалось достоверно выбрать обновление.");
    }
}
