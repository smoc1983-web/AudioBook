"""Owner-approved 4.2.97 publication; never replaces releases/assets or creates keys."""
import hashlib,json,os,pathlib,re,subprocess,zipfile
REPO='smoc1983-web/AudioBook'
TAG='preview-v100097'
CERT='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
def gh(*args):return subprocess.check_output(['gh',*args],text=True).strip()
def api(path):return json.loads(gh('api',path))
SOURCE='1ada617b45068a0fc44a8e6b227d620131c8d87a'
APK_SHA='2f567feb6333306e593ea02f528b084d24ad6c2d11b7dbfdce9a6b6eed5a379e'
APK_BYTES=33439545
RUN_ID=44320951915
ARTIFACT_ID=14435422839
run=api(f'repos/{REPO}/actions/runs/{RUN_ID}')
assert run['head_sha']==SOURCE and run['conclusion']=='success'
artifact=api(f'repos/{REPO}/actions/artifacts/{ARTIFACT_ID}')
assert artifact['workflow_run']['id']==RUN_ID and artifact['workflow_run']['head_sha']==SOURCE
assert artifact['name']=='Audiobook-Preview-4.2.97-phone-test' and not artifact['expired']
root=pathlib.Path('dist');metadata=root/'preview-update.json';apk=root/'Audiobook-Preview.apk'
source=SOURCE
assert apk.stat().st_size==APK_BYTES and hashlib.sha256(apk.read_bytes()).hexdigest()==APK_SHA
with zipfile.ZipFile(apk) as archive:
    changes=json.loads(archive.read('assets/release-notes.json'))['changes']
assert isinstance(changes,list) and 0<len(changes)<=12
assert all(isinstance(line,str) and 0<len(line)<=300 for line in changes)
meta={'packageName':'com.audiobookdownloader.preview','versionCode':100097,'versionName':'4.2.97',
      'size':APK_BYTES,'sha256':APK_SHA,'certificateSha256':CERT,'apk':apk.name,
      'commit':source,'changes':changes}
metadata.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert meta['versionCode']==100097 and meta['versionName']=='4.2.97'
assert meta['packageName']=='com.audiobookdownloader.preview' and meta['commit']==source
assert meta['certificateSha256']==CERT and meta['apk']==apk.name and 'parts' not in meta
assert meta['size']==apk.stat().st_size and meta['sha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
assert "package: name='com.audiobookdownloader.preview' versionCode='100097' versionName='4.2.97'" in badging
assert "application-debuggable" not in badging
verified=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
assert CERT in verified and 'Number of signers: 1' in verified
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verified
releases=api(f'repos/{REPO}/releases?per_page=100')
assert not any(r['tag_name']==TAG for r in releases),'Never replace an existing release'
tags=subprocess.check_output(['git','ls-remote','--tags','origin',f'refs/tags/{TAG}'],text=True)
assert not tags.strip(),'Never replace an existing tag'
assert all(int(r['tag_name'].split('preview-v')[1])<100097 for r in releases if re.fullmatch(r'preview-v[1-9][0-9]*',r['tag_name']))
notes=root/'release-notes.md'
notes.write_text('Audiobook 4.2.97\n\n'+'\n'.join('• '+line for line in meta['changes'])+'\n\nОбновление поверх прежней Preview: пакет и подпись сохранены. Удалять приложение не нужно.\n')
gh('release','create',TAG,'--repo',REPO,'--target',source,'--draft','--prerelease','--title','Audiobook 4.2.97 — Переплёт и улучшения библиотеки','--notes-file',str(notes))
for file in (apk,metadata):gh('release','upload',TAG,str(file),'--repo',REPO)
release=next(r for r in api(f'repos/{REPO}/releases?per_page=100') if r['tag_name']==TAG)
files={file.name:file for file in (apk,metadata)}
assert release['draft'] and release['target_commitish']==source
assert {a['name'] for a in release['assets']}==set(files)
for asset in release['assets']:
    file=files[asset['name']]
    assert asset['state']=='uploaded' and asset['size']==file.stat().st_size
    assert asset['digest']=='sha256:'+hashlib.sha256(file.read_bytes()).hexdigest()
gh('release','edit',TAG,'--repo',REPO,'--draft=false','--prerelease','--latest=false')
release=api(f'repos/{REPO}/releases/tags/{TAG}')
assert not release['draft'] and api(f'repos/{REPO}/git/ref/tags/{TAG}')['object']['sha']==source
(root/'publication.json').write_text(json.dumps({'url':release['html_url'],'tag':TAG,'source':source,'sha256':meta['sha256'],'certificateSha256':CERT,'size':meta['size'],'releaseId':release['id'],'versionCode':100097,'apkBuildRun':RUN_ID,'apkArtifactId':ARTIFACT_ID},indent=2)+'\n')
print('Published verified 4.2.97:',release['html_url'])
