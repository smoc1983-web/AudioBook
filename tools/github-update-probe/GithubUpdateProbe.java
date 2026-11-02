package com.audiobookdownloader;
import java.io.*;import java.nio.file.*;import org.json.JSONObject;
/** Runs the exact Android-independent production discovery/transport/rules against public GitHub. */
public final class GithubUpdateProbe {
 public static void main(String[] args)throws Exception {
  PreviewUpdateHttp http=new PreviewUpdateHttp();
  String tag=PreviewUpdateRules.latestTag(http.read(PreviewUpdateRules.FEED,"application/atom+xml",2*1024*1024));
  if(args.length>1&&!tag.equals(args[1]))throw new IOException("Unexpected latest tag: "+tag);
  String metadataUrl=PreviewUpdateRules.assetUrl(tag,BuildConfig.UPDATE_METADATA);
  JSONObject metadata=new JSONObject(http.read(metadataUrl,"application/octet-stream",65536));
  PreviewUpdateRules info=new PreviewUpdateRules(metadata,tag);
  Path out=Paths.get(args[0]);Files.createDirectories(out);
  Path apk=out.resolve(BuildConfig.UPDATE_APK);
  try(OutputStream stream=Files.newOutputStream(apk)){http.download(info,stream,p->{});}
  JSONObject result=new JSONObject().put("tag",tag).put("versionCode",info.versionCode).put("versionName",info.versionName).put("feedUrl",PreviewUpdateRules.FEED).put("metadataUrl",metadataUrl).put("apkUrl",info.parts.get(0).url).put("bytes",Files.size(apk)).put("sha256",info.sha256).put("certificateSha256",info.certificateSha256).put("publicNoCredentials",true).put("productionJavaTransportAndRules",true);
  Files.writeString(out.resolve("public-github-check.json"),result.toString(2)+"\n");
  Files.writeString(out.resolve(BuildConfig.UPDATE_METADATA),metadata.toString(2)+"\n");
  System.out.println(result.toString(2));
 }
}
