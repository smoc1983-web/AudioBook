"""Reserve a monotonic run suffix before any CI build; read `git ls-remote --tags` on stdin.
The result is frozen in GITHUB_ENV for every build/test/package step of the workflow.
Manual public releases can therefore never make the next Actions run overwrite their tag.
"""
import re,sys

def reserve(run_number, refs):
    if not re.fullmatch(r'[1-9][0-9]*',run_number):raise ValueError('Invalid GitHub run number')
    code=100000+int(run_number)+1
    for line in refs.splitlines():
        match=re.search(r'\srefs/tags/preview-v([1-9][0-9]*)$',line)
        if match:code=max(code,int(match[1])+1)
    if code>2147483647:raise ValueError('Android versionCode exhausted')
    return code-100000

if __name__=='__main__':print(reserve(sys.argv[1],sys.stdin.read()))
