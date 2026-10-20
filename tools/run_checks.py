#!/usr/bin/env python3
"""Run saved current checks portably; does not attempt an Android build."""
from pathlib import Path
import subprocess,sys,shutil,tempfile

root=Path(__file__).resolve().parent.parent
current=root/'current'
def run(args):
 print('+ '+' '.join(map(str,args)),flush=True)
 subprocess.run(list(map(str,args)),cwd=current,check=True)

try:
 import PIL
except ImportError:
 raise SystemExit('Install Pillow first: python -m pip install Pillow')
if not shutil.which('node') or not shutil.which('java'):
 raise SystemExit('Node.js and a JDK 11+ must be installed.')

run([sys.executable,'check.py'])
for path in sorted(current.glob('check_*.py')):run([sys.executable,path.name])
for path in sorted(current.glob('test-*.cjs')):run(['node',path.name])
for path in sorted((current/'tests').glob('test-*.cjs')):run(['node',path.relative_to(current)])
with tempfile.TemporaryDirectory(prefix='audiobook-checks-') as build:
 run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','8','-target','8','-Xlint:-options',
      '-d',build,'app/src/main/java/com/SearchBookIndex.java','app/src/main/java/com/LibraryRecoveryRules.java',
      'tests/SearchBookIndexTest.java','tests/LibraryRecoveryRulesTest.java'])
 for name in ['SearchBookIndexTest','LibraryRecoveryRulesTest']:run(['java','-cp',build,name])
print('All saved source/parser/JVM checks passed. Android build/device tests were not run.')
