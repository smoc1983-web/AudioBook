from widget_check_support import remove_widgets
from widget_check_support import baseline_bytes
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).parent
base=Path('/workspace/audiobook-splash-merged')
s=(r/'MainActivity.kt').read_text()
android='{http://schemas.android.com/apk/res/android}'
name='com.audiobookdownloader.AbDiagnosticReportProvider'
def tree(node):return node.tag,tuple(sorted(node.attrib.items())),tuple(tree(c) for c in node)
for variant in ['debug','release']:
 manifest=ET.parse(r/('app/src/'+variant+'/AndroidManifest.xml')).getroot()
 remove_widgets(manifest)
 app=manifest.find('application')
 provider=[x for x in app.findall('provider') if x.get(android+'name')==name]
 assert len(provider)==1
 provider=provider[0]
 assert provider.get(android+'authorities')=='${applicationId}.diagnostic_reports'
 assert provider.get(android+'exported')=='false' and provider.get(android+'grantUriPermissions')=='true'
 metadata=provider.find('meta-data')
 assert metadata.get(android+'name')=='android.support.FILE_PROVIDER_PATHS'
 assert metadata.get(android+'resource')=='@xml/audiobook_diagnostic_paths'
 if variant=='debug':
  app.remove(provider)
  assert tree(manifest)==tree(ET.parse(base/'app/src/debug/AndroidManifest.xml').getroot())
paths=ET.parse(r/'app/src/main/res/xml/audiobook_diagnostic_paths.xml').getroot()
assert len(paths)==1 and paths[0].tag=='cache-path'
assert paths[0].get('path')=='diagnostic-reports/'
assert paths[0].get('name')=='diagnostic_reports'
assert 'class AbDiagnosticReportProvider : androidx.core.content.FileProvider()' in s
print('PASS: provider is private, limited to one cache subdirectory, registered for both build types; original debug components/permissions unchanged')
a=s.index('private object AbDiagnosticReportFiles');b=s.index('@Composable\nprivate fun AbCrashReports',a);helper=s[a:b]
for token in ['bufferedWriter(Charsets.UTF_8)','File.createTempFile(','.txt','getUriForFile(app,app.packageName+".diagnostic_reports",file)','Intent.EXTRA_STREAM','newRawUri','FLAG_GRANT_READ_URI_PERMISSION','Intent.createChooser','MAX_AGE_MS','file.delete();throw error']:
 assert token in helper,token
for token in ['Intent.EXTRA_TEXT','Uri.fromFile','FLAG_GRANT_WRITE_URI_PERMISSION','root-path','ACTION_SENDTO','HttpURLConnection','read(context']:
 assert token not in helper,token
assert helper.index('bufferedWriter(Charsets.UTF_8)')<helper.index('getUriForFile(')
ui=s[b:s.index('// Public bibliographic metadata',b)]
for token in ['Отправить отчёт (.txt)','withContext(Dispatchers.IO) {AbDiagnosticReportFiles.create(context,saved.second)}','enabled=!sharing','if(!visible || report!==saved)return@launch','shareJob?.cancel()','CancellationException','shareError','Скопировать отчёт','Отправить текстом']:
 assert token in ui,token
assert 'if(requested<=0)return@LaunchedEffect' in ui
assert s.count('AbCrashReport.read(')==1
print('PASS: UTF-8 snapshot becomes a unique closed file before sharing, attached as a read-only content URI; no automatic sending; old text/copy controls and cancellation guards retained')
for p in (r/'js-check').glob('*.js'):
 assert p.read_bytes()==(base/'js-check'/p.name).read_bytes(),p.name
for item in ['PlaybackService.kt','app/src/main/java/com/SearchBookIndex.java','app/src/main/res/values/audiobook_branding.xml','rename_launcher.py']:
 assert baseline_bytes(r/item)==baseline_bytes(base/item),item
start='private fun AudioBookRoot('
end='// A committed marker survives abrupt process termination.'
old=(base/'MainActivity.kt').read_text()
assert s[s.index(start):s.index(end)]==old[old.index(start):old.index(end)]
print('PASS: splash, all extracted source parsers, playback service and Java index unchanged; structural/provider configuration checks, not Android sharing execution')
