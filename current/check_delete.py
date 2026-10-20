from workspace_paths import WORKSPACE
from pathlib import Path
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
backend=s[s.index('private object AbBookDeletion'):s.index('private class AbDeleteOperation')]
controller=s[s.index('private fun rememberBookDeletion'):s.index('fun bookTotalKey(',s.index('private fun rememberBookDeletion'))]
prepare=s[s.index('    val deleteModernBook=rememberBookDeletion'):s.index('    val keyboardView')]
menu=s[s.index('private fun AbBookMenu'):s.index('private fun AbShelfPicker')]
assert 'deleteRecursively' not in backend and 'fun deleteLibraryBook(' not in s
assert 'val deleteBook: (LibraryBook, (Boolean,String)->Unit) -> Unit' in s
assert 'actions.deleteBook(local) {success,message' in menu and 'enabled=!deleting' in menu
assert 'if(!deleting)dismiss()' in menu and 'deleteMessage=message' in menu
assert 'withContext(Dispatchers.IO) {AbBookDeletion.remove(app,request.resolvedBook)}' in controller
assert 'sync.requestSync()' in controller and 'if(success)removed(request.book)' in controller
print('PASS: async deletion, explicit progress/failure, duplicate tap protection, success only after verification, library refresh on all completed/cancelled operations')

assert 'createDeleteRequest' in controller and 'Build.VERSION.SDK_INT>=30' in controller
assert 'IntentSenderRequest.Builder(sender).build()' in controller and 'StartIntentSenderForResult' in controller
assert 'android.app.RecoverableSecurityException' in backend
assert 'if(Build.VERSION.SDK_INT==29)listOfNotNull(senderUri)' in backend
assert '.take(128)' in backend and 'request.approved' in controller
assert 'if(result.resultCode==android.app.Activity.RESULT_OK)' in controller
assert 'request.approved.addAll(request.batch)' in controller
assert 'runner.run?.invoke(request)' in controller
assert 'AbBookDeletion.selectedFolder(app,request.book,uri)' in controller
assert controller.index('val chosen=AbBookDeletion.selectedFolder')<controller.index('takePersistableUriPermission')
assert 'OpenDocumentTree' in controller
print('PASS: bounded Android 11+ consent batches, one URI per Android 10 recovery grant, reattempt and verification after consent, folder fallback and refusal handling')

assert 'private fun present' in backend and 'Boolean?' in backend
assert 'openFileDescriptor(target.uri,"r")' in backend
assert 'if(denied)null else false' in backend
assert 'if(present(context,target)==false)' in backend
assert 'failed.isEmpty()' in backend
assert 'file.parentFile?.absolutePath==book.folder.absolutePath' in backend
assert 'savedUri?.takeIf{it.authority!="media"}' in backend
assert 'target.file.canonicalFile==File(book.folder.canonicalFile,target.file.name)' in backend
assert 'filter{!it.isDirectory && it.name.endsWith(".mp3",true)}' in backend
assert 'book.folder.canonicalFile!=libraryRoot().canonicalFile && book.folder.listFiles()?.isEmpty()==true' in backend
assert 'if(!publicRoot && libraryDocuments(context,uri,uri).isEmpty())' in backend
assert 'if(!known && physical!=null && !exact && !virtual)continue' in backend
assert 'val expectedName=if(virtual)book.title else book.folder.name' in backend
assert 'book.files.all{it.name in names}' in backend
assert 'first.authority==second.authority' in backend
print('PASS: unreadable is not treated as absent; deletion addresses exact chapters; no directory recursion or parent-tree deletion; wrong physical folders rejected, hidden paths require book-name plus chapter match')

assert 'AbLibraryHousekeeping.hold(book.title)' in backend and 'finally{release()}' in backend
assert 'request.waiting.forEach{it.join()}' in controller
assert 'DisposableEffect(Unit) {onDispose{pending?.release()}}' in controller
assert 'jobs.forEach{it.cancel()}' in prepare and 'downloadIds.entries.filter{it.value==title}' in prepare
assert 'AbLibraryHousekeeping.bookKey(it.book)==name' in prepare
assert 'putString("' not in prepare and 'remove(bookUrlKey' not in prepare and 'remove(bookCoverKey' not in prepare
assert 'targetBook=book.title' in prepare and 'playingTitle.isNotBlank()' in prepare
assert 'manager.query(DownloadManager.Query().setFilterById(id))' in backend
assert 'original==parts[2]' in backend and 'title=="${parts[1]} - ${parts[0]}"' in backend
assert 'else if(found) manager.remove(id)' in backend
assert 'entry.key.startsWith("download_job_")' in backend and 'AbLibraryHousekeeping.bookKey(it)==name' in backend
print('PASS: pending jobs awaited, cancellation scoped to selected book, current selection stopped only for that book, cleanup lease held throughout backend work/consent; online links and collections preserved')

base=(WORKSPACE / 'audiobook-folder-cleanup')
service=(root/'PlaybackService.kt').read_text()
old_service=(base/'PlaybackService.kt').read_text()
for a,b in [('    private fun remainingSleepSeconds()', '    private fun publish'),
            ('    private fun startTrack', '    private fun prepareTrack')]:
    assert service[service.index(a):service.index(b)]==old_service[old_service.index(a):old_service.index(b)]
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
a='internal object AbLibraryHousekeeping'
b='@Suppress("DEPRECATION")\nfun libraryRoot()'
old=(base/'MainActivity.kt').read_text()
assert s[s.index(a):s.index(b)]==old[old.index(a):old.index(b)]
print('PASS: start-track, wake-lock configuration, timer, folder housekeeping and service/permission attributes unchanged; media attributes and product labels intentionally updated')
print('Static source regression checks, not Kotlin compilation, system-consent interaction or deletion on Android.')
