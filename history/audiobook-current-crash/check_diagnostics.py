from pathlib import Path
import json,re,datetime
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
old=Path('/workspace/audiobook-logsender-fix/MainActivity.kt').read_text()
def without_report(value):
    start=value.index('private object AbCrashReport')
    end=value.index('@Composable\nprivate fun AbCrashReports',start)
    return value[:start]+value[end:]
normal=without_report(s).replace('4.0-current-crash-1','4.0-logsender-interop-1')
normal=normal.replace('AbCrashReport.read(this@MainActivity,true)','AbCrashReport.read(this@MainActivity)')
normal=normal.replace('AbCrashReport.read(context,requested>0)','AbCrashReport.read(context)')
assert normal==without_report(old)
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-logsender-fix/PlaybackService.kt').read_bytes()
assert (root/'app/src/debug/AndroidManifest.xml').read_bytes()==Path('/workspace/audiobook-logsender-fix/app/src/debug/AndroidManifest.xml').read_bytes()
for file in Path('/workspace/audiobook-logsender-fix/js-check').glob('*.js'):
    assert (root/'js-check'/file.name).read_bytes()==file.read_bytes(),file.name
print('PASS: application code outside diagnostics, browser sessions, parser scripts, service and debug manifest unchanged')

read=s[s.index('private fun readCrashBuffer'):s.index('private fun readLocal')]
assert '"epoch","-t","2000"' in read
assert '"--uid="+android.os.Process.myUid()' in read and '"--pid="+pid' in read
assert 'elapsedRealtime()+3000L' in read
while_headers=re.findall(r'while\(([^\n]+)\)',read)
assert len(while_headers)==2 and all('output.size()' not in x for x in while_headers)
assert 'if(output.size()+count>64_000)' in read
assert 'output.write(previous,previous.size-keep,keep)' in read
assert 'output.reset()' in read and 'output.write(buffer,0,count)' in read
assert 'process?.destroy()' in read and 'completed=true' in read
assert 'Подходящий стек этого события в журнале не найден' in read
selector=s[s.index('private fun matchingCrash'):s.index('private fun readCrashBuffer')]
assert 'time in (stamp-90_000L)..(stamp+5_000L)' in selector
assert 'process==pid' in selector and 'else if(line.startsWith("---------"))accept=false' in selector
assert 'SystemSnapshot(exit.timestamp,exit.pid,text.toString())' in s
assert 'PID завершённого процесса' in s and 'exit.processName' in s
audit=s[s.index('private fun installedBuild'):s.index('private val LOG_PREFIX')]
assert 'getPackageInfo(context.packageName,flags)' in audit
assert 'MATCH_DISABLED_COMPONENTS' in audit and 'getComponentEnabledSetting' in audit
assert 'info.lastUpdateTime' in audit and 'info.longVersionCode' in audit
assert 'info.providers.orEmpty().firstOrNull' in audit and 'info.services.orEmpty().firstOrNull' in audit
assert 'val override=' not in s
print('PASS: bounded tail collection drains past the old limit; event PID/time filters and installed APK audit present')

# Execute the exact portable regular expression extracted from Kotlin.
# This tests timestamp/PID capture syntax, not Kotlin/Android execution.
literal=re.search(r'LOG_PREFIX=Regex\(("(?:\\.|[^"\\])*")\)',s).group(1)
pattern=re.compile(json.loads(literal))
cases=[
    ('1791799080.923  1431  1431 E AndroidRuntime: FATAL EXCEPTION: main','1791799080.923','1431','E','AndroidRuntime','FATAL EXCEPTION: main'),
    ('1791799080.000001   6930    20 E AndroidRuntime: Caused by: java.lang.IllegalStateException','1791799080.000001','6930','E','AndroidRuntime','Caused by: java.lang.IllegalStateException'),
    ('1791799080.002   6930  6930 F libc: Fatal signal 11','1791799080.002','6930','F','libc','Fatal signal 11'),
    ('1791799080.010   6930  6930 E AndroidRuntime: Ошибка: название книги — строка','1791799080.010','6930','E','AndroidRuntime','Ошибка: название книги — строка'),
]
for line,stamp,pid,level,tag,message in cases:
    match=pattern.fullmatch(line)
    assert match is not None,line
    assert match.group(1)==stamp and match.group(2)==pid
    assert match.group(4)==level and match.group(5)==tag and match.group(6)==message
assert pattern.fullmatch('10-10 11:53:28.923 1431 1431 E AndroidRuntime: Old threadtime record') is None
assert pattern.fullmatch('--------- beginning of crash') is None
assert pattern.fullmatch('\tat app.example.Main.onCreate(Main.kt:42)') is None
assert pattern.fullmatch('logcat: unknown option --uid') is None
print('PASS: exact timestamp/PID regex handles epoch precision, fatal/native/Unicode messages and rejects banners/old format/errors')
print('No Kotlin compilation or real logcat run claimed.')
