package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor

class UpdateSessionTest {
    private class Tasks:Executor {
        val work=ArrayDeque<Runnable>()
        override fun execute(command:Runnable){work.add(command)}
        fun finish(){work.remove().run()}
    }
    private class Backend:UpdateBackend {
        var checks=0;var downloads=0;var cancellations=0;var preparations=0
        val release=PreviewUpdater.Release(PreviewUpdateRules(PreviewUpdateRulesTest.valid(),PreviewUpdateRulesTest.TAG))
        var onDownload:((Int)->Unit)->Unit={it(50)}
        var failure:Exception?=null
        override fun latest():PreviewUpdater.Release {checks++;failure?.let{throw it};return release}
        override fun download(release:PreviewUpdater.Release,progress:(Int)->Unit){downloads++;onDownload(progress)}
        override fun prepareInstall():File {preparations++;failure?.let{throw it};return File("/fixture/verified.apk")}
        override fun cancel(){cancellations++}
    }
    private fun session(backend:Backend,tasks:Tasks)=UpdateSession(File("/fixture/update.apk"),{backend},tasks)
    @Test fun detachAndReattachKeepOneOperationAndItsRelease() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        var oldCalls=0;val old=UpdateSession.Listener{oldCalls++}
        session.addListener(old)
        assertTrue(session.check());assertFalse(session.check());assertEquals(1,tasks.work.size)
        session.removeListener(old);val stoppedAt=oldCalls
        tasks.finish()
        assertEquals(stoppedAt,oldCalls);assertEquals(1,backend.checks)
        assertSame(backend.release,session.snapshot().release);assertFalse(session.snapshot().busy)
        var resumed:UpdateUiState?=null
        session.addListener{resumed=session.snapshot()}
        assertSame(backend.release,resumed!!.release);assertEquals(1L,resumed!!.operationId)
    }
    @Test fun downloadSurvivesMissingWindowAndRepeatedClicksDoNotDuplicateIt() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        session.check();tasks.finish()
        assertTrue(session.download());assertFalse(session.download());assertFalse(session.install())
        tasks.finish()
        assertEquals(1,backend.downloads);assertTrue(session.snapshot().message.contains("загружено"))
        assertNull("Download never launches an installer itself",session.claimInstaller())
    }
    @Test fun cancelledDownloadCannotFinishOrRequestAnInstaller() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        session.check();tasks.finish()
        backend.onDownload={progress->session.cancel();progress(100)}
        assertTrue(session.download());tasks.finish()
        assertEquals(1,backend.cancellations);assertFalse(session.snapshot().busy)
        assertTrue(session.snapshot().message.contains("отменена"));assertNull(session.claimInstaller())
        assertTrue(session.check());tasks.finish();assertEquals(2,backend.checks)
    }
    @Test fun installerRequestSurvivesObserverReplacementAndCanBeClaimedOnce() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        assertTrue(session.install());assertFalse(session.install());tasks.finish()
        assertEquals(1,backend.preparations)
        assertEquals(File("/fixture/verified.apk"),session.claimInstaller());assertNull(session.claimInstaller())
    }
    @Test fun failedVerificationNeverOffersInstallerAndAllowsFreshCheck() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        backend.failure=IOException("Подпись APK отличается")
        session.install();tasks.finish()
        assertTrue(session.snapshot().rejectedCachedApk);assertNull(session.claimInstaller())
        assertEquals("Подпись APK отличается",session.snapshot().message)
        backend.failure=null;assertTrue(session.check());tasks.finish();assertNotNull(session.snapshot().release)
    }
    @Test fun cancelBeforeWorkerStartsDoesNotContactServer() {
        val backend=Backend();val tasks=Tasks();val session=session(backend,tasks)
        session.check();session.cancel();assertFalse(session.check());tasks.finish()
        assertEquals(0,backend.checks);assertFalse(session.snapshot().busy)
        assertTrue(session.snapshot().message.contains("отменена"))
    }
    @Test fun contextualActionUsesCacheButStillRequiresVerificationAndRejectsBadCache() {
        val backend=Backend();val tasks=Tasks();val target=File.createTempFile("update-ui-",".apk")
        try {
            val session=UpdateSession(target,{backend},tasks)
            assertEquals("Установить загруженное обновление",session.primaryLabel())
            backend.failure=IOException("Повреждённый APK")
            assertTrue(session.primary());tasks.finish();assertNull(session.claimInstaller())
            assertEquals("Проверить обновления",session.primaryLabel())
            backend.failure=null;assertTrue(session.primary());tasks.finish()
            assertEquals("Загрузить обновление",session.primaryLabel())
            assertTrue(session.primary());tasks.finish();assertEquals(1,backend.downloads)
        }finally {target.delete()}
    }
}
