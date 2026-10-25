"""Exercise the read-only shell bridge on code-free old-package fixtures, never a user device."""
import os,subprocess,json,hashlib,xml.sax.saxutils as X
import glob
from pathlib import Path
assert os.environ.get('CI')=='true'
def run(*args):
 try:return subprocess.check_output(list(args),text=True,stderr=subprocess.STDOUT,timeout=120)
 except subprocess.CalledProcessError as e:
  print(e.output[-6000:]);raise
assert run('adb','get-serialno').strip().startswith('emulator-')
old='com.audiobookdownloader';new='com.audiobookdownloader.preview';out=Path('startup-evidence');out.mkdir(exist_ok=True)
def write(package,relative,data):
 subprocess.run(['adb','exec-in','run-as',package,'dd','of='+relative,'bs=65536'],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=30)
 assert run('adb','shell','run-as',package,'sha256sum',relative).split()[0]==hashlib.sha256(data).hexdigest()
subprocess.run(['adb','uninstall',old],capture_output=True)
try:
 assert 'Success' in run('adb','install','legacyFixture/build/outputs/apk/debug/legacyFixture-debug.apk')
 shelves=[{'n':'Проверка обновления','b':[{'t':'Общая книга','u':'','c':''}]},{'n':'Из старой Audiobook','b':[{'t':'Старая книга','u':'https://bookinfo.net/a/b','c':''}]}]
 raw=json.dumps(shelves,ensure_ascii=False);xml=('<?xml version="1.0" encoding="utf-8"?><map><string name="secret_not_collection">do-not-copy</string><string name="shelves_v1">'+X.escape(raw)+'</string></map>').encode()
 run('adb','shell','run-as',old,'mkdir','-p','shared_prefs');write(old,'shared_prefs/audiobook_downloader.xml',xml)
 apk=run('adb','shell','pm','path',new).strip().splitlines()[0].removeprefix('package:')
 def read():
  result=run('adb','shell','CLASSPATH='+apk,'app_process','/','com.audiobookdownloader.LegacyCollectionsReader');line=next(l for l in result.splitlines() if l.startswith('AUDIOBOOK_COLLECTIONS:'));return json.loads(line.split(':',1)[1])
 reply=read();assert reply['ok'] and 'do-not-copy' not in json.dumps(reply);assert len(json.loads(reply['data'])['collections'])==2
 run('adb','shell','run-as',new,'mkdir','-p','cache');payload=json.dumps(reply,ensure_ascii=False).encode();write(new,'cache/legacy-snapshot.json',payload)
 result=run('adb','shell','am','instrument','-w','-r','-e','class','com.audiobookdownloader.CollectionTransferTest#legacySnapshotMergesIntoRealCollections','-e','legacy_snapshot','/data/user/0/'+new+'/cache/legacy-snapshot.json',new+'.test/androidx.test.runner.AndroidJUnitRunner');(out/'legacy-import-native.txt').write_text(result);assert 'OK (1 test)' in result,result
 assert run('adb','shell','run-as',old,'sha256sum','shared_prefs/audiobook_downloader.xml').split()[0]==hashlib.sha256(xml).hexdigest()
 assert 'Success' in run('adb','install','-r','legacyFixture/build/outputs/apk/release/legacyFixture-release.apk')
 blocked=read();assert not blocked['ok'] and blocked['code']=='NOT_DEBUGGABLE' and 'do-not-copy' not in json.dumps(blocked)
 # Exercise the actual previous stable UI as a non-debuggable release, signed only with a CI test key.
 buildtools=Path(os.environ['ANDROID_HOME'])/'build-tools'/'35.0.0';signed=Path('upgrade-baseline/legacy-stable-ci-test.apk')
 run(str(buildtools/'apksigner'),'sign','--ks',os.environ['LEGACY_FIXTURE_KEYSTORE'],'--ks-key-alias','fixture','--ks-pass','pass:android','--key-pass','pass:android','--out',str(signed),'upgrade-baseline/legacy-stable-unsigned.apk')
 assert 'Success' in run('adb','install','-r',str(signed))
 run('adb','shell','pm','grant',old,'android.permission.READ_MEDIA_AUDIO');run('adb','shell','pm','grant',old,'android.permission.POST_NOTIFICATIONS')
 try:run('adb','shell','run-as',old,'id');raise AssertionError('Historical stable fixture must not be debuggable')
 except subprocess.CalledProcessError:pass
 result=run('adb','shell','am','instrument','-w','-r','-e','class','com.audiobookdownloader.CollectionTransferTest#legacyReleaseScreensTransferWithoutPrivateAccess','-e','legacy_screens','true',new+'.test/androidx.test.runner.AndroidJUnitRunner');(out/'legacy-screen-native.txt').write_text(result)
 if 'OK (1 test)' not in result:
  dump=subprocess.run(['adb','exec-out','run-as',new,'cat','cache/legacy-screen-failure.xml'],capture_output=True);(out/'legacy-screen-failure.xml').write_bytes(dump.stdout)
 assert 'OK (1 test)' in result,result
 report={'shellReaderMatchesShizukuUserService':True,'debuggableOldPackageRead':True,'oldPrivateFileUnchangedByDirectRead':True,'otherPrivateSettingsNotTransferred':True,'realNewCollectionStoreMerged':True,'repeatImportIdempotent':True,'releaseOldPackageRejectedByShellReader':True,'actualPreviousStableReleaseUiReadViaAccessibility':True,'screenImportHasTitlesAndMembershipOnly':True,'rootUsed':False,'actualShizukuManagerPairingTested':False,'physicalPhone':False}
 (out/'legacy-collections.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
finally:subprocess.run(['adb','uninstall',old],capture_output=True)
