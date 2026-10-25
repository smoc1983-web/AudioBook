"""Publish only an isolated, signed APK. No credentials enter the manifest or APK."""
import hashlib,json,os,re,shutil,subprocess
from pathlib import Path
apk=Path('app/build/outputs/apk/debug/app-debug.apk')
build_tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
identity=subprocess.check_output([str(build_tools/'aapt'),'dump','badging',str(apk)],text=True)
m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",identity)
assert m and m[1]=='com.audiobookdownloader.preview', 'Refusing to publish the stable package'
version=int(m[2]);assert version==100000+int(os.environ['GITHUB_RUN_NUMBER'])
manifest_tree=subprocess.check_output([str(build_tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert "application-label:'Audiobook'" in identity, 'Application must be named Audiobook'
assert 'com.audiobookdownloader.preview.updates' in manifest_tree
assert 'com.audiobookdownloader.preview.diagnostic_reports' in manifest_tree
certificate=subprocess.check_output([str(build_tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
fingerprint=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',certificate)[1]
# Compare with the latest published identity before exposing a newly signed APK.
old=json.loads(subprocess.check_output(['gh','api',f"repos/{os.environ['GITHUB_REPOSITORY']}/releases?per_page=30"],text=True))
for release in old:
    if not release['draft'] and release['tag_name'].startswith('preview-v'):
        asset=next((a for a in release['assets'] if a['name']=='preview-update.json'),None)
        if asset:
            previous=json.loads(subprocess.check_output(['gh','api','-H','Accept: application/octet-stream',f"repos/{os.environ['GITHUB_REPOSITORY']}/releases/assets/{asset['id']}?download=1"],text=True))
            assert previous['certificateSha256']==fingerprint, 'Preview signing identity changed; refusing publication'
            assert version>=previous['versionCode'], 'Version code regressed; refusing publication'
        break
out=Path('dist');out.mkdir(exist_ok=True)
shutil.copyfile(apk,out/'Audiobook-Preview.apk')
manifest={'packageName':m[1],'versionCode':version,'versionName':m[3],'size':apk.stat().st_size,
    'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificateSha256':fingerprint,
    'apk':'Audiobook-Preview.apk','commit':os.environ['GITHUB_SHA']}
(out/'preview-update.json').write_text(json.dumps(manifest,indent=2)+'\n')
(out/'tag.txt').write_text(f'preview-v{version}\n')
print(json.dumps(manifest,indent=2))
