"""Real published-baseline->next replacement and native old-updater validation in CI's emulator only."""
import subprocess,json,os,re,uuid,hashlib,wave
from pathlib import Path
assert os.environ.get('CI')=='true','Never run on a user phone'
def run(*args):return subprocess.check_output(list(args),text=True,stderr=subprocess.STDOUT,timeout=60)
serial=run('adb','get-serialno').strip();assert serial.startswith('emulator-'),'Disposable emulator required'
package='com.audiobookdownloader.preview';tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0';old=Path('upgrade-baseline/Audiobook-Preview.apk');new=Path('app/build/outputs/apk/release/app-release.apk');tests=Path('upgradeProbe/build/outputs/apk/debug/upgradeProbe-debug.apk');out=Path('startup-evidence');out.mkdir(exist_ok=True)
def cert(apk):return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',run(str(tools/'apksigner'),'verify','--print-certs',str(apk)))[1]
assert cert(old)==cert(new)=='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
assert cert(tests)==cert(new),'Instrumentation must keep the same signing identity'
probe_identity=run(str(tools/'aapt'),'dump','badging',str(tests))
probe_package=re.search(r"package: name='([^']+)'",probe_identity)[1]
assert probe_package=='com.audiobookdownloader.preview.upgradeprobe'
# The probe must execute code from the actually installed target, not copies of app classes.
import importlib.util,zipfile,struct
spec=importlib.util.spec_from_file_location('apk_audit','scripts/audit-installable-apk.py')
apk_audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(apk_audit)
with zipfile.ZipFile(tests) as archive:
 for name in archive.namelist():
  if not re.fullmatch(r'classes\d*\.dex',name):continue
  data=archive.read(name)
  types=apk_audit.defined_types(data)
  forbidden={'Lcom/audiobookdownloader/PreviewUpdater;','Lcom/audiobookdownloader/PreviewUpdatesActivity;','Lcom/audiobookdownloader/MainActivity;','Lcom/audiobookdownloader/MainActivityKt;','Lcom/audiobookdownloader/BuildConfig;'}
  assert not forbidden.intersection(types),'Probe bundles app implementation instead of invoking installed APK'
  u32=lambda offset:struct.unpack_from('<I',data,offset)[0]
  def dex_string(offset):
   while data[offset]&128:offset+=1
   offset+=1;return data[offset:data.index(0,offset)].decode('utf-8',errors='replace')
  strings=[dex_string(u32(u32(60)+4*i)) for i in range(u32(56))]
  referenced=[strings[u32(u32(68)+4*i)] for i in range(u32(64))]
  assert not any(value.startswith('Lj$/') for value in referenced),'Old-target probe must not assume a new Java runtime bridge'
# Only throwaway tests from this job are removed; no physical device or stable package is touched.
subprocess.run(['adb','uninstall',package],capture_output=True);subprocess.run(['adb','uninstall',package+'.test'],capture_output=True);subprocess.run(['adb','uninstall',probe_package],capture_output=True)
assert 'Success' in run('adb','install',str(old));assert 'Success' in run('adb','install','-t',str(tests))
marker='upgrade-'+uuid.uuid4().hex
markerfile=out/'upgrade-marker.txt';markerfile.write_text(marker)
audio=out/'upgrade-audio.wav'
with wave.open(str(audio),'wb') as stream:
 stream.setnchannels(1);stream.setsampwidth(2);stream.setframerate(8000);stream.writeframes(bytes(1600))
audio_sha=hashlib.sha256(audio.read_bytes()).hexdigest()
# The previous public APK is now a non-debuggable release. Seed its private fixture
# through same-signed instrumentation; run-as intentionally cannot open a release.
fixture_dir='/data/local/tmp/audiobook-upgrade-fixture-'+marker
run('adb','shell','mkdir','-p',fixture_dir)
for source,name in [(markerfile,'marker.txt'),(audio,'audio.wav'),(new,'candidate.apk')]:
 run('adb','push',str(source),fixture_dir+'/'+name)
run('adb','shell','pm','grant',package,'android.permission.READ_MEDIA_AUDIO');run('adb','shell','pm','grant',package,'android.permission.POST_NOTIFICATIONS')
def instrument(method,argument,value,extra=()):
 result=run('adb','shell','am','instrument','-w','-r','-e','class','com.audiobookdownloader.UpgradeCandidateTest#'+method,'-e',argument,value,'-e','upgrade_audio_sha',audio_sha,*extra,probe_package+'/androidx.test.runner.AndroidJUnitRunner')
 (out/(method+'.txt')).write_text(result);assert re.search(r'OK \(1 test\)',result),result
 return result
instrument('seedInstalledUpgradeData','upgrade_fixture_dir',fixture_dir,
 ('-e','upgrade_marker',marker,'-e','upgrade_candidate_sha',hashlib.sha256(new.read_bytes()).hexdigest()))
candidate_result=instrument('oldUpdaterAcceptsNewSignedApk','upgrade_apk','/data/user/0/'+package+'/cache/upgrade-candidate.apk')
assert 'OLD_UPDATER_INSTALLER_CONFIRMATION_VERIFIED' in candidate_result,'Old installed updater did not show Android installation confirmation'
assert 'Success' in run('adb','install','-r',str(new))
instrument('installedUpgradeKeepsDataAndNewName','upgrade_marker',marker)
previous=json.loads(Path('upgrade-baseline/preview-update.json').read_text());result={'baselineCode':previous['versionCode'],'newCode':100000+int(os.environ['GITHUB_RUN_NUMBER']),'oldUpdaterAcceptedNewApk':True,'oldUpdaterOpenedSystemConfirmation':True,'systemConfirmationCancelledForHostReplacement':True,'releaseBaselineSeededBySameSignedInstrumentation':True,'standalonePlatformProbe':True,'probeHasNoAppImplementationCopies':True,'probeNeedsNoNewRuntimeBridge':True,'androidPackageReplacementSucceeded':True,'label':'Audiobook','settingsCollectionFolderAndPrivateFilePreserved':True,'certificateUnchanged':True,'installationMethod':'adb install -r in disposable emulator; user will confirm installer on phone','actualInAppNetworkDownloadTested':False,'physicalPhone':False,'newBuildType':'release','debuggable':False,'privateAudioFileChecksumPreserved':True,'googlePlayProtectVerdictTested':False}
(out/'installed-upgrade.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
