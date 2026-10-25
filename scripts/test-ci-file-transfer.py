"""Check the observed short-pipe failure and the mandatory checksum gate without a device."""
import ast,hashlib,subprocess,tempfile,types
from pathlib import Path
module=ast.parse(Path('scripts/test-legacy-collections.py').read_text())
function=next(n for n in module.body if isinstance(n,ast.FunctionDef) and n.name=='write')
payload='Коллекции: книга и серия'.encode();expected=hashlib.sha256(payload).hexdigest()
def scenario(failures):
 attempts=[];checks=[]
 def transfer(args,**kwargs):
  assert 'input' not in kwargs and kwargs['stdin'].seekable()
  assert kwargs['stdin'].read()==payload
  assert args[5]=='of=/data/user/0/com.audiobookdownloader/shared_prefs/fixture.xml'
  attempts.append(args);return subprocess.CompletedProcess(args,0)
 def run(*args):
  if 'sha256sum' in args:
   checks.append(args)
   if len(checks)<=failures:raise subprocess.CalledProcessError(1,args,'missing fixture')
   return expected+'  fixture.xml'
  return ''
 env=dict(Path=Path,hashlib=hashlib,tempfile=tempfile,time=types.SimpleNamespace(sleep=lambda _:None),
          subprocess=types.SimpleNamespace(run=transfer,PIPE=subprocess.PIPE,CalledProcessError=subprocess.CalledProcessError,TimeoutExpired=subprocess.TimeoutExpired),run=run)
 exec(compile(ast.Module(body=[function],type_ignores=[]),'<fixture-transfer>','exec'),env)
 try:env['write']('com.audiobookdownloader','shared_prefs/fixture.xml',payload)
 except AssertionError:
  assert failures>=3 and len(attempts)==3
 else:assert failures<3 and len(attempts)==failures+1
 return len(attempts)
assert scenario(0)==1
assert scenario(2)==3
assert scenario(99)==3
print('PASS: regular-file UTF-8 transfer, short-transfer retries, strict checksum failure after three attempts')
