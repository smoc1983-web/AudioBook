package com.audiobookdownloader;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Exact application source hosts; never follows off-site or arbitrary private-network URLs. */
public final class ContinuationPolicy {
 private final Map<String,String> hosts=new HashMap<>(),books=new HashMap<>();
 public ContinuationPolicy(String additions)throws JSONException {
  add("azbyka","azbyka.ru","www.azbyka.ru");add("bookinfo","bookinfo.net","www.bookinfo.net");
  add("knigoblud","knigoblud.club","www.knigoblud.club","mob.knigoblud.club");add("online","audiokniga-online.ru","www.audiokniga-online.ru");
  JSONArray rows=new JSONArray(additions);
  for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.getString("id");books.put(id,row.getString("book"));JSONArray hs=row.getJSONArray("hosts");for(int j=0;j<hs.length();j++)hosts.put(hs.getString(j),id);}
 }
 private void add(String id,String...names){for(String name:names)hosts.put(name,id);}
 public String source(String address){try{URI u=URI.create(address);if(!Arrays.asList("http","https").contains(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=80&&u.getPort()!=443))return "";String result=hosts.get(String.valueOf(u.getHost()).toLowerCase(Locale.ROOT));return result==null?"":result;}catch(Exception e){return "";}}
 public boolean same(String a,String b){String id=source(a);return !id.isEmpty()&&id.equals(source(b));}
 public String canonical(String address){try{
  if(source(address).isEmpty())return "";URI u=URI.create(address);String host=u.getHost().toLowerCase(Locale.ROOT),path=u.getRawPath();if(path==null||path.isEmpty())path="/";
  while(path.length()>1&&path.endsWith("/"))path=path.substring(0,path.length()-1);
  List<String> q=new ArrayList<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&"))if(!part.matches("(?i)(?:utm_[^=]*|fbclid|gclid)=.*"))q.add(part);
  return URI.create("https://"+host+path+(q.isEmpty()?"":"?"+String.join("&",q))).normalize().toASCIIString();
 }catch(Exception e){return "";}}
 public boolean book(String address){try{
  String id=source(address),p=URI.create(address).getPath();if(id.isEmpty()||p==null)return false;
  if(books.containsKey(id))return Pattern.compile(books.get(id),Pattern.CASE_INSENSITIVE).matcher(p).matches();
  if(id.equals("bookinfo"))return p.matches("/[^/]+/[^/]+/[^/]+/?")&&!p.matches("/(?:author|authors|series|cycle|genre|genres|search|user|api|page)/.*");
  if(id.equals("knigoblud"))return p.matches("(?i).*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/?");
  if(id.equals("azbyka"))return p.matches("/audio/[^/]+\\.html")&&!p.matches("/audio/(?:search|info|contact|karta).*\\.html");
  return id.equals("online")&&p.matches("/[^/]+/[0-9]+-[^/]+\\.html");
 }catch(Exception e){return false;}}
 public boolean series(String address){try{String p=URI.create(address).getPath();return !source(address).isEmpty()&&p!=null&&Pattern.compile("(?i)/(?:series|serie|cycle|cycles|cikl|cikly|cikls|serii|seriya|sequence)(?:/|[0-9])|/audio/(?:cikl|seriya)[-/]").matcher(p).find();}catch(Exception e){return false;}}
 public boolean nextPage(String root,String next){try{
  if(!same(root,next)||book(next))return false;
  String a=URI.create(canonical(root)).getPath(),b=URI.create(canonical(next)).getPath();
  return b.equals(a)||b.matches(Pattern.quote(a)+"/(?:page/)?[0-9]+");
 }catch(Exception e){return false;}}
}
