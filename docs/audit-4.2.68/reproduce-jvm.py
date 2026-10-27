"""Reproduce the pure-Java audit; Android-bound tests run separately in CI."""
from pathlib import Path
import subprocess,os,sys,argparse,shutil
parser=argparse.ArgumentParser()
parser.add_argument("--jars-dir",type=Path,required=True)
args=parser.parse_args()
repo=Path(__file__).resolve().parents[2]
out=repo/"target/quality-review";out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
jars=':'.join(str(args.jars_dir/n) for n in ['jsoup-1.23.2.jar','json-20240303.jar','junit-4.13.2.jar','hamcrest-core-1.3.jar'])
sources=[]
for folder in ['app/src/main/java','current/app/src/main/java']:
 for p in (repo/folder).rglob('*.java'):
  if 'android.' not in p.read_text() and 'BuildConfig' not in p.read_text():sources.append(str(p))
tests=[p for p in (repo/'app/src/test/java').rglob('*.java') if p.name!='PreviewUpdateRulesTest.java']
subprocess.run(['java','--add-modules','jdk.compiler','com.sun.tools.javac.Main','-g','-cp',jars,'-d',str(classes),*sources,*map(str,tests)],check=True)
(out/'jacoco.exec').unlink(missing_ok=True)
names=['com.audiobookdownloader.'+p.stem for p in tests]
subprocess.run(['java','-javaagent:'+str(args.jars_dir/'org.jacoco.agent-0.8.12-runtime.jar')+'=destfile='+str(out/'jacoco.exec'),'-cp',str(classes)+':'+jars+':'+str(repo/'app/src/test/resources'),'org.junit.runner.JUnitCore',*names],check=True)
report=repo/'target/site/jacoco/jacoco.xml';report.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-jar',str(args.jars_dir/'org.jacoco.cli-0.8.12-nodeps.jar'),'report',str(out/'jacoco.exec'),'--classfiles',str(classes),'--sourcefiles',str(repo/'app/src/main/java'),'--xml',str(report)],check=True)
print('Compiled production sources:',len(sources),'JUnit classes:',len(tests),'coverage:',report)
