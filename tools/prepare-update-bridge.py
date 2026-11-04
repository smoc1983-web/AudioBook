"""Prepare, never publish: one original-signer Preview APK, legacy Site parts and GitHub assets."""
import argparse
import hashlib
import importlib.util
import json
import re
import shutil
from pathlib import Path

spec=importlib.util.spec_from_file_location('public_update_packager',Path(__file__).with_name('package-public-update.py'))
packager=importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)

FIELDS=('packageName','versionCode','versionName','size','sha256','certificateSha256','apk','changes')

def verify_pair(legacy,github,updates,apk,minimum_installed):
    if any(legacy.get(key)!=github.get(key) for key in FIELDS):
        raise ValueError('Legacy and GitHub must describe the exact same edition')
    code=github.get('versionCode')
    if type(code) is not int or code<=minimum_installed or code<=100000:
        raise ValueError('Bridge must be strictly newer than the installed version')
    if github.get('versionName')!=f'4.2.{code-100000}':
        raise ValueError('Preserve the existing version naming scheme')
    if github.get('packageName')!='com.audiobookdownloader.preview' or github.get('apk')!='Audiobook-Preview.apk':
        raise ValueError('Bridge may not change package or channel')
    if github.get('certificateSha256')!=packager.CERTIFICATES['preview']:
        raise ValueError('Original Preview certificate is required')
    size=github.get('size')
    if type(size) is not int or not 0<size<=200*1024*1024 or 'parts' in github:
        raise ValueError('GitHub requires one bounded complete APK')
    if apk.stat().st_size!=size or hashlib.sha256(apk.read_bytes()).hexdigest()!=github.get('sha256'):
        raise ValueError('GitHub APK bytes differ from metadata')
    parts=legacy.get('parts',[])
    if not 1<=len(parts)<=32:
        raise ValueError('Invalid legacy parts')
    digest=hashlib.sha256();total=0;seen=set()
    for part in parts:
        name=part.get('url','')
        if not re.fullmatch(r'parts/[a-zA-Z0-9._-]+\.bin',name) or name in seen:
            raise ValueError('Unsafe or duplicate legacy part')
        seen.add(name)
        if type(part.get('size')) is not int or not 0<part['size']<=8*1024*1024:
            raise ValueError('Invalid legacy part size')
        file=updates/name
        if file.is_symlink() or file.stat().st_size!=part['size']:
            raise ValueError('Legacy part size differs')
        data=file.read_bytes()
        if hashlib.sha256(data).hexdigest()!=part.get('sha256'):
            raise ValueError('Legacy part hash differs')
        total+=len(data);digest.update(data)
    if total!=size or digest.hexdigest()!=github['sha256']:
        raise ValueError('Legacy reconstruction differs from the GitHub APK')
    return {'versionCode':code,'versionName':github['versionName'],'packageName':github['packageName'],
            'sha256':github['sha256'],'certificateSha256':github['certificateSha256'],
            'size':size,'legacyParts':len(parts),'minimumInstalledCode':minimum_installed,
            'sameApkInBothChannels':True,'published':False,
            'installed4292ApkInspected':False,'phoneDnsVerified':False}

def prepare(apk,output,build_tools,minimum_installed):
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use a new empty staging directory; never replace a publication')
    updates=output/'legacy-site/updates'
    updates.mkdir(parents=True,exist_ok=True)
    # This verifies the real APK with aapt/apksigner, original certificate and non-debuggable flag.
    legacy=packager.package(apk,'preview',updates,build_tools)
    github={key:legacy[key] for key in FIELDS}
    release=output/'github';release.mkdir()
    shipped=release/github['apk'];shutil.copyfile(apk,shipped)
    (release/'preview-update.json').write_text(json.dumps(github,ensure_ascii=False,indent=2)+'\n')
    result=verify_pair(legacy,github,updates,shipped,minimum_installed)
    (output/'bridge-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--build-tools',type=Path,required=True)
    parser.add_argument('--minimum-installed-code',type=int,required=True)
    args=parser.parse_args()
    print(json.dumps(prepare(args.apk,args.output,args.build_tools,args.minimum_installed_code),indent=2))
