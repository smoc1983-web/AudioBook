package com.audiobookdownloader;
import java.net.URI;import java.text.Normalizer;import java.util.*;import java.nio.charset.StandardCharsets;import org.json.*;import org.jsoup.Jsoup;import org.jsoup.parser.Parser;
/** Only collection names/book references cross the import boundary. No app settings or credentials. */
public final class CollectionTransferRules {
 public static final int MAX_BYTES=1024*1024,MAX_SHELVES=256,MAX_BOOKS=10000;
 public static final String FORMAT="AudiobookCollections";
 public static final class Result {
  public final String collections;public final int addedShelves,addedBooks,filledReferences;
  Result(String value,int shelves,int books,int filled){collections=value;addedShelves=shelves;addedBooks=books;filledReferences=filled;}
 }
 static String key(String value){return Normalizer.normalize(value.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
 private static String string(JSONObject o,String field,int max,boolean required)throws Exception{
  Object v=o.opt(field);if(v==null||v==JSONObject.NULL){if(required)throw new IllegalArgumentException("Нет названия коллекции или книги");return "";}
  if(!(v instanceof String))throw new IllegalArgumentException("Некорректное название или ссылка");String s=((String)v).trim();
  if(s.length()>max||required&&s.isEmpty()||s.indexOf('\0')>=0)throw new IllegalArgumentException("Некорректное название или ссылка");return s;
 }
 private static String web(String value)throws Exception{if(value.isEmpty())return value;URI u=new URI(value);if(!("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("Файл содержит неподдерживаемую ссылку");return value;}
 public static JSONArray parse(String document)throws Exception{
  if(document==null||document.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Файл коллекций слишком большой");
  String s=document.trim();if(s.startsWith("\ufeff"))s=s.substring(1).trim();
  if(s.startsWith("<")){
   if(s.contains("<!DOCTYPE")||s.contains("<!ENTITY"))throw new IllegalArgumentException("Неподдерживаемый XML");
   var elements=Jsoup.parse(s,"",Parser.xmlParser()).select("map > string[name=shelves_v1]");if(elements.size()!=1)throw new IllegalArgumentException("В файле нет коллекций Audiobook");s=elements.first().wholeText();
  }
  int depth=0;boolean quoted=false,escaped=false;for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;}else if(ch=='"')quoted=true;else if(ch=='{'||ch=='['){if(++depth>32)throw new IllegalArgumentException("Слишком сложный файл коллекций");}else if(ch=='}'||ch==']')depth--;}
  JSONTokener tokener=new JSONTokener(s);Object root=tokener.nextValue();if(tokener.nextClean()!=0)throw new IllegalArgumentException("Лишние данные после коллекций");
  if(root instanceof JSONObject){JSONObject o=(JSONObject)root;
   if(o.has("format")){if(!FORMAT.equals(o.optString("format"))||o.optInt("version",-1)!=1)throw new IllegalArgumentException("Неподдерживаемая версия копии");root=o.getJSONArray("collections");}
   else if(o.opt("shelves_v1") instanceof String)return parse(o.getString("shelves_v1"));
   else throw new IllegalArgumentException("Это не резервная копия коллекций");
  }
  if(!(root instanceof JSONArray))throw new IllegalArgumentException("Это не резервная копия коллекций");JSONArray rows=(JSONArray)root;if(rows.length()>MAX_SHELVES)throw new IllegalArgumentException("Слишком много коллекций");
  JSONArray normalized=new JSONArray();int count=0;
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i);String name=string(row,"n",200,true);Object rawBooks=row.opt("b");if(!(rawBooks instanceof JSONArray))throw new IllegalArgumentException("Некорректный список книг");JSONArray books=(JSONArray)rawBooks,clean=new JSONArray();
   count+=books.length();if(count>MAX_BOOKS)throw new IllegalArgumentException("Слишком много книг");
   for(int j=0;j<books.length();j++){JSONObject b=books.getJSONObject(j);clean.put(new JSONObject().put("t",string(b,"t",1000,true)).put("u",web(string(b,"u",8192,false))).put("c",web(string(b,"c",8192,false))));}
   normalized.put(new JSONObject().put("n",name).put("b",clean));
  }
  return normalized;
 }
 public static String export(String raw)throws Exception{return new JSONObject().put("format",FORMAT).put("version",1).put("collections",parse(raw)).toString(2);}
 public static Result merge(String current,String incoming)throws Exception{
  JSONArray before=parse(current),addition=parse(incoming);LinkedHashMap<String,JSONObject> shelves=new LinkedHashMap<>();int addedShelves=0,addedBooks=0,filled=0;
  // Existing values take priority; only missing references are filled by an import.
  for(int pass=0;pass<2;pass++){
   JSONArray source=pass==0?before:addition;
   for(int i=0;i<source.length();i++){
    JSONObject row=source.getJSONObject(i);String id=key(row.getString("n"));JSONObject shelf=shelves.get(id);
    if(shelf==null){shelf=new JSONObject().put("n",row.getString("n")).put("b",new JSONArray());shelves.put(id,shelf);if(pass==1)addedShelves++;}
    JSONArray books=shelf.getJSONArray("b"),sourceBooks=row.getJSONArray("b");Map<String,JSONObject> index=new LinkedHashMap<>();for(int j=0;j<books.length();j++)index.put(key(books.getJSONObject(j).getString("t")),books.getJSONObject(j));
    for(int j=0;j<sourceBooks.length();j++){
     JSONObject book=sourceBooks.getJSONObject(j),old=index.get(key(book.getString("t")));
     if(old==null){JSONObject copy=new JSONObject(book.toString());books.put(copy);index.put(key(copy.getString("t")),copy);if(pass==1)addedBooks++;}
     else for(String field:new String[]{"u","c"})if(old.optString(field).isEmpty()&&!book.optString(field).isEmpty()){old.put(field,book.getString(field));if(pass==1)filled++;}
    }
   }
  }
  JSONArray result=new JSONArray();for(JSONObject row:shelves.values())result.put(row);
  String json=result.toString();parse(json);return new Result(json,addedShelves,addedBooks,filled);
 }
}
