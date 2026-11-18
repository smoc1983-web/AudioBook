package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*

/** Assertions finish before cleanup; ActivityScenario.close must not wait on a live UI ticker. */
internal fun finishPlaybackFixture(c:Context) {
    PlaybackService.stop(c,true)
    try {runBlocking {withTimeout(20000){while(PlaybackService.state.value.hasSelection||PlaybackService.state.value.isPlaying)delay(50)}}}
    catch(e:TimeoutCancellationException) {
        android.util.Log.e("PlaybackFixture", "Main thread at cleanup timeout:\n"+android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
        throw e
    }
    c.stopService(Intent(c,PlaybackService::class.java))
    // runOnMainSync posts an ordinary lifecycle action; unlike close(), it does not
    // first wait for an IdleHandler while the playback screen is still resumed.
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().toList().forEach{it.finish()}
    }
}
