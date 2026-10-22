"""Real apksigner/PKCS12 regressions. Never uses or exports the user's private key.
Usage: python scripts/tests/test-phone-signing.py SDK_ROOT UNSIGNED_STABLE_APK
All keys/passwords are randomly generated inside an automatically removed temp directory.
"""
import fcntl,hashlib,json,os,pathlib,pty,re,secrets,select,shutil,struct,subprocess,sys,tempfile,termios,time,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SDK=pathlib.Path(sys.argv[1]).resolve()
APK=pathlib.Path(sys.argv[2]).resolve()
TOOLS=sorted((SDK/'build-tools').iterdir())[-1]
SIGNER=TOOLS/'apksigner'
CERT='020e6c23aad35b45e6ef2ee3ef3326a6ddac731b8491162f0d340848887c9bc7'
cases=[]
def run(args,env=None):
    result=subprocess.run([str(a) for a in args],env=env,capture_output=True,text=True,timeout=90)
    if result.returncode:raise RuntimeError('Test tool failed: '+str(args[0])+'\n'+result.stderr)
    return result.stdout

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def cert(apk):return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',run([SIGNER,'verify','--print-certs',apk]))[1]
def make_apk(path,version=100038,preview=False):
    with zipfile.ZipFile(APK) as source,zipfile.ZipFile(path,'w') as target:
        manifest=source.read('AndroidManifest.xml')
        assert manifest.count(struct.pack('<I',100038))==1
        manifest=manifest.replace(struct.pack('<I',100038),struct.pack('<I',version))
        if preview:
            original='com.audiobookdownloader'.encode('utf-16le');wrong='com.audiobookdownloadeq'.encode('utf-16le')
            assert original in manifest
            manifest=manifest.replace(original,wrong)
        target.writestr('AndroidManifest.xml',manifest)
        target.writestr('resources.arsc',source.read('resources.arsc'))

def sign(src,dst,key,env):
    run([SIGNER,'sign','--ks',key,'--ks-type','PKCS12','--ks-key-alias','androiddebugkey',
         '--ks-pass','env:TEST_STORE_SECRET','--key-pass','env:TEST_KEY_SECRET','--out',dst,src],env)

with tempfile.TemporaryDirectory(prefix='audiobook-pkcs12-tests-') as directory:
    temp=pathlib.Path(directory)
    store_password=' '+secrets.token_urlsafe(18)+' ! '
    other_password=secrets.token_urlsafe(24)
    env=dict(os.environ,TEST_STORE_SECRET=store_password,TEST_KEY_SECRET=store_password,TEST_OTHER_SECRET=other_password)
    key=temp/'original-test-only.p12'
    run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-alias','androiddebugkey',
         '-storepass:env','TEST_STORE_SECRET','-keypass:env','TEST_KEY_SECRET','-keyalg','RSA','-keysize','2048','-validity','2',
         '-dname','CN=Ephemeral APK Signing Test'],env)
    foreign=temp/'foreign-test-only.p12'
    run(['keytool','-genkeypair','-keystore',foreign,'-storetype','PKCS12','-alias','androiddebugkey',
         '-storepass:env','TEST_STORE_SECRET','-keypass:env','TEST_KEY_SECRET','-keyalg','RSA','-keysize','2048','-validity','2',
         '-dname','CN=Ephemeral Foreign Test'],env)
    before=sha(key)
    old_unsigned=temp/'old-unsigned.apk';make_apk(old_unsigned,1)
    old=temp/'old.apk';sign(old_unsigned,old,key,env)
    # Production certificate pin is replaced ONLY in this disposable fixture copy.
    script=temp/'sign-phone-update-fixed.sh'
    script.write_text((ROOT/'scripts/sign-phone-update.sh').read_text().replace(CERT,cert(old)))
    base_env=dict(os.environ,ANDROID_HOME=str(SDK),AUDIOBOOK_SIGNING_KEYSTORE=str(key),
                  AUDIOBOOK_STORE_PASSWORD=store_password,AUDIOBOOK_KEY_PASSWORD=store_password)
    def exercise(name,*,accepted,store=None,keypass=None,keyfile=None,version=100038,preview=False,oldfile=None,discover=False):
        folder=temp/name;folder.mkdir();candidate=folder/'new.apk';make_apk(candidate,version,preview)
        source_hash=sha(candidate)
        this_env=dict(base_env)
        if store is not None:this_env['AUDIOBOOK_STORE_PASSWORD']=store
        if keypass is not None:this_env['AUDIOBOOK_KEY_PASSWORD']=keypass
        if keyfile is not None:this_env['AUDIOBOOK_SIGNING_KEYSTORE']=str(keyfile)
        if discover:shutil.copyfile(old,folder/'extracted-stable.apk')
        command=['bash',str(script),str(candidate)]+([] if discover else [str(oldfile or old)])
        result=subprocess.run(command,env=this_env,capture_output=True,text=True,timeout=90)
        output=folder/'Audiobook-update.apk'
        assert (result.returncode==0)==accepted,(name,result.stdout,result.stderr)
        assert output.exists()==accepted,(name,'Unexpected output')
        assert sha(key)==before and sha(candidate)==source_hash,(name,'Original input was modified')
        assert not list(folder.glob('.audiobook-sign.*')),(name,'Temporary APKs were not cleaned')
        for password in [store_password,other_password,this_env['AUDIOBOOK_STORE_PASSWORD'],this_env['AUDIOBOOK_KEY_PASSWORD']]:
            assert password not in result.stdout+result.stderr,(name,'Secret leaked')
        if accepted:
            assert cert(output)==cert(old)
            badging=run([TOOLS/'aapt','dump','badging',output]);assert "name='com.audiobookdownloader'" in badging and "versionCode='100038'" in badging
            rerun=subprocess.run(['bash',str(script),str(candidate),str(old)],env=this_env,capture_output=True,text=True,timeout=30)
            assert rerun.returncode!=0 and cert(output)==cert(old),'Existing output changed'
        cases.append({'case':name,'passed':True})
    exercise('same-store-and-key-password',accepted=True)
    exercise('discover-extracted-old-apk-next-to-candidate',accepted=True,discover=True)
    exercise('different-certificate-refused',accepted=False,keyfile=foreign)
    exercise('wrong-store-password',accepted=False,store=secrets.token_urlsafe(20))
    exercise('wrong-key-password',accepted=False,keypass=secrets.token_urlsafe(20))
    exercise('unchanged-version-rejected',accepted=False,version=1)
    exercise('wrong-package-rejected',accepted=False,preview=True)
    helper=temp/'Rewrap.java'
    helper.write_text('''import java.security.*;import java.nio.file.*;
class Rewrap { public static void main(String[] a)throws Exception {
 char[] store=System.getenv("TEST_STORE_SECRET").toCharArray(),key=System.getenv("TEST_OTHER_SECRET").toCharArray();
 KeyStore source=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(a[0]))){source.load(in,store);}
 KeyStore target=KeyStore.getInstance("PKCS12");target.load(null,store);
 target.setKeyEntry("androiddebugkey",source.getKey("androiddebugkey",store),key,source.getCertificateChain("androiddebugkey"));
 try(var out=Files.newOutputStream(Path.of(a[1]))){target.store(out,store);}
}}''')
    separate=temp/'distinct-passwords-test-only.p12';run(['java',helper,key,separate],env)
    separate_hash=sha(separate)
    exercise('different-store-and-key-passwords',accepted=True,keyfile=separate,keypass=other_password)
    assert sha(separate)==separate_hash
    # Exercise the exact hidden terminal prompts, including spaces and Enter for same key password.
    folder=temp/'hidden-terminal-passwords';folder.mkdir();candidate=folder/'new.apk';make_apk(candidate)
    tty_env={k:v for k,v in base_env.items() if k not in ['AUDIOBOOK_STORE_PASSWORD','AUDIOBOOK_KEY_PASSWORD']}
    master,slave=pty.openpty()
    def controlling_tty():
        os.setsid();fcntl.ioctl(0,termios.TIOCSCTTY,0)
    process=subprocess.Popen(['bash',str(script),str(candidate),str(old)],env=tty_env,stdin=slave,stdout=slave,stderr=slave,preexec_fn=controlling_tty)
    os.close(slave);captured=b'';sent_store=False;sent_key=False;deadline=time.monotonic()+90
    try:
        while time.monotonic()<deadline:
            if select.select([master],[],[],.2)[0]:
                try:chunk=os.read(master,65536)
                except OSError:break
                if not chunk:break
                captured+=chunk
                if not sent_store and 'Пароль хранилища: '.encode() in captured:
                    os.write(master,store_password.encode()+b'\n');sent_store=True
                if not sent_key and 'Пароль закрытого ключа'.encode() in captured:
                    os.write(master,b'\n');sent_key=True
            if process.poll() is not None:break
        assert process.wait(timeout=5)==0,captured.decode(errors='replace')
        assert sent_store and sent_key and cert(folder/'Audiobook-update.apk')==cert(old)
        assert store_password.encode() not in captured,'TTY echoed password'
        assert sha(key)==before
        cases.append({'case':'hidden-terminal-input-and-empty-key-password', 'passed':True})
    finally:
        if process.poll() is None:process.kill();process.wait()
        os.close(master)
    result={'tool':'Android SDK build-tools 35 apksigner, real PKCS12','cases':cases,'original_fixture_key_unchanged':True,
            'user_private_key_available':False,'user_key_signing_tested':False,'fixture_keys_exported':False}
    report=ROOT/'docs/verification/phone-signing-pkcs12.json'
    report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))
