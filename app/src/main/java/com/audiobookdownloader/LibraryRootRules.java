package com.audiobookdownloader;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Document IDs are opaque: neither filesystem prefix matching nor String.hashCode identifies a book. */
final class LibraryRootRules {
 static final int MAX_ROOTS=32;
 static String treeKey(String raw) {return key(raw,2);}
 static String documentKey(String raw) {return key(raw,4);}
 private static String key(String raw,int field) {
  try {
   URI uri=parse(raw);if(uri==null)return "";
   String[] parts=uri.getRawPath().split("/",-1);
   if(!shape(parts))return "";
   if(field==4&&parts.length==3)return "";
   if(!ids(parts))return "";
   return uri.getRawAuthority()+"\u0000"+decode(parts[field]);
  }catch(Exception invalid){return "";}
 }
 private static URI parse(String raw)throws Exception {
  if(raw==null||raw.length()>8192)return null;
  URI uri=new URI(raw);return base(uri)?uri:null;
 }
 private static boolean base(URI uri) {
  return "content".equals(uri.getScheme())&&uri.getRawAuthority()!=null&&uri.getRawAuthority().matches("[A-Za-z0-9._-]+")&&uri.getRawQuery()==null&&uri.getRawFragment()==null;
 }
 private static boolean shape(String[] p) {
  if(p.length!=3&&p.length!=5)return false;
  if(!p[1].equals("tree"))return false;
  return p.length==3||p[3].equals("document");
 }
 private static boolean ids(String[] p)throws Exception {
  return !decode(p[2]).isEmpty()&&(p.length==3||!decode(p[4]).isEmpty());
 }
 private static String decode(String raw)throws Exception {
  String value=new URI("content://provider/"+raw).getPath().substring(1);
  if(value.isEmpty())return "";
  for(int n=0;n<value.length();n++)if(Character.isISOControl(value.charAt(n)))return "";
  return value;
 }
 static List<String> add(List<String> rows,String raw) {
  String key=treeKey(raw);if(key.isEmpty())throw new IllegalArgumentException("Нужна ссылка системного выбора папки");
  ArrayList<String> result=new ArrayList<>(rows);
  for(String row:rows)if(treeKey(row).equals(key))return result;
  if(rows.size()>=MAX_ROOTS)throw new IllegalArgumentException("Можно подключить не более 32 папок");
  result.add(raw);return result;
 }
}
