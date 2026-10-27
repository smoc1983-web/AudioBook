package com.audiobookdownloader;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Only validated collection names/book references cross the import boundary. */
public final class CollectionTransferRules {
 public static final int MAX_BYTES=1024*1024,MAX_SHELVES=256,MAX_BOOKS=10000;
 public static final String FORMAT="AudiobookCollections";
 public static final class Result {
  public final String collections;public final int addedShelves,addedBooks,filledReferences;
  Result(String value,int shelves,int books,int filled){collections=value;addedShelves=shelves;addedBooks=books;filledReferences=filled;}
 }
 static String key(String value){return Normalizer.normalize(value.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
 private static String string(JSONObject object,String field,int max,boolean required)throws Exception{
  Object value=object.opt(field);
  if(value==null||value==JSONObject.NULL){
   if(required)throw new IllegalArgumentException("Нет названия коллекции или книги");
   return "";
  }
  if(!(value instanceof String))throw new IllegalArgumentException("Некорректное название или ссылка");
  String text=((String)value).trim();
  if(text.length()>max||text.indexOf('\0')>=0)throw new IllegalArgumentException("Некорректное название или ссылка");
  if(required&&text.isEmpty())throw new IllegalArgumentException("Нет названия коллекции или книги");
  return text;
 }
 private static String web(String value)throws Exception{
  if(value.isEmpty())return value;
  URI uri=new URI(value);
  boolean http="https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme());
  if(!http||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Файл содержит неподдерживаемую ссылку");
  return value;
 }
 private static String document(String input)throws Exception{
  if(input==null)throw new IllegalArgumentException("Нет файла коллекций");
  if(input.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Файл коллекций слишком большой");
  String text=input.trim();if(text.startsWith("\ufeff"))text=text.substring(1).trim();
  return text.startsWith("<")?CollectionXmlReader.read(text):text;
 }
 private static final class JsonDepth {
  int depth;boolean quoted,escaped;
  void accept(char value){
   if(quoted){quoted(value);return;}
   if(value=='"'){quoted=true;return;}
   if(value=='{'||value=='['){open();return;}
   if(value=='}'||value==']')depth--;
  }
  void quoted(char value){
   if(escaped)escaped=false;
   else if(value=='\\')escaped=true;
   else if(value=='"')quoted=false;
  }
  void open(){
   if(++depth>32)throw new IllegalArgumentException("Слишком сложный файл коллекций");
  }
 }
 private static Object root(String text)throws Exception{
  JsonDepth depth=new JsonDepth();for(int i=0;i<text.length();i++)depth.accept(text.charAt(i));
  JSONTokener tokener=new JSONTokener(text);Object value=tokener.nextValue();
  if(tokener.nextClean()!=0)throw new IllegalArgumentException("Лишние данные после коллекций");
  return value;
 }
 private static Object unwrap(Object value)throws Exception{
  if(!(value instanceof JSONObject))return value;
  JSONObject object=(JSONObject)value;
  if(object.has("format")){
   if(!FORMAT.equals(object.optString("format"))||object.optInt("version",-1)!=1)
    throw new IllegalArgumentException("Неподдерживаемая версия копии");
   return object.getJSONArray("collections");
  }
  if(object.opt("shelves_v1") instanceof String)return parse(object.getString("shelves_v1"));
  throw new IllegalArgumentException("Это не резервная копия коллекций");
 }
 private static JSONObject book(JSONObject raw)throws Exception{
  return new JSONObject().put("t",string(raw,"t",1000,true))
   .put("u",web(string(raw,"u",8192,false))).put("c",web(string(raw,"c",8192,false)));
 }
 private static JSONArray normalize(JSONArray rows)throws Exception{
  if(rows.length()>MAX_SHELVES)throw new IllegalArgumentException("Слишком много коллекций");
  JSONArray result=new JSONArray();int count=0;
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i);String name=string(row,"n",200,true);Object raw=row.opt("b");
   if(!(raw instanceof JSONArray))throw new IllegalArgumentException("Некорректный список книг");
   JSONArray books=(JSONArray)raw,clean=new JSONArray();count+=books.length();
   if(count>MAX_BOOKS)throw new IllegalArgumentException("Слишком много книг");
   for(int j=0;j<books.length();j++)clean.put(book(books.getJSONObject(j)));
   result.put(new JSONObject().put("n",name).put("b",clean));
  }
  return result;
 }
 public static JSONArray parse(String input)throws Exception{
  Object value=unwrap(root(document(input)));
  if(!(value instanceof JSONArray))throw new IllegalArgumentException("Это не резервная копия коллекций");
  return normalize((JSONArray)value);
 }
 public static String export(String raw)throws Exception{
  return new JSONObject().put("format",FORMAT).put("version",1).put("collections",parse(raw)).toString(2);
 }
 private static int fillReferences(JSONObject old,JSONObject fresh)throws Exception{
  int count=0;
  for(String field:new String[]{"u","c"}){
   if(old.optString(field).isEmpty()&&!fresh.optString(field).isEmpty()){old.put(field,fresh.getString(field));count++;}
  }
  return count;
 }
 private static final class Shelf {
  final JSONObject value;
  final Map<String,JSONObject> books=new LinkedHashMap<>();
  Shelf(String name)throws Exception{value=new JSONObject().put("n",name).put("b",new JSONArray());}
 }
 private static final class Merge {
  final Map<String,Shelf> shelves=new LinkedHashMap<>();
  int addedShelves,addedBooks,filled;
  Shelf shelf(String name,boolean incoming)throws Exception{
   String identity=key(name);Shelf shelf=shelves.get(identity);
   if(shelf==null){shelf=new Shelf(name);shelves.put(identity,shelf);if(incoming)addedShelves++;}
   return shelf;
  }
  void books(Shelf shelf,JSONArray books,boolean incoming)throws Exception{
   for(int i=0;i<books.length();i++){
    JSONObject fresh=books.getJSONObject(i);String identity=key(fresh.getString("t"));JSONObject old=shelf.books.get(identity);
    if(old!=null){filled+=fillReferences(old,fresh);continue;}
    JSONObject copy=new JSONObject(fresh.toString());shelf.value.getJSONArray("b").put(copy);shelf.books.put(identity,copy);
    if(incoming)addedBooks++;
   }
  }
  void add(JSONArray rows,boolean incoming)throws Exception{
   for(int i=0;i<rows.length();i++){
    JSONObject row=rows.getJSONObject(i);books(shelf(row.getString("n"),incoming),row.getJSONArray("b"),incoming);
   }
  }
  Result result()throws Exception{
   JSONArray rows=new JSONArray();for(Shelf shelf:shelves.values())rows.put(shelf.value);
   String json=rows.toString();parse(json);return new Result(json,addedShelves,addedBooks,filled);
  }
 }
 public static Result merge(String current,String incoming)throws Exception{
  Merge merge=new Merge();merge.add(parse(current),false);merge.add(parse(incoming),true);return merge.result();
 }
}
