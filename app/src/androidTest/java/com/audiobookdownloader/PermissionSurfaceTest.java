package com.audiobookdownloader;
import android.content.*;import android.content.pm.*;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.*;import static org.junit.Assert.*;
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class PermissionSurfaceTest {
 @Test public void installedPlayerHasNoScreenReaderOrPrivilegedTransferComponents()throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS);
  if(p.requestedPermissions!=null)for(String permission:p.requestedPermissions)assertFalse(permission,permission.contains("shizuku")||permission.equals("android.permission.READ_SMS")||permission.equals("android.permission.RECEIVE_SMS")||permission.equals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")||permission.equals("android.permission.BIND_ACCESSIBILITY_SERVICE"));
  if(p.services!=null)for(ServiceInfo service:p.services){assertNotEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",service.permission);assertNotEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",service.permission);}
  if(p.providers!=null)for(ProviderInfo provider:p.providers)assertFalse(provider.name,provider.name.startsWith("rikka.shizuku."));
  for(String name:new String[]{"LegacyCollectionsBridge","LegacyCollectionsScreenService","LegacyCollectionsReader","LegacyCollectionScreens","ILegacyCollections"})try{c.getClassLoader().loadClass("com.audiobookdownloader."+name);fail("Retired implementation shipped: "+name);}catch(ClassNotFoundException expected){}
 }
}
