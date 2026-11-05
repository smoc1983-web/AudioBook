"""Owner-approved 4.2.94 publication; never replaces releases/assets or creates keys."""
import hashlib,json,os,pathlib,re,subprocess
REPO='smoc1983-web/AudioBook'
TAG='preview-v100094'
CERT='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
def gh(*args):return subprocess.check_output(['gh',*args],text=True).strip()
def api(path):return json.loads(gh('api',path))
root=pathlib.Path('dist');metadata=root/'preview-update.json';apk=root/'Audiobook-Preview.apk'
meta=json.loads(metadata.read_text());source=os.environ['GITHUB_SHA']
assert meta['versionCode']==100094 and meta['versionName']=='4.2.94'
assert meta['packageName']=='com.audiobookdownloader.preview' and meta['commit']==source
assert meta['certificateSha256']==CERT and meta['apk']==apk.name and 'parts' not in meta
assert meta['size']==apk.stat().st_size and meta['sha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
verified=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
assert CERT in verified and 'Number of signers: 1' in verified
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verified
releases=api(f'repos/{REPO}/releases?per_page=100')
assert not any(r['tag_name']==TAG for r in releases),'Never replace an existing release'
assert all(int(r['tag_name'].split('preview-v')[1])<100094 for r in releases if re.fullmatch(r'preview-v[1-9][0-9]*',r['tag_name']))
notes=root/'release-notes.md'
notes.write_text('Audiobook 4.2.94\n\n'+'\n'.join('• '+line for line in meta['changes'])+'\n\nОбновление поверх прежней Preview: пакет и подпись сохранены. Удалять приложение не нужно.\n')
gh('release','create',TAG,'--repo',REPO,'--target',source,'--draft','--prerelease','--title','Audiobook 4.2.94 — поиск, загрузки и продолжения','--notes-file',str(notes))
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
(root/'publication.json').write_text(json.dumps({'url':release['html_url'],'tag':TAG,'source':source,'sha256':meta['sha256'],'certificateSha256':CERT,'size':meta['size']},indent=2)+'\n')
print('Published verified 4.2.94:',release['html_url'])
