from pathlib import Path
import re
root=Path(__file__).parent
main=(root/'MainActivity.kt').read_text()
service=(root/'PlaybackService.kt').read_text()
old_service=Path('/workspace/audiobook-shake-fix/PlaybackService.kt').read_text()

# Preserve the latency fix; the additional provider query only filters partial downloads during an IO scan.
checks=Path('/workspace/audiobook-anr-fix/check_anr.py').read_text()
checks=checks[:checks.index('# No source parsers changed')].replace("assert s.count('manager.query(')==1","assert s.count('manager.query(')==3")
exec(checks,{'__file__':str(root/'check_anr.py')})
resolver=main[main.index('fun preferDownloadedTracks'):main.index('fun libraryFileForTrack')]
assert not re.search(r'\.(exists|isFile|listFiles|length|query|openConnection)\(',resolver)
assert 'singleOrNull()' in resolver and 'libraryTrackSource(local)' in resolver
assert 'track.copy(src=libraryTrackSource(local))' in resolver
assert 'if(!isNetworkAudio(track.src))track' in resolver
assert 'tracks.map' in resolver
print('PASS: local resolution preserves playlist ordering, metadata and existing content/file addresses; no disk/network work in UI resolver')

start=service[service.index('    private fun startTrack('):service.index('    private fun prepareTrack')]
assert 'val requestedTitle=bookTitle' in start and 'val requestedTracks=playlist' in start
assert start.index('queueWorker.execute')<start.index('scanLibrary(this)')<start.index('handler.post')
assert start.index('if(stopped||token!=generation)')<start.index('playlist=resolved')<start.index('prepareTrack(playlist')
assert 'else prepareTrack(item,token)' in start
assert 'persistPlaylist()' in start
assert service.count('MediaPlayer()')==1
print('PASS: restored URL playlists resolved on worker; obsolete scans cannot replace a newer/stopped playlist; local-only requests skip scan')

player=service[service.index('    private fun prepareTrack'):service.index('    @Suppress("DEPRECATION")\n    private fun requestFocus')]
assert 'player.setDataSource(this, android.net.Uri.parse(item.src))' in player
assert 'else player.setDataSource(item.src)' in player
assert 'if (playWhenReady) resumePlayback() else publish()' in player
assert 'setOnInfoListener' in player and 'MEDIA_INFO_BUFFERING_START' in player
assert 'sleepEndAt' not in player
print('PASS: Android content URI handling and play/pause during preparation retained; buffering is observable')

for a,b in [('    private fun remainingSleepSeconds()', '    private fun setTimer'),
            ('    private fun maybeScheduleTimer()', '    private fun updateShakeListener()')]:
    assert service[service.index(a):service.index(b)]==old_service[old_service.index(a):old_service.index(b)],a
shake=service[service.index('    private val shakeListener'):service.index('    private val tickRunnable')]
assert '3.24f' in shake and 'strongShakeHits >= 3' in shake and 'shakeAboveThreshold' in shake
assert '1200L' in shake and '3000L' in shake and 'val seconds = sleepRestartSeconds' in shake
assert 'pausePlayback' not in shake and 'resumePlayback' not in shake
sensor=service[service.index('    private fun updateShakeListener'):service.index('    private fun releasePlayer')]
assert 'Sensor.TYPE_ACCELEROMETER, true' not in sensor
assert 'SensorManager.SENSOR_DELAY_NORMAL' in sensor and 'isPlaying' in sensor
assert 'seconds.toLong() * 1000L' in service
assert 'last_sleep_seconds' not in shake
print('PASS: timer units, schedule and fade preserved; active duration and sensitive threshold retained; sensor mode restored')

scan=main[main.index('private fun unfinishedLibraryChapters'):main.index('class LibrarySyncState')]
assert 'STATUS_SUCCESSFUL' in scan and 'cursor.getColumnIndexOrThrow' in scan and 'cursor ->' in scan
assert 'ids.toLongArray()' in scan and '.chunked(128)' in scan
assert 'val unfinished=unfinishedLibraryChapters(context)' in scan
assert 'filter{it.files.isNotEmpty()}' in scan
print('PASS: running/paused/failed partial MP3s filtered from complete downloads; successful jobs remain playable')

assert 'takeLast(79)' in main and 'Последние события плеера' in main
assert 'playbackNote' in main and 'playbackNote' in service
for old in Path('/workspace/audiobook-search-recovery/js-check').glob('*.js'):
    if old.name not in {'analyzer.js','external-page.js','catalog.js','catalog-template.js','genre-template.js','classifier.js','genre-link.js','public-classifier.js','public-search.js','player-reader.js'}:
        assert (root/'js-check'/old.name).read_bytes()==old.read_bytes(),old.name
print('PASS: bounded playback trace included in report; Azbyka/player scripts preserved; player and catalog-link roles deliberately updated')
print('Structural regression checks only. Android build and runtime playback not tested.')
