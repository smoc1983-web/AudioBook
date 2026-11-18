package com.audiobookdownloader

import android.content.*
import android.net.Uri
import android.provider.DocumentsContract as Docs
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class LibraryRootsTest {
 private val app=ApplicationProvider.getApplicationContext<Context>()
 private class Device(base:Context):ContextWrapper(base) {
  val id=UUID.randomUUID().toString()
  override fun getApplicationContext():Context=this
  override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("roots-$id-$name",mode)
  override fun getFilesDir()=File(super.getCacheDir(),"roots-device-$id").apply{mkdirs()}
 }
 private val c=Device(app)
 private val p get()=c.getSharedPreferences("audiobook_downloader",0)
 private val data=File(app.cacheDir,"deletion-documents/roots-${c.id}").apply{mkdirs()}
 private fun tree(folder:File)=Docs.buildTreeDocumentUri(app.packageName+".deletion-fixture","root/"+folder.relativeTo(File(app.cacheDir,"deletion-documents")).path).toString()
 private fun audio(parent:File,name:String):File=File(File(parent,name).apply{mkdirs()},"01.mp3").apply{writeBytes(ByteArray(2048){(it%239).toByte()})}
 @After fun clean(){data.deleteRecursively();c.filesDir.deleteRecursively()}
 @Test fun migrationIsAdditiveAndAnExplicitEmptyListDoesNotReconnectOldGrants() {
  val old=tree(File(data,"legacy"));val next=tree(File(data,"next"))
  p.edit().putString(LibraryRoots.LEGACY,old).putInt("unrelated",17).commit()
  assertEquals(listOf(old),LibraryRoots.load(p).roots)
  assertEquals(old,p.getString(LibraryRoots.LEGACY,null))
  assertTrue(LibraryRoots.add(p,next));assertTrue(LibraryRoots.add(p,next));assertEquals(listOf(old,next),LibraryRoots.load(p).roots)
  assertTrue(LibraryRoots.remove(p,old));assertTrue(LibraryRoots.remove(p,next))
  assertTrue(LibraryRoots.load(p,old).roots.isEmpty());assertEquals(old,p.getString(LibraryRoots.LEGACY,null));assertEquals(17,p.getInt("unrelated",0))
 }
 @Test fun badConfigurationIsPreservedUntilConfirmedRecoveryAndOldSnapshotIsNotOverwritten() {
  val old=tree(File(data,"old"));val malformed="{broken original";p.edit().putString(LibraryRoots.LEGACY,old).putString(LibraryRoots.KEY,malformed).commit()
  assertFalse(LibraryRoots.load(p).valid);assertEquals(malformed,p.getString(LibraryRoots.KEY,null))
  try{LibraryRoots.add(p,tree(File(data,"new")));fail()}catch(_:IllegalArgumentException){}
  assertEquals(malformed,p.getString(LibraryRoots.KEY,null));assertTrue(LibraryRoots.recover(p))
  assertEquals(malformed,JSONObject(p.getString("library_roots_damaged_v1","")!!).getString("value"));assertEquals(listOf(old),LibraryRoots.load(p).roots)
  p.edit().putString(LibraryRoots.KEY,"second corruption").commit();assertFalse(LibraryRoots.recover(p));assertEquals("second corruption",p.getString(LibraryRoots.KEY,null))
 }
 @Test fun independentRootsAndAnUnavailableDirectoryKeepOtherBooksAndAudioUnchanged() {
  val a=File(data,"one");val b=File(data,"two");val fa=audio(a,"A-${c.id}");val fb=audio(b,"B-${c.id}")
  val roots=listOf(tree(a),tree(File(data,"missing")),tree(b));p.edit().putString(LibraryRoots.KEY,LibraryRoots.encode(roots)).commit()
  val report=scanLibraryReport(c);val relevant=report.books.filter{it.title in listOf(fa.parentFile!!.name,fb.parentFile!!.name)}
  assertEquals(2,relevant.size);assertTrue(report.folderUnavailable);assertEquals(1,report.rootIssues.size)
  assertEquals(roots,LibraryRoots.load(p).roots);assertTrue(fa.exists());assertTrue(fb.exists())
  relevant.forEach{assertEquals(2048L,it.files.single().let{f->c.contentResolver.openFileDescriptor(Uri.parse(libraryTrackSource(f)),"r")!!.use{fd->fd.statSize}})}
 }
 @Test fun unrelatedDownloadFailureDoesNotHideAnExternalBookWithTheSameChapterName() {
  val parent=File(data,"external");val file=audio(parent,"External-${c.id}")
  LibraryRoots.add(p,tree(parent))
  c.getSharedPreferences("audiobook_audio_validation_v1",0).edit().putString("blocked_123",AbLibraryHousekeeping.bookKey(file.parentFile!!.name)+"\u0000"+"01").commit()
  assertEquals("A different managed download must not hide the selected provider file",1,scanLibrary(c).count{it.title==file.parentFile!!.name})
 }
 @Test fun overlappingGrantsDoNotDuplicateBooksOrChangeTheirIdentityPositionNotesOrCollections() {
  val outer=File(data,"overlap");val file=audio(outer,"Book-${c.id}");val parent=tree(outer);val child=tree(file.parentFile!!)
  p.edit().putString(LibraryRoots.LEGACY,parent).commit()
  val initial=scanLibrary(c).single{it.title==file.parentFile!!.name};val edition=EditionMetadata.local(p,initial)
  val tracks=initial.files.map{ServiceTrack(it.nameWithoutExtension,libraryTrackSource(it),10000,edition)}
  StableProgress.save(p,initial.title,tracks,0,3456);assertTrue(addBookmark(p,initial.title,0,"01",3456,edition))
  val mark=loadBookmarks(p,initial.title,edition).single();updateBookmark(p,initial.title,mark,"Keep","Keep note")
  val shelves=listOf(Shelf("Keep",listOf(BookRef(initial.title,"",""))));saveShelves(p,shelves);saveQueue(p,listOf(initial.title))
  assertTrue(LibraryRoots.add(p,child));assertEquals(1,scanLibrary(c).count{it.title==initial.title})
  assertTrue(LibraryRoots.remove(p,parent));val changed=scanLibrary(c).single{it.title==initial.title}
  assertEquals(initial.bookId,changed.bookId);assertEquals(edition,EditionMetadata.local(p,changed));assertNotEquals(initial.folderUri,changed.folderUri)
  assertEquals(StableProgress.Resume(0,3456),StableProgress.resume(p,changed.title,changed.files.map{ServiceTrack(it.nameWithoutExtension,libraryTrackSource(it),10000,edition)}))
  assertEquals("Keep note",loadBookmarks(p,changed.title,edition).single().note);assertEquals(shelves,loadShelves(p));assertEquals(listOf(initial.title),loadQueue(p))
  assertTrue(file.exists());assertEquals(2048L,file.length())
  val manifest=LibraryBackup.manifest(c,listOf(changed),false).toString();assertFalse(manifest.contains(LibraryRoots.KEY));assertFalse(manifest.contains(parent))
 }
 @Test fun javaHashCollisionDocumentsStaySeparateAndSameNamedEditionsRemainGuarded() {
  val parent=File(data,"collisions");audio(parent,"Aa");audio(parent,"BB")
  val ta=tree(parent);p.edit().putString(LibraryRoots.KEY,LibraryRoots.encode(listOf(ta))).commit()
  val collision=scanLibrary(c).filter{it.title=="Aa"||it.title=="BB"}
  assertEquals(2,collision.size);assertEquals(2,collision.map{it.folder.path}.distinct().size)
  val one=File(data,"same-one");val two=File(data,"same-two");val name="Same-${c.id}";audio(one,name);audio(two,name)
  LibraryRoots.add(p,tree(one));LibraryRoots.add(p,tree(two))
  val same=scanLibrary(c).filter{it.title==name};assertEquals(2,same.size);assertTrue(same.all{it.identityAmbiguous});assertEquals(2,same.map{it.bookId}.distinct().size)
 }
 private fun grant(uri:Uri,revoke:Boolean=false) {
  val pkg=InstrumentationRegistry.getInstrumentation().context.packageName
  app.startActivity(Intent().setComponent(ComponentName(pkg,"com.audiobookdownloader.SharedAudioFixtureSenderActivity"))
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target",app.packageName).putParcelableArrayListExtra("uris",arrayListOf(uri)).putExtra("revoke",revoke)
   .putExtra("flags",Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
  val end=System.currentTimeMillis()+5000
  while(System.currentTimeMillis()<end){val allowed=app.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==0;if(allowed!=revoke)return;Thread.sleep(25)}
  fail("Grant did not change to requested state")
 }
 @Test fun realReadOnlyPersistentGrantSurvivesReconnectAndRevocationDoesNotClearTheOtherRoot() {
  val pkg=InstrumentationRegistry.getInstrumentation().context.packageName
  val a=Docs.buildTreeDocumentUri("$pkg.library_roots","read-only");val b=Docs.buildTreeDocumentUri("$pkg.library_roots","other")
  grant(a);grant(b)
  try {
   assertTrue(LibraryRoots.connect(c,a));assertTrue(LibraryRoots.connect(c,b));assertTrue(LibraryRoots.connect(c,a))
   assertEquals(listOf(a.toString(),b.toString()),LibraryRoots.load(c).roots)
   assertTrue(app.contentResolver.persistedUriPermissions.any{it.uri==a&&it.isReadPermission&&!it.isWritePermission})
   assertEquals(2,scanLibrary(c).count{it.title=="Grant read-only"||it.title=="Grant other"})
   app.contentResolver.releasePersistableUriPermission(a,Intent.FLAG_GRANT_READ_URI_PERMISSION);grant(a,true)
   val report=scanLibraryReport(c);assertTrue(report.folderUnavailable);assertTrue(report.books.any{it.title=="Grant other"});assertFalse(report.books.any{it.title=="Grant read-only"})
   assertEquals(2,LibraryRoots.load(c).roots.size)
   assertTrue(LibraryRoots.disconnect(c,b.toString()));assertTrue(app.contentResolver.persistedUriPermissions.any{it.uri==b&&it.isReadPermission})
  }finally {
   for(uri in listOf(a,b)){try{app.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};grant(uri,true)}
  }
 }
 private fun failing(base:SharedPreferences,fail:()->Boolean):SharedPreferences {
  return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,m,args->
   if(m.name!="edit")m.invoke(base,*(args?:emptyArray())) else {
    val edit=base.edit()
    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,method,values->
     if(method.name=="commit"&&fail()){edit.commit();false} else {val result=method.invoke(edit,*(values?:emptyArray()));if(result is SharedPreferences.Editor)proxy else result}
    }
   }
  } as SharedPreferences
 }
 @Test fun failedCommitDoesNotBecomeSuccessJustBecausePreferencesMemoryChanged() {
  val a=tree(File(data,"a"));val b=tree(File(data,"b"));p.edit().putString(LibraryRoots.KEY,LibraryRoots.encode(listOf(a))).commit()
  var fail=false;val wrapped=failing(p){fail}
  assertEquals(listOf(a),LibraryRoots.load(wrapped).roots);fail=true;assertFalse(LibraryRoots.add(wrapped,b))
  val pending=LibraryRoots.load(wrapped);assertFalse(pending.saved);assertEquals(listOf(a),pending.roots)
  fail=false;assertEquals(listOf(a,b),LibraryRoots.load(wrapped).roots);assertTrue(LibraryRoots.load(wrapped).saved)
 }
 @Test fun originsUseOneFirstWriterAcrossConcurrentScansAndRejectDamagedMappings() {
  val authority=app.packageName+".deletion-fixture";val first=Docs.buildDocumentUriUsingTree(Docs.buildTreeDocumentUri(authority,"root"),"root/book")
  val second=Docs.buildDocumentUriUsingTree(Docs.buildTreeDocumentUri(authority,"root/book"),"root/book")
  val a=LibraryRootOrigins(c);val b=LibraryRootOrigins(c)
  assertEquals(first.toString(),a.origin(first));assertEquals(first.toString(),b.origin(second));assertTrue(b.save());assertTrue(a.save())
  assertEquals(first.toString(),LibraryRootOrigins(c).origin(second))
  val prefs=c.getSharedPreferences("audiobook_library_origins_v1",0);val key=ChapterIdentity.hash(LibraryRootRules.documentKey(first.toString()))
  prefs.edit().putString(key,"content://another/tree/root/document/unrelated").commit()
  try{LibraryRootOrigins(c).origin(second);fail()}catch(_:IllegalArgumentException){}
  assertEquals("content://another/tree/root/document/unrelated",prefs.getString(key,null))
 }
}
