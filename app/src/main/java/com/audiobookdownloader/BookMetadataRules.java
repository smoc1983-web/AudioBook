package com.audiobookdownloader;

/** Display overrides never participate in chapter, edition or folder identity. */
final class BookMetadataRules {
 private BookMetadataRules() {}
 static boolean validId(String id) {
  return id!=null&&id.matches("[bl]3-[0-9a-f]{64}");
 }
 static boolean validText(String text,boolean required) {
  if(text==null||text.length()>512)return false;
  if(required&&text.trim().isEmpty())return false;
  for(int n=0;n<text.length();n++)if(Character.isISOControl(text.charAt(n)))return false;
  return true;
 }
 static boolean validVolume(String text) {
  if(text==null)return false;
  if(text.isEmpty())return true;
  if(!text.matches("[0-9]{1,4}(\\.[0-9]{1,2})?"))return false;
  return Double.parseDouble(text)>0;
 }
}
