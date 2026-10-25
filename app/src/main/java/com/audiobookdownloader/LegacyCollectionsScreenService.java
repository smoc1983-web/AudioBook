package com.audiobookdownloader;
import android.accessibilityservice.AccessibilityService;import android.os.*;import android.view.accessibility.*;import java.util.*;
/** User-started, ten-minute, read-only capture restricted to the original Audiobook package. */
public final class LegacyCollectionsScreenService extends AccessibilityService {
 private static LegacyCollectionScreens session;private static long deadline;private static LegacyCollectionsScreenService connected;private final Handler handler=new Handler(Looper.getMainLooper());
 static synchronized void begin(){session=new LegacyCollectionScreens();deadline=SystemClock.elapsedRealtime()+10*60*1000;if(connected!=null)connected.scheduleExpiry();}
 static synchronized boolean isConnected(){return connected!=null;}
 static synchronized LegacyCollectionScreens snapshot(){return session;}
 static synchronized void finish(){deadline=0;if(connected!=null)connected.disableSelf();}
 static synchronized void clear(){session=null;finish();}
 private void scheduleExpiry(){handler.removeCallbacksAndMessages(null);handler.postDelayed(()->{synchronized(LegacyCollectionsScreenService.class){if(deadline>0&&SystemClock.elapsedRealtime()>=deadline){deadline=0;disableSelf();}}},Math.max(1,deadline-SystemClock.elapsedRealtime()));}
 @Override protected void onServiceConnected(){synchronized(LegacyCollectionsScreenService.class){connected=this;if(session!=null&&deadline>SystemClock.elapsedRealtime())scheduleExpiry();else disableSelf();}}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){synchronized(LegacyCollectionsScreenService.class){if(session==null||deadline<=SystemClock.elapsedRealtime())return;if(event.getPackageName()==null||!LegacyCollectionsReader.LEGACY.contentEquals(event.getPackageName()))return;
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;try{if(root.getPackageName()==null||!LegacyCollectionsReader.LEGACY.contentEquals(root.getPackageName()))return;List<String> texts=new ArrayList<>();boolean[] back={false,false};walk(root,texts,back,0);session.accept(texts,back[0],back[1]);}finally{root.recycle();}
 }}
 private void walk(AccessibilityNodeInfo node,List<String> texts,boolean[] back,int depth){if(depth>64||texts.size()>2000||node.isPassword()||node.isEditable()||!node.isVisibleToUser())return;CharSequence desc=node.getContentDescription();if(desc!=null&&desc.toString().equals("Все коллекции"))back[0]=true;if(desc!=null&&desc.toString().equals("Переименовать коллекцию"))back[1]=true;CharSequence text=node.getText();if(text!=null&&text.length()<4096)texts.add(text.toString());for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){try{walk(child,texts,back,depth+1);}finally{child.recycle();}}}}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){synchronized(LegacyCollectionsScreenService.class){if(connected==this)connected=null;}handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
