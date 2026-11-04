"""Publish only the exact 4.2.93 artifact explicitly approved by the owner. Never builds/signs/deletes."""
import hashlib,json,os,pathlib,re,subprocess,sys
REPO='smoc1983-web/AudioBook';TAG='preview-v100093';RELEASE=433502781
SHA='249a1cc5173e7c454d08fe5647daaf93d2d784eeb6be46e95e717ebc712abf26'
CERT='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
SOURCE='6b418365402c28567ea69fa7ccbbd0dd06612f00'
def gh(*args):return subprocess.check_output(['gh',*args],text=True).strip()
def api(path):return json.loads(gh('api',path))
root=pathlib.Path(sys.argv[1]);metadata=next(root.rglob('preview-update.json'));apk=metadata.with_name('Audiobook-Preview.apk')
assert apk.stat().st_size==33085849 and hashlib.sha256(apk.read_bytes()).hexdigest()==SHA,'Approved APK changed'
meta=json.loads(metadata.read_text())
assert meta['versionCode']==100093 and meta['versionName']=='4.2.93' and meta['packageName']=='com.audiobookdownloader.preview'
assert meta['sha256']==SHA and meta['certificateSha256']==CERT and meta['size']==33085849 and meta['apk']==apk.name and 'parts' not in meta
sdk=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools'
signers=list(sdk.glob('*/apksigner'));assert signers,'Android signature verifier unavailable'
verified=subprocess.check_output([str(sorted(signers)[-1]),'verify','--verbose','--print-certs',str(apk)],text=True)
assert CERT in verified and 'Number of signers: 1' in verified,'Original signer mismatch'
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verified
release=api(f'repos/{REPO}/releases/{RELEASE}')
assert release['tag_name']==TAG and release['target_commitish']==SOURCE and release['prerelease'],'Unexpected draft'
files={apk.name:apk,metadata.name:metadata}
for name,file in files.items():
 existing=[a for a in release['assets'] if a['name']==name]
 assert len(existing)<=1,'Duplicate release asset'
 if not existing:
  assert release['draft'],'Never add missing assets to a public release'
  gh('release','upload',TAG,str(file),'--repo',REPO)
release=api(f'repos/{REPO}/releases/{RELEASE}')
assert {a['name'] for a in release['assets']}==set(files),'Unexpected release files'
for asset in release['assets']:
 file=files[asset['name']]
 assert asset['state']=='uploaded' and asset['size']==file.stat().st_size
 assert asset['digest']=='sha256:'+hashlib.sha256(file.read_bytes()).hexdigest(),'GitHub API digest mismatch'
if release['draft']:gh('release','edit',TAG,'--repo',REPO,'--draft=false','--prerelease','--latest=false')
release=api(f'repos/{REPO}/releases/{RELEASE}')
assert not release['draft'] and release['tag_name']==TAG
ref=api(f'repos/{REPO}/git/ref/tags/{TAG}');assert ref['object']['sha']==SOURCE,'Published tag target changed'
print(json.dumps({'id':release['id'],'tag':TAG,'url':release['html_url'],'published':True,'sha256':SHA,'assets':[{'name':a['name'],'id':a['id'],'size':a['size'],'digest':a['digest']} for a in release['assets']]},indent=2))
