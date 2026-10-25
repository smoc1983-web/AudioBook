"""Real published-baseline->next replacement and native old-updater validation in CI's emulator only."""
import subprocess,json,os,re,uuid,hashlib,xml.sax.saxutils as X
from pathlib import Path
assert os.environ.get('CI')=='true','Never run on a user phone'
def run(*args):return subprocess.check_output(list(args),text=True,stderr=subprocess.STDOUT,timeout=60)
serial=run('adb','get-serialno').strip();assert serial.startswith('emulator-'),'Disposable emulator required'
package='com.audiobookdownloader.preview';tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0';old=Path('upgrade-baseline/Audiobook-Preview.apk');new=Path('app/build/outputs/apk/debug/app-debug.apk');tests=Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk');out=Path('startup-evidence');out.mkdir(exist_ok=True)
def cert(apk):return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',run(str(tools/'apksigner'),'verify','--print-certs',str(apk)))[1]
assert cert(old)==cert(new)=='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
assert cert(tests)==cert(new),'Instrumentation must keep the same signing identity'
# Only throwaway tests from this job are removed; no physical device or stable package is touched.
subprocess.run(['adb','uninstall',package],capture_output=True);subprocess.run(['adb','uninstall',package+'.test'],capture_output=True)
assert 'Success' in run('adb','install',str(old));assert 'Success' in run('adb','install','-t',str(tests))
marker='upgrade-'+uuid.uuid4().hex;fixture=out/'upgrade-fixture.xml';tree='content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAudioBooks'
fixture.write_text('<?xml version="1.0" encoding="utf-8"?><map><string name="upgrade_marker">'+marker+'</string><int name="player_last_position" value="54321"/><string name="library_tree_uri">'+X.escape(tree)+'</string><string name="shelves_v1">'+X.escape('[{"n":"Проверка обновления","b":[]}]')+'</string></map>')
run('adb','shell','run-as',package,'mkdir','-p','shared_prefs','files','cache')
def write_app_file(relative,source):
 with source.open('rb') as data:
  subprocess.run(['adb','exec-in','run-as',package,'dd','of='+relative,'bs=65536'],stdin=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=60)
 expected=hashlib.sha256(source.read_bytes()).hexdigest();actual=run('adb','shell','run-as',package,'sha256sum',relative).split()[0];assert actual==expected,'Transfer changed '+relative
write_app_file('shared_prefs/audiobook_downloader.xml',fixture)
markerfile=out/'upgrade-marker.txt';markerfile.write_text(marker);write_app_file('files/upgrade-marker.txt',markerfile)
write_app_file('cache/upgrade-candidate.apk',new)
run('adb','shell','pm','grant',package,'android.permission.READ_MEDIA_AUDIO');run('adb','shell','pm','grant',package,'android.permission.POST_NOTIFICATIONS')
def instrument(method,argument,value):
 result=run('adb','shell','am','instrument','-w','-r','-e','class','com.audiobookdownloader.UpgradeCandidateTest#'+method,'-e',argument,value,package+'.test/androidx.test.runner.AndroidJUnitRunner')
 (out/(method+'.txt')).write_text(result);assert re.search(r'OK \(1 test\)',result),result
 return result
candidate_result=instrument('oldUpdaterAcceptsNewSignedApk','upgrade_apk','/data/user/0/'+package+'/cache/upgrade-candidate.apk')
assert 'OLD_UPDATER_INSTALLER_CONFIRMATION_VERIFIED' in candidate_result,'Old installed updater did not show Android installation confirmation'
assert 'Success' in run('adb','install','-r',str(new))
instrument('installedUpgradeKeepsDataAndNewName','upgrade_marker',marker)
previous=json.loads(Path('upgrade-baseline/preview-update.json').read_text());result={'baselineCode':previous['versionCode'],'newCode':100000+int(os.environ['GITHUB_RUN_NUMBER']),'oldUpdaterAcceptedNewApk':True,'oldUpdaterOpenedSystemConfirmation':True,'systemConfirmationCancelledForHostReplacement':True,'androidPackageReplacementSucceeded':True,'label':'Audiobook','settingsCollectionFolderAndPrivateFilePreserved':True,'certificateUnchanged':True,'installationMethod':'adb install -r in disposable emulator; user will confirm installer on phone','actualInAppNetworkDownloadTested':False,'physicalPhone':False}
(out/'installed-upgrade.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
