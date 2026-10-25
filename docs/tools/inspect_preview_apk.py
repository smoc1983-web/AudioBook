"""Inspect a CI artifact without Android SDK: identity, providers, size/hash and v1 certificate.
Usage: python docs/tools/inspect_preview_apk.py artifact.zip output-directory
CI additionally verifies v2/v3 signatures with apksigner; this script does not replace it.
"""
import hashlib,json,struct,subprocess,sys,tempfile,zipfile
from pathlib import Path

def axml(data):
    strings=[];elements=[];position=8
    def u16(p):return struct.unpack_from('<H',data,p)[0]
    def u32(p):return struct.unpack_from('<I',data,p)[0]
    def length8(p):
        n=data[p];return (((n&127)<<8)|data[p+1],p+2) if n&128 else (n,p+1)
    def length16(p):
        n=u16(p);return (((n&32767)<<16)|u16(p+2),p+4) if n&32768 else (n,p+2)
    while position<len(data):
        kind,header,size=struct.unpack_from('<HHI',data,position)
        if size<8:raise ValueError('Invalid XML chunk')
        if kind==1:
            count=u32(position+8);flags=u32(position+16);start=u32(position+20)
            for i in range(count):
                p=position+start+u32(position+header+i*4)
                if flags&256:
                    _,p=length8(p);n,p=length8(p);strings.append(data[p:p+n].decode('utf-8'))
                else:
                    n,p=length16(p);strings.append(data[p:p+2*n].decode('utf-16le'))
        elif kind==0x102:
            name=strings[u32(position+20)];attrs={};base=position+16+u16(position+24)
            for i in range(u16(position+28)):
                p=base+i*u16(position+26);raw=u32(p+8);dtype=data[p+15];value=u32(p+16)
                attrs[strings[u32(p+4)]]=strings[raw] if raw!=0xffffffff else strings[value] if dtype==3 else value
            elements.append((name,attrs))
        position+=size
    return elements

def resource_string(package,value):
    """Resolve a scalar string reference used by the channel's application label."""
    if isinstance(value,str):return value
    data=package.read('resources.arsc')
    def u32(p):return struct.unpack_from('<I',data,p)[0]
    def pool(offset):
        _,header,_=struct.unpack_from('<HHI',data,offset)
        count=u32(offset+8);utf8=bool(u32(offset+16)&256);start=u32(offset+20);strings=[]
        for i in range(count):
            pos=offset+start+u32(offset+header+i*4)
            def length(p):
                if utf8:
                    n=data[p];return (((n&127)<<8)|data[p+1],p+2) if n&128 else (n,p+1)
                n=struct.unpack_from('<H',data,p)[0]
                return (((n&32767)<<16)|struct.unpack_from('<H',data,p+2)[0],p+4) if n&32768 else (n,p+2)
            n,pos=length(pos)
            if utf8:n,pos=length(pos)
            strings.append(data[pos:pos+n*(1 if utf8 else 2)].decode('utf-8' if utf8 else 'utf-16le'))
        return strings
    strings=[];position=struct.unpack_from('<H',data,2)[0]
    while position<len(data):
        kind,header,size=struct.unpack_from('<HHI',data,position)
        if kind==1:strings=pool(position)
        elif kind==0x200 and u32(position+8)==value>>24:
            child=position+header
            type_offset=u32(position+284) if header>=288 else 0
            while child<position+size:
                tag,entry_header,entry_size=struct.unpack_from('<HHI',data,child)
                if tag==0x201 and data[child+8]+type_offset==(value>>16)&255:
                    assert data[child+9]==0,'Unsupported sparse resource table'
                    index=value&65535
                    if index<u32(child+12):
                        offset=u32(child+entry_header+index*4)
                        if offset!=0xffffffff:
                            entry=child+u32(child+16)+offset
                            length,flags=struct.unpack_from('<HH',data,entry)
                            assert not flags&1,'Label is not a scalar resource'
                            typed=entry+length
                            if data[typed+3]==3:return strings[u32(typed+4)]
                child+=entry_size
        position+=size
    raise ValueError('Application label resource not found')

archive=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(archive) as artifact:
    manifest=json.loads(artifact.read('preview-update.json'))
    assert manifest['apk']=='Audiobook-Preview.apk'
    apk=artifact.read(manifest['apk']);assert len(apk)==manifest['size']
    assert hashlib.sha256(apk).hexdigest()==manifest['sha256']
    output=out/f"Audiobook-Preview-{manifest['versionName']}.apk";output.write_bytes(apk)
with zipfile.ZipFile(output) as package:
    elements=axml(package.read('AndroidManifest.xml'))
    identity=next(a for n,a in elements if n=='manifest')
    application=next(a for n,a in elements if n=='application')
    providers=[a['authorities'] for n,a in elements if n=='provider']
    assert identity['package']==manifest['packageName']=='com.audiobookdownloader.preview'
    assert identity['versionCode']==manifest['versionCode']
    assert identity['versionName']==manifest['versionName']
    application['label']=resource_string(package,application['label'])
    assert application['label']==('Audiobook' if manifest['versionCode']>100048 else 'Audiobook Preview') and application['allowBackup']==0
    assert all(a.startswith(identity['package']+'.') for a in providers)
    assert identity['package']+'.updates' in providers
    certificate=next(n for n in package.namelist() if n.startswith('META-INF/') and n.endswith(('.RSA','.DSA','.EC')))
    with tempfile.TemporaryDirectory() as tmp:
        block=Path(tmp)/'cert.der';pem=Path(tmp)/'cert.pem';block.write_bytes(package.read(certificate))
        subprocess.run(['openssl','pkcs7','-inform','DER','-in',str(block),'-print_certs','-out',str(pem)],check=True,capture_output=True)
        der=subprocess.check_output(['openssl','x509','-in',str(pem),'-outform','DER'])
        assert hashlib.sha256(der).hexdigest()==manifest['certificateSha256']
result={**manifest,'label':application['label'],'providers':providers,'file':str(output)}
(out/(manifest['versionName']+'-inspection.json')).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
