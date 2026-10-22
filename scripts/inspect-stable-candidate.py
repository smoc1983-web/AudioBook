"""Check a stable candidate without pretending it has the user's signing identity."""
import os,re,json,hashlib,subprocess
from pathlib import Path
apk=Path('dist-stable/Audiobook-stable-UNSIGNED.apk')
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
identity=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
match=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",identity)
assert match and match[1]=='com.audiobookdownloader' and int(match[2])>1
assert 'Audiobook Preview' not in identity
manifest=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert 'com.audiobookdownloader.updates' in manifest and 'com.audiobookdownloader.diagnostic_reports' in manifest
assert subprocess.run([str(tools/'apksigner'),'verify',str(apk)],capture_output=True).returncode!=0, 'Candidate must await the original signer'
result={'packageName':match[1],'versionCode':int(match[2]),'versionName':match[3],
        'libraryDirectory':'Download/AudioBooks','size':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'signed':False,'installable':False,'originalSignerRequired':True,'originalPhoneIDE':'Code on the Go',
        'realUserUpgradeTested':False,'commit':os.environ['GITHUB_SHA']}
(apk.parent/'stable-candidate.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False))
