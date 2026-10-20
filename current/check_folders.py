from workspace_paths import WORKSPACE
from pathlib import Path
import re
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
service=(root/'PlaybackService.kt').read_text()
base=(WORKSPACE / 'audiobook-compact-player')
obj=s[s.index('internal object AbLibraryHousekeeping'):s.index('@Suppress("DEPRECATION")\nfun libraryRoot()')]
assert 'deleteRecursively' not in obj and 'DocumentsContract' not in obj and 'contentResolver' not in obj
assert 'val root=libraryRoot()' in obj
assert 'if(!directChild(parent,root))' in obj and 'if(!directChild(root,folder)' in obj
assert 'child.canonicalFile==File(parent.canonicalFile,child.name)' in obj
assert 'val children=folder.listFiles() ?: return 0' in obj
assert 'remaining.isEmpty() && folder.delete()' in obj
assert 'if(depth>8 || protectedNow())return 0' in obj
assert 'if(!protectedNow() && remaining.isEmpty()' in obj
assert 'if(!hasLibraryAudioPermission(context))continue' in obj
assert 'val allowed=setOf("cover.jpg","book_metadata.json")' in obj
assert 'it.name !in allowed' in obj and 'json.optString("format")=="AudioBookMetadata"' in obj
assert 'json.optInt("version")==1' in obj and 'bookKey(json.optString("title"))==name' in obj
assert obj.index('if(!owned)continue')<obj.index('cover.delete()')<obj.index('metadata.delete()')
assert 'val latest=folder.listFiles() ?: continue' in obj and 'now-it.lastModified()<QUIET_MS' in obj
assert 'catch(_:Exception) {null}' in obj and 'val retained=retainedNames(context) ?: return CleanupResult(0)' in obj
for key in ['FAVORITES_KEY','SHELVES_KEY','QUEUE_KEY','download_job_']:
    assert key in obj
assert 'state.isPlaying || state.isLoading' in obj and 'holds.containsKey(name)' in obj
print('PASS: deletion is scoped to the public app root, rejects aliases and unreadable listings, retains unknown/audio files, saved references and downloads; legacy-only deletion requires a valid ownership marker')

request=s[s.index('        request.job=modernWork.launch {'):s.index('    fun cancelModernDownload')]
assert request.index('AbLibraryHousekeeping.hold(book)')<request.index('keepDownloadedCover(book)')
assert request.index('putString("download_job_"+id')<request.index('releaseFolder()')
assert '} finally {\n                releaseFolder()' in request
cover=s[s.index('    suspend fun saveForDownload'):s.index('    private fun decode(',s.index('    suspend fun saveForDownload'))]
assert 'exportToLibrary:Boolean=true' in cover
assert 'publicFolder=exportToLibrary && (local==null || local.folderUri.isBlank())' in cover
assert 'if(publicFolder) abWriteBookMetadata(File(folder,"book_metadata.json"),metadata)' in cover
assert 'if(keep) abWriteBookMetadata(File(File(context.filesDir,"book-covers")' in cover
assert 'if(publicFolder) {' in cover and 'if(keep) writeImage' in cover
assert 'if(exportToLibrary && local!=null && local.folderUri.isNotBlank()' in cover
assert '} finally {\n            releaseFolder()' in cover
assert 'saveForDownload(context.applicationContext,title,local,cover,url,exportToLibrary=local!=null)' in s
print('PASS: previews do not export library files; explicit downloads and saved books keep durable covers; linked libraries no longer create filesystem shadow folders; in-flight writers reserve their titles')

assert 'ScheduledThreadPoolExecutor(1,' in obj and 'worker.schedule(Runnable {' in obj
assert 'pending?.cancel(false)' in obj and 'setRemoveOnCancelPolicy(true)' in obj
assert 'if(requestGeneration==token)schedule(app,QUIET_MS+1500L)' in obj
assert 'LaunchedEffect(data.searching,data.searchScreenId,data.page)' in s
assert 'LaunchedEffect(data.searching,data.searchScreenId,data.page,data.version)' not in s
assert 'if(key==FAVORITES_KEY || key==SHELVES_KEY || key==QUEUE_KEY)coverRetentionVersion++' in s
assert 'if(wasActive && !isPlaying && !isLoading)AbLibraryHousekeeping.request(applicationContext)' in service
print('PASS: one debounced background worker, deferred retry for recent files, event-based scheduling rather than progress polling; no audio control commands in housekeeping')

# Audio attributes and size/download feedback intentionally change this release.
base=(WORKSPACE / 'audiobook-delete-fix')
old_service=(base/'PlaybackService.kt').read_text()
for a,b in [('    private fun startTrack','    private fun prepareTrack'),
            ('    private fun remainingSleepSeconds()', '    private fun publish')]:
    assert service[service.index(a):service.index(b)]==old_service[old_service.index(a):old_service.index(b)],a
a='    private fun prepareTrack'
b='    @Suppress("DEPRECATION")\n    private fun requestFocus'
preparation=service[service.index(a):service.index(b)].replace('player.setAudioAttributes(mediaAttributes)','player.setAudioStreamType(AudioManager.STREAM_MUSIC)')
assert preparation==old_service[old_service.index(a):old_service.index(b)]
old_main=(base/'MainActivity.kt').read_text()
a='internal object AbLibraryHousekeeping'
b='@Suppress("DEPRECATION")\nfun libraryRoot()'
assert s[s.index(a):s.index(b)]==old_main[old_main.index(a):old_main.index(b)]
a='private object AbBookDeletion';b='private class AbDeleteOperation'
assert s[s.index(a):s.index(b)]==old_main[old_main.index(a):old_main.index(b)]
# Branding adds labels, while all existing service/provider/permission attributes remain.
import xml.etree.ElementTree as ET
old_manifest=ET.parse(base/'app/src/debug/AndroidManifest.xml').getroot()
new_manifest=ET.parse(root/'app/src/debug/AndroidManifest.xml').getroot()
name_attr='{http://schemas.android.com/apk/res/android}name'
for old_node in list(old_manifest)+list(old_manifest.find('application')):
    if old_node.tag=='application':continue
    candidates=[n for n in new_manifest.iter(old_node.tag) if n.get(name_attr)==old_node.get(name_attr)]
    assert len(candidates)==1
    assert all(candidates[0].get(k)==v for k,v in old_node.attrib.items())
print('PASS: preparation preserved except media attributes; timers, start-track, wake lock, deletion backend, housekeeping and debug service/permission attributes preserved (labels updated)')
print('Static regression checks only; no Kotlin compilation or Android filesystem/device execution.')
