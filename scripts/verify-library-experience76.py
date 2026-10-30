import os,json,hashlib,subprocess,struct,zipfile,xml.etree.ElementTree as E
from pathlib import Path
ROOT=Path("receipts");ROOT.mkdir(exist_ok=True)
CANDIDATE=100076
SOURCE="51d47acaaec3164c72f7ce53b904a9b097a0c06e"
NORMAL=41095515200
SCOPED=41095511814
def cmd(*args):return subprocess.check_output(list(args),text=True)
def dl(run,name,dest):
    subprocess.check_call(["gh","run","download",str(run),"--repo",os.environ["GITHUB_REPOSITORY"],"--name",name,"--dir",str(dest)])
dl(NORMAL,"source-test-reports",ROOT/"source")
dl(NORMAL,"android-startup-test-reports",ROOT/"native")
dl(SCOPED,"library-experience-api23",ROOT/"scope23")
dl(SCOPED,"library-experience-api36",ROOT/"scope36")
for code,dest in [(CANDIDATE,"candidate"),(100074,"baseline")]:
    args=["gh","release","download","preview-v"+str(code),"--repo",os.environ["GITHUB_REPOSITORY"],"--pattern","Audiobook-Preview.apk","--dir",str(ROOT/dest)]
    if dest=="candidate":args+=["--pattern","preview-update.json"]
    subprocess.check_call(args)
tools=Path(os.environ["ANDROID_HOME"])/"build-tools/35.0.0"
apk=ROOT/"candidate/Audiobook-Preview.apk"
meta=json.loads((ROOT/"candidate/preview-update.json").read_text())
certificate="3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7"
assert meta["packageName"]=="com.audiobookdownloader.preview" and meta["versionCode"]==CANDIDATE and meta["versionName"]=="4.2.76",meta
assert meta["commit"]==SOURCE
assert apk.stat().st_size==meta["size"]
assert hashlib.sha256(apk.read_bytes()).hexdigest()==meta["sha256"]
verified=cmd(str(tools/"apksigner"),"verify","--verbose","--print-certs",str(apk))
assert "Signer #1 certificate SHA-256 digest: "+certificate in verified and meta["certificateSha256"]==certificate
assert "Verified using v1 scheme (JAR signing): true" in verified
assert "Verified using v2 scheme (APK Signature Scheme v2): true" in verified
badging=cmd(str(tools/"aapt"),"dump","badging",str(apk))
assert "package: name='com.audiobookdownloader.preview' versionCode='100076' versionName='4.2.76'" in badging
assert "application-label:'Audiobook'" in badging and "application-debuggable" not in badging
def definitions(data):
    def u(off):
        assert 0<=off<=len(data)-4
        return struct.unpack_from("<I",data,off)[0]
    def leb(p):
        value=shift=0
        for _ in range(5):
            assert p<len(data)
            b=data[p];p+=1;value|=(b&127)<<shift
            if b<128:return value,p
            shift+=7
        raise AssertionError("Invalid ULEB")
    assert data.startswith(b"dex")
    strings,types,methods,classes=u(60),u(68),u(92),u(100)
    def string(idx):
        p=u(strings+idx*4);_,p=leb(p)
        return data[p:data.index(0,p)].decode("utf-8",errors="replace")
    def typename(idx):return string(u(types+idx*4))
    found={};count=u(96)
    assert count<200000 and classes+count*32<=len(data)
    for i in range(count):
        off=classes+i*32;name=typename(u(off));p=u(off+24);names=set()
        if p:
            sizes=[]
            for _ in range(4):
                n,p=leb(p);sizes.append(n)
            for _ in range(sizes[0]+sizes[1]):
                _,p=leb(p);_,p=leb(p)
            for n in sizes[2:]:
                idx=0
                for _ in range(n):
                    delta,p=leb(p);idx+=delta
                    _,p=leb(p);code,p=leb(p)
                    if code:
                        m=methods+idx*8
                        assert typename(struct.unpack_from("<H",data,m)[0])==name
                        names.add(string(u(m+4)))
        found.setdefault(name,set()).update(names)
    return found
classes={}
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if name.startswith("classes") and name.endswith(".dex"):
            for c,methods in definitions(z.read(name)).items():classes.setdefault(c,set()).update(methods)
    required={
      "BookDescriptionParser":set(),"BookDescriptionRepository":{"load"},
      "LibraryBackup":{"manifest","export","inspect","restore","safeEntry"},
      "AbPersistentDownloads":{"entries","enqueueBatch","restore","pump","cancelChapter","cancelBook","pause","install"},
      "AbDownloadsWorker":{"doWork"},"AbQueueRecoveryWorker":{"doWork"},
      "LibraryExperienceRules":{"completedEdition"},
      "ExperiencePanelsKt":{"AbUnifiedBookDialog","AbPersistentQueueDialog","AbBackupPanel","AbStoragePanel","AbReadabilityPanel"},
      "MainActivityKt":{"AbBookDescriptionDialog","AbDialog"}
    }
    proof={}
    for c,methods in required.items():
        descriptor="Lcom/audiobookdownloader/"+c+";"
        assert descriptor in classes,c
        names={n.split("$")[0] for n in classes[descriptor]}
        assert methods<=names,(c,methods-names)
        proof[c]=sorted(methods)
    for c in ["BookDescriptionTest","LibraryExperienceTest","DeletionFixtureProvider","TestBitmapExports","BookDeletionTest"]:
        assert "Lcom/audiobookdownloader/"+c+";" not in classes,c
    assert not any("test_network_security" in n for n in z.namelist())
    notes=json.loads(z.read("assets/release-notes.json"))
    assert len(notes["changes"])==9
    for term in ["свечен","резерв","очеред"]:assert any(term in n.lower() for n in notes["changes"]),term
audits=[]
for label,path in [("candidate",apk),("baseline",ROOT/"baseline/Audiobook-Preview.apk")]:
    audits.append(json.loads(cmd("python3","scripts/audit-installable-apk.py","--apk",str(path),"--build-tools",str(tools),"--output",str(ROOT/(label+"-permissions.json")))))
assert set(audits[0]["requestedPermissions"])==set(audits[1]["requestedPermissions"])
report={"schema":1,"source":SOURCE,"versionCode":CANDIDATE,"bytes":meta["size"],"sha256":meta["sha256"],"certificateSha256":certificate,"packageName":meta["packageName"],"label":"Audiobook","debuggable":False,"signaturesV1V2":True,"definedClassCount":len(classes),"definedMethods":proof,"debugFixturesAbsent":True,"releaseNotes":notes["changes"],"permissionsUnchangedFrom74":True,"actualSignedApkInspected":True,"publicFeedPublished":False,"publicFeedStill":100074,"physicalPhone":False,"actualAndroidUpdaterNetworkDownload":False}
summary={}
for variant in ["Debug","Release"]:
    files=[p for p in (ROOT/"source").rglob("*.xml") if "test-results/test"+variant+"UnitTest/" in p.as_posix()]
    assert files,variant
    counts=dict(tests=0,failures=0,errors=0,skipped=0)
    for p in files:
        suite=E.parse(p).getroot()
        for key in counts:counts[key]+=int(suite.get(key,0))
    assert counts==dict(tests=147,failures=0,errors=0,skipped=1),counts
    summary["jvm"+variant]=counts
for label,folder in [("android36","native"),("scoped23","scope23"),("scoped36","scope36")]:
    suites=[E.parse(p).getroot() for p in (ROOT/folder).rglob("*.xml") if "androidTest-results" in p.as_posix()]
    assert suites,label
    suite=max(suites,key=lambda s:int(s.get("tests",0)))
    counts={key:int(suite.get(key,0)) for key in ["tests","failures","errors","skipped"]}
    assert counts["failures"]==counts["errors"]==0,counts
    assert counts["tests"]>=(75 if label=="android36" else 33)
    for name in ["dialogRetainsReadableTextWhenUserRefreshesOffline","longDescriptionKeepsRefreshAndCloseVisibleAtLargeTextSize"]:
        assert any(t.get("name")==name and t.find("failure") is None for t in suite.findall("testcase")),(label,name)
    summary[label]=counts
upgrade=json.loads(next((ROOT/"native").rglob("installed-upgrade.json")).read_text())
assert upgrade["baselineCode"]==100074 and upgrade["newCode"]==CANDIDATE
for key in ["oldUpdaterAcceptedNewApk","oldUpdaterOpenedSystemConfirmation","androidPackageReplacementSucceeded","settingsCollectionFolderAndPrivateFilePreserved","certificateUnchanged","privateAudioFileChecksumPreserved"]:assert upgrade[key],key
lint=E.parse(next((ROOT/"source").rglob("lint-results-release.xml"))).getroot();severity={}
for row in lint.findall("issue"):severity[row.get("severity")]=severity.get(row.get("severity"),0)+1
assert not(severity.get("Error",0) or severity.get("Fatal",0))
osv=json.loads(next((ROOT/"source").rglob("dependency-advisories.json")).read_text())
assert osv["complete"] and not osv["findings"]
summary["lint"]=severity;summary["osv"]={"complete":True,"resolvedModules":len(osv["modules"]),"findings":[]}
report.update(tests=summary,upgrade=upgrade,normalRun=NORMAL,scopedRun=SCOPED,applicationTestsRerun=False)
(ROOT/"library-experience76-verification.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print(json.dumps(report,ensure_ascii=False))
