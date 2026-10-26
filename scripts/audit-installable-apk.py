"""Audit the shipped APK's manifest and defined DEX classes; never claims Google approval."""
import argparse,json,re,struct,subprocess,zipfile
from pathlib import Path
FORBIDDEN_PERMISSIONS={'android.permission.BIND_ACCESSIBILITY_SERVICE','android.permission.BIND_NOTIFICATION_LISTENER_SERVICE','android.permission.READ_SMS','android.permission.RECEIVE_SMS','android.permission.READ_CALL_LOG','android.permission.READ_PHONE_STATE','android.permission.SYSTEM_ALERT_WINDOW','android.permission.QUERY_ALL_PACKAGES','android.permission.MANAGE_EXTERNAL_STORAGE','moe.shizuku.manager.permission.API_V23'}
ALLOWED={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.WAKE_LOCK','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK','android.permission.READ_MEDIA_AUDIO','android.permission.READ_EXTERNAL_STORAGE','android.permission.WRITE_EXTERNAL_STORAGE','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.POST_NOTIFICATIONS','android.permission.MODIFY_AUDIO_SETTINGS','android.permission.RECEIVE_BOOT_COMPLETED'}
RETIRED=('Lcom/audiobookdownloader/LegacyCollections','Lcom/audiobookdownloader/LegacyCollectionScreens','Lcom/audiobookdownloader/ILegacyCollections','Lrikka/shizuku/')
def audit_manifest(identity,manifest,package):
 assert "package: name='"+package+"'" in identity,'Wrong package'
 assert 'application-debuggable' not in identity,'Installable APK must be a release without debugging'
 debug=re.search(r'android:debuggable[^\n]*',manifest)
 assert debug is None or re.search(r'=\(type 0x12\)0x0(?:\s|$)',debug[0]),'Manifest permits debugging'
 permissions=sorted(set(re.findall(r"^uses-permission[^:]*: name='([^']+)'",identity,re.M)))
 assert not set(permissions)&FORBIDDEN_PERMISSIONS,'Unexpected sensitive permission'
 assert not set(permissions)-(ALLOWED|{package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}),'Unreviewed permission: '+str(set(permissions)-ALLOWED-{package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'})
 assert not any(p in manifest for p in FORBIDDEN_PERMISSIONS),'Sensitive service binding or permission remains'
 assert 'LegacyCollectionsScreenService' not in manifest and 'rikka.shizuku' not in manifest,'Privileged component remains'
 return permissions

def defined_types(data):
 assert data.startswith(b'dex\n'),'Not DEX'
 u32=lambda p:struct.unpack_from('<I',data,p)[0]
 def string(p):
  while data[p]&128:p+=1
  p+=1;return data[p:data.index(0,p)].decode('utf-8',errors='replace')
 strings=[string(u32(u32(60)+4*i)) for i in range(u32(56))]
 types=[strings[u32(u32(68)+4*i)] for i in range(u32(64))]
 return [types[u32(u32(100)+32*i)] for i in range(u32(96))]

def audit_types(types):
 assert not any(t.startswith(RETIRED) for t in types),'Retired privileged implementation remains in APK'

def audit(apk,tools):
 identity=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
 manifest=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
 permissions=audit_manifest(identity,manifest,'com.audiobookdownloader.preview')
 classes=[]
 with zipfile.ZipFile(apk) as z:
  for name in z.namelist():
   if re.fullmatch(r'classes\d*\.dex',name):classes.extend(defined_types(z.read(name)))
 audit_types(classes)
 assert classes,'Missing application classes'
 return {'packageName':'com.audiobookdownloader.preview','debuggable':False,'requestedPermissions':permissions,'noAccessibilityService':True,'noNotificationListener':True,'noShizuku':True,'retiredClassesAbsent':True,'definedClassCount':len(classes),'googlePlayProtectVerdictTested':False,'guidance':'https://developers.google.com/android/play-protect/warning-dev-guidance'}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--apk',required=True,type=Path);p.add_argument('--build-tools',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args();result=audit(a.apk,a.build_tools);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
