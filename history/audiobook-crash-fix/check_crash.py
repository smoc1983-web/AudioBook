from pathlib import Path
import re
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
activity=s[s.index('class MainActivity :'):s.index('private object AbLaunchState')]
assert 'private val accessState by lazy' in activity
assert activity.index('showStartup(recover)')<activity.index('private fun startApp(')
startup=activity[activity.index('private fun showStartup'):activity.index('override fun onStart')]
assert 'setContentView(panel)' in startup
assert 'AudioBookApp' not in startup and 'WebView(' not in startup
assert 'AbCrashReport.read(this@MainActivity)' in startup and 'withContext(Dispatchers.IO)' in startup
assert 'Открыть без фоновой загрузки' in startup and 'Скопировать отчёт' in startup
launch=activity[activity.index('private fun startApp'):activity.index('private fun startupFailed')]
assert launch.index('AbLaunchState.arm(this)')<launch.index('setContent {')
assert launch.index('withFrameNanos')<launch.index('AbLaunchState.healthy')
assert 'AudioBookApp(libraryAccessVersion,foreground,browserGeneration,!withoutAutomaticCatalogs)' in launch
assert 'catch(error: RuntimeException)' in launch and 'catch(error: LinkageError)' in launch
marker=s[s.index('private object AbLaunchState'):s.index('fun Modifier.appCard')]
assert 'store.getLong("token",0L)==token' in marker
assert '!store.getBoolean("failed",false)' in marker
assert 'putBoolean("failed",true).commit()' in marker
assert 'putBoolean("failed",false).commit()' in marker
assert '.clear()' not in marker and '.remove(' not in marker
assert 'AbLaunchState.failed(app)' in s[s.index('private class Recorder'):s.index('@Synchronized fun install')]
modern=s[s.index('private fun AbModernApp'):s.index('private fun AbSearchScreen')]
assert 'catalogueReady && catalogueRequested' in modern
assert modern.index('withFrameNanos')<modern.index('delay(1500L)')<modern.index('AbDiscoveryLoaders')
assert 'onLoadCatalogue={catalogue=emptyMap();catalogueRequested=true}' in modern
print('PASS: independent native recovery, committed startup marker, token/exception protection and deferred catalogues')

helper=s[s.index('private fun abCreateBrowser'):s.index('private object AbWebViews')]
assert 'catch(error:RuntimeException){failure(error)}' in helper
assert 'catch(error:LinkageError){failure(error)}' in helper
assert 'AbCrashReport.record(context,error' in helper
assert '.post {failed(message)}' in helper
assert not re.search(r'\bcatch\(.*(?:Throwable|OutOfMemoryError|VirtualMachineError)',helper)
calls=list(re.finditer('HiddenBookWebView',s))
assert len(calls)==8,len(calls)
assert s.count('HiddenBookWebView(onFailure=')==7
assert 'abCreateBrowser(webContext,{message ->' in s
assert 'abCreateBrowser(context,{if(alive[0])failed(it)})' in s
print('PASS: browser construction failures complete every active source loader and preserve the error')

report=s[s.index('private object AbCrashReport'):s.index('@Composable\nprivate fun AbCrashReports')]
assert 'ProcessBuilder("logcat","-d","-b","crash"' in report
assert '"--uid="+android.os.Process.myUid()' in report
assert 'elapsedRealtime()+1800L' in report and 'output.size()<64_000' in report
assert 'process?.destroy()' in report
assert 'FATAL EXCEPTION' in report and 'Время каждой записи' in report
assert 'REVISION="4.0-startup-recovery-1"' in report
assert '@Synchronized private fun save' in report and 'atomic.finishWrite(output)' in report
print('PASS: optional bounded UID-filtered crash log, dated records and atomic exception saving')

anr=Path('/workspace/audiobook-anr-fix/check_anr.py').read_text()
exec(anr[:anr.index('# No source parsers changed')],{'__file__':str(root/'check_anr.py')})
for file in Path('/workspace/audiobook-azbyka-direct/js-check').glob('*.js'):
    assert (root/'js-check'/file.name).read_bytes()==file.read_bytes(),file.name
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-azbyka-direct/PlaybackService.kt').read_bytes()
assert 'AbAzbykaSearchTask(web,query)' in s
assert 'https://azbyka.ru/audio/karta-sajta' in s
assert 'abMatchesBookQuery(it,query)' in s
print('PASS: source parsers byte-identical; direct Azbyka search, ANR fix and service retained')
print('Static checks only; Android compilation and runtime not tested.')
