package com.audiobookdownloader;
import java.util.*;import java.util.regex.*;import org.json.*;
/** Conservative parser for the legacy Collections UI. Only complete shelves can be imported. */
public final class LegacyCollectionScreens {
 private final LinkedHashMap<String,Shelf> shelves=new LinkedHashMap<>();private Shelf active;
 private static final Pattern COUNT=Pattern.compile("^(\\d{1,5}) книг$");
 private static final String CAPTION="Собирайте книги на своих полках";
 private static final class Shelf{String name;int expected=-1;LinkedHashMap<String,String> books=new LinkedHashMap<>();Shelf(String n){name=n;}}
 private Shelf shelf(String name){return shelves.computeIfAbsent(CollectionTransferRules.key(name),k->new Shelf(name));}
 public synchronized void accept(List<String> raw,boolean backToAll){accept(raw,backToAll,false);}
 public synchronized void accept(List<String> raw,boolean backToAll,boolean allControls){
  ArrayList<String> rows=new ArrayList<>();for(String value:raw)for(String line:value.split("\\n")){String s=line.trim();if(!s.isEmpty())rows.add(s);}
  int caption=rows.indexOf(CAPTION);if(rows.contains("Название коллекции")||rows.contains("Новая коллекция"))return;
  if(caption<1){if(active!=null&&rows.contains("В коллекции")&&rows.contains("Убрать из коллекции"))readBooks(rows,active,1);else if(allControls){active=null;readCounts(rows,1);}return;}
  if(!backToAll){if(!rows.subList(0,caption).contains("Мои коллекции"))return;active=null;readCounts(rows,caption+2);return;}
  String name=rows.get(caption-1);if(name.length()>200)return;active=shelves.get(CollectionTransferRules.key(name));if(active==null||active.expected<0)return;readBooks(rows,active,caption+2);
 }
 private void readCounts(List<String> rows,int start){for(int i=start;i<rows.size();i++){Matcher m=COUNT.matcher(rows.get(i));if(m.matches()){String name=rows.get(i-1);if(name.length()<=200&&(shelves.containsKey(CollectionTransferRules.key(name))||shelves.size()<CollectionTransferRules.MAX_SHELVES)){Shelf s=shelf(name);int count=Integer.parseInt(m.group(1));if(s.expected!=count)s.books.clear();s.expected=count;}}}}
 private void readBooks(List<String> rows,Shelf current,int start){for(int i=start;i<rows.size();i++)if(rows.get(i).equals("В коллекции")){String title=rows.get(i-1);if(title.length()<=1000&&current.books.size()<CollectionTransferRules.MAX_BOOKS)current.books.putIfAbsent(CollectionTransferRules.key(title),title);}}

 public synchronized int complete(){int n=0;for(Shelf s:shelves.values())if(s.expected==s.books.size())n++;return n;}
 public synchronized int partial(){return shelves.size()-complete();}
 public synchronized String export()throws Exception{JSONArray out=new JSONArray();for(Shelf s:shelves.values())if(s.expected==s.books.size()){JSONArray books=new JSONArray();for(String title:s.books.values())books.put(new JSONObject().put("t",title).put("u","").put("c",""));out.put(new JSONObject().put("n",s.name).put("b",books));}return CollectionTransferRules.export(out.toString());}
}
