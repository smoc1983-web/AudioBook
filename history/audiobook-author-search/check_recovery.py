from pathlib import Path
import re,xml.etree.ElementTree as ET
root=Path(__file__).parent
main=(root/'MainActivity.kt').read_text()
service=(root/'PlaybackService.kt').read_text()
manifest=ET.parse(root/'app/src/debug/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
tools='{http://schemas.android.com/tools}'
permissions={node.get(android+'name') for node in manifest.findall('uses-permission')}
assert {'android.permission.WAKE_LOCK','android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'} <= permissions
components={node.get(android+'name'):node for node in manifest.find('application')}
player=components['com.audiobookdownloader.PlaybackService']
assert player.get(android+'foregroundServiceType')=='mediaPlayback'
assert player.get(android+'stopWithTask')=='false' and player.get(android+'exported')=='false'
for name in ['com.itsaky.androidide.logsender.utils.LogSenderInstaller','com.itsaky.androidide.logsender.LogSenderService']:
    assert components[name].get(tools+'node')=='remove'
print('PASS: debug overlay includes wake/media foreground permissions and service configuration; LogSender removals retained')

wake=service[service.index('    private fun updatePlaybackWakeLock'):service.index('    private fun publish')]
assert 'playWhenReady&&(isPlaying||isLoading)' in wake
assert 'PARTIAL_WAKE_LOCK' in wake and 'setReferenceCounted(false)' in wake
assert 'lock.acquire()' in wake and 'lock.release()' in wake
assert 'if(!wanted)' in wake and 'releasePlaybackWakeLock()' in wake
assert 'wakeUnavailable' in wake
publish=service[service.index('    private fun publish'):service.index('    private fun updateSessionState')]
assert 'updatePlaybackWakeLock()' in publish
start=service[service.index('    private fun startTrack'):service.index('    private fun prepareTrack')]
assert start.index('updatePlaybackWakeLock()')<start.index('queueWorker.execute')
destroy=service[service.index('    override fun onDestroy()'):service.index('    override fun onBind')]
assert 'releasePlaybackWakeLock()' in destroy and 'unregisterReceiver(screenReceiver)' in destroy
assert 'val hasSelection:Boolean' in service
assert 'if(!state.hasSelection) item' in main and 'if(!state.hasPlayer) item' not in main
assert 'if(state.hasSelection && data.page' in main
print('PASS: wake held during preparation/playback, released on pause/stop/destruction; UI retains book independently of native player')

send=service[service.index('        private fun send'):service.index('        private fun decodeTracks')]
assert 'startForegroundService' in send and 'running.handleCommand' not in send
restart=service[service.index('    override fun onStartCommand'):service.index('    private fun handleCommand')]
assert restart.index('startForeground(')<restart.index('handleCommand(intent)')
assert 'return START_STICKY' in restart and 'resumeOnRestart&&trackIndex' in restart
assert 'restorePlayWhenReady=false' in restart
assert '.putBoolean("service_should_resume",playWhenReady)' in service
for name,nextname in [('    private fun failPlayback','    private fun stopPlayback'),
                     ('    private fun stopPlayback','    @android.annotation.SuppressLint')]:
    section=service[service.index(name):service.index(nextname)]
    assert section.index('playWhenReady=false')<section.index('persistState()')
assert 'playWhenReady = false' in service[service.index('    private fun pausePlayback'):service.index('    private fun seekPlayback')]
assert 'if (nextBook == null) {\n                    playWhenReady=false' in service
print('PASS: Android receives every command; restart respects play/pause/error/stop and does not replay a completed empty queue')

restore=service[service.index('        suspend fun restoreScreenState'):service.index('        fun command')]
assert 'Dispatchers.IO' in restore and 'Dispatchers.Main' in restore
assert 'selectionRevision.get()==revision' in restore and 'mutableState.value===before' in restore
assert 'startService' not in restore and 'startForeground' not in restore and 'startTrack(' not in restore
assert 'LaunchedEffect(Unit) {PlaybackService.restoreScreenState' in main
assert 'targetBook=book.title' in main
assert 'val target=intent.getStringExtra("target_book")' in service
print('PASS: cold screen restore never starts sound or overwrites newer selection; deletion is scoped to its target book')

receiver=service[service.index('    private val screenReceiver'):service.index('    private val preferenceListener')]
assert 'ACTION_SCREEN_OFF' in receiver and 'ACTION_SCREEN_ON' in receiver
assert 'pausePlayback' not in receiver and 'stopPlayback' not in receiver
assert 'WAKE_LOCK разрешён' in main and 'foregroundServiceType=' in main
assert 'onDestroy сервиса' in service and 'Process.myPid()' in service
assert 'AbCrashReport.install(context)\n        web' in main
assert main.count('4.0-author-search-1')==2
print('PASS: screen transitions/lifecycle/PID recorded; report inspects installed APK; crash handler restored after WebView creation')
print('Static/XML checks, not manifest merge, Kotlin compilation, device sleep or Android lifecycle runtime tests.')
