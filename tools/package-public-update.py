"""Prepare a public read-only update feed from a verified, already signed APK.
Private signing keys and GitHub access credentials are never read or packaged.
"""
import argparse,hashlib,json,os,re,subprocess
from pathlib import Path
CERTIFICATES={'stable':'020e6c23aad35b45e6ef2ee3ef3326a6ddac731b8491162f0d340848887c9bc7','preview':'3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'}
def package(apk,channel,output,build_tools):
    identity=subprocess.check_output([str(build_tools/'aapt'),'dump','badging',str(apk)],text=True)
    m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",identity)
    if 'application-debuggable' in identity:raise ValueError('Only non-debuggable release APKs may be published')
    expected='com.audiobookdownloader'+('.preview' if channel=='preview' else '')
    if not m or m[1]!=expected:raise ValueError('Wrong APK applicationId')
    # An unsigned stable candidate must never enter the direct installation feed.
    verified=subprocess.run([str(build_tools/'apksigner'),'verify','--print-certs',str(apk)],capture_output=True,text=True,check=True)
    cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',verified.stdout)
    if not cert or cert[1]!=CERTIFICATES[channel]:raise ValueError('Original signing certificate does not match')
    data=apk.read_bytes();sha=hashlib.sha256(data).hexdigest();code=int(m[2])
    if not 0<len(data)<=200*1024*1024:raise ValueError('APK size outside allowed range')
    name='Audiobook-Preview.apk' if channel=='preview' else 'Audiobook.apk'
    metadata_path=output/(channel+'-update.json')
    if metadata_path.exists():
        previous=json.loads(metadata_path.read_text())
        if previous.get('available',True) and (previous.get('versionCode',0)>code or previous.get('versionCode')==code and previous.get('sha256')!=sha):raise ValueError('Version must increase; do not replace a published version')
    parts=[];(output/'parts').mkdir(parents=True,exist_ok=True)
    for index,start in enumerate(range(0,len(data),8*1024*1024)):
        chunk=data[start:start+8*1024*1024];filename=f'{channel}-{code}-{sha[:16]}-{index:02d}.bin'
        (output/'parts'/filename).write_bytes(chunk)
        parts.append({'url':'parts/'+filename,'size':len(chunk),'sha256':hashlib.sha256(chunk).hexdigest()})
    result={'packageName':m[1],'versionCode':code,'versionName':m[3],'size':len(data),'sha256':sha,'certificateSha256':cert[1],'apk':name,'parts':parts}
    temp=metadata_path.with_suffix('.tmp');temp.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');temp.replace(metadata_path)
    return result
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--channel',choices=CERTIFICATES,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--build-tools',type=Path,required=True);a=p.parse_args()
    print(json.dumps(package(a.apk,a.channel,a.output,a.build_tools),ensure_ascii=False))
