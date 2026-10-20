from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
old=Path('/workspace/audiobook-crash-fix/MainActivity.kt').read_text()

# Retain the independent native recovery and durable startup checks.
startup=Path('/workspace/audiobook-crash-fix/check_crash.py').read_text()
exec(startup[:startup.index('helper=s[')],{'__file__':str(root/'check_crash.py')})
assert 'AndroidView(' not in s and 'androidx.compose.ui.viewinterop.AndroidView' not in s
assert 'ImageView(' not in s and 'import android.widget.ImageView' not in s
nav=s[s.index('val navigationActive = remember'):s.index('AbModernApp(modernData')]
assert 'AbDetachedBrowser(onFailure=' in nav and 'navigationId==loadId' in nav
assert 'navigationActive[0] = false' in nav
assert 'AbAzbykaPages.load(this,currentUrl,modernWork' in nav
hidden=s[s.index('fun HiddenBookWebView'):s.index('abstract class BookWebsiteTask')]
assert 'AbDetachedBrowser' in hidden and 'holder[0]?.close()' in hidden
helper=s[s.index('private fun abCreateBrowser'):s.index('private object AbWebViews')]
assert 'setup:WebView.()->Unit):WebView?' in helper
assert 'return null' in helper and 'return android.view.View' not in helper
assert 'web.measure(' in helper and 'web.layout(0,0,width,height)' in helper
assert 'web.onResume()' in helper and 'DisposableEffect(context)' in helper
assert 'alive[0]=false' in helper and 'web?.let{AbWebViews.release(it)}' in helper
assert '.addView(' not in helper and '.removeView(' not in helper
assert 'abCreateBrowser(context,{if(alive[0])failure(it)},setup)' in helper
assert 'catch(error:RuntimeException)' in helper and 'catch(error:LinkageError)' in helper
assert 'coerceIn(0,1)' in s[s.index('private fun AbExternalGenreSearch'):s.index('private object AbAzbykaPages')]
assert 'ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(115.dp)' in s
assert 'ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(360.dp)' in s
assert 'Box(Modifier.fillMaxWidth().height(330.dp))' in s
assert '4.0-startup-recovery-1' not in s and s.count('4.0-logsender-interop-1')==3
print('PASS: all AndroidView/ImageView call sites removed; detached viewport/session lifecycle and cover layout retained')

manifest=ET.parse(root/'app/src/debug/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
tools='{http://schemas.android.com/tools}'
app=manifest.find('application')
assert app is not None and len(manifest)==1 and not app.attrib
assert len(app)==2
assert [(n.tag,n.get(android+'name'),n.get(tools+'node')) for n in app]==[
    ('provider','com.itsaky.androidide.logsender.utils.LogSenderInstaller','remove'),
    ('service','com.itsaky.androidide.logsender.LogSenderService','remove')]
assert all(set(n.attrib)=={android+'name',tools+'node'} for n in app)
assert 'PlaybackService' not in (root/'app/src/debug/AndroidManifest.xml').read_text()
print('PASS: debug overlay is valid XML and targets only the two IDE LogSender components')

anr=Path('/workspace/audiobook-anr-fix/check_anr.py').read_text()
exec(anr[:anr.index('# No source parsers changed')],{'__file__':str(root/'check_anr.py')})
for file in Path('/workspace/audiobook-crash-fix/js-check').glob('*.js'):
    assert (root/'js-check'/file.name).read_bytes()==file.read_bytes(),file.name
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-crash-fix/PlaybackService.kt').read_bytes()
print('PASS: parser scripts and PlaybackService.kt byte-identical; prior ANR checks retained')
print('Static checks only. No Android build, manifest-merger run or device session claimed.')
