from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-logsender-fix/MainActivity.kt').read_text()
start=s.index('    fun read(context:Context)')
end=s.index('    private fun readLocal(context:Context)',start)
s=s[:start]+(root/'read-report.txt').read_text()+s[end:]
start=s.index('    private fun readSystem(')
end=s.index('    fun seen(',start)
block=s[start:end]
assert 'Pair<Long,String>?' in block
block=block.replace('Pair<Long,String>?','SystemSnapshot?',1)
block=block.replace('            exit.timestamp to text.toString()',
                    '            SystemSnapshot(exit.timestamp,exit.pid,text.toString())')
needle='            if(exit.reason==android.app.ApplicationExitInfo.REASON_ANR)'
assert needle in block
block=block.replace(needle,'''            text.append("PID завершённого процесса: "+exit.pid+"\\\\nПроцесс: "+exit.processName+"\\\\n")
'''.replace('\\\\n','\\n')+needle,1)
s=s[:start]+block+s[end:]
assert 'AbCrashReport.read(this@MainActivity)' in s
s=s.replace('AbCrashReport.read(this@MainActivity)','AbCrashReport.read(this@MainActivity,true)',1)
s=s.replace('AbCrashReport.read(context)}','AbCrashReport.read(context,requested>0)}',1)
s=s.replace('4.0-logsender-interop-1','4.0-current-crash-1')
(root/'MainActivity.kt').write_text(s)
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
