"""Fetch the last signed public-channel APK privately in CI; no signing keys are downloaded."""
import hashlib,json,os,subprocess
from pathlib import Path
repo=os.environ['GITHUB_REPOSITORY'];code=100000+int(os.environ['GITHUB_RUN_NUMBER']);out=Path('upgrade-baseline');out.mkdir(exist_ok=True)
releases=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/releases?per_page=100'],text=True))
candidates=[]
for r in releases:
 tag=r['tag_name'];assets={a['name'] for a in r['assets']}
 if not r['draft'] and tag.startswith('preview-v') and tag[9:].isdigit() and int(tag[9:])<code and {'Audiobook-Preview.apk','preview-update.json'}<=assets:candidates.append(r)
assert candidates,'No previous installed channel available for update test'
old=max(candidates,key=lambda r:int(r['tag_name'][9:]));subprocess.run(['gh','release','download',old['tag_name'],'--repo',repo,'--dir',str(out),'--pattern','Audiobook-Preview.apk','--pattern','preview-update.json','--clobber'],check=True)
m=json.loads((out/'preview-update.json').read_text());apk=out/'Audiobook-Preview.apk';assert m['packageName']=='com.audiobookdownloader.preview' and m['versionCode']<code
assert apk.stat().st_size==m['size'] and hashlib.sha256(apk.read_bytes()).hexdigest()==m['sha256']
assert m['certificateSha256']=='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
print(json.dumps({'baseline':m['versionName'],'code':m['versionCode'],'sha256Verified':True}))
