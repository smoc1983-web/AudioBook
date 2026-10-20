"""Source contract/regression checks only; not Kotlin/Android execution."""
from pathlib import Path
import re
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
p=(root/'PlaybackService.kt').read_text()
def block(text,a,b):return text[text.index(a):text.index(b,text.index(a))]
request=block(s,'    fun requestModernDownload','    fun cancelModernDownload')
assert request.index('downloadStarts[track.title]=request')<request.index('request.job=modernWork.launch')<request.index('withContext(Dispatchers.IO)')
assert 'catch(error:Exception)' in request and 'trackStates[track.title]=TrackState.ERROR' in request
poll=block(s,'    LaunchedEffect(downloadIds.size)','    // React only to actual changes.')
assert 'statuses==null' in poll and 'downloadStatusUnavailable=true' in poll
assert 'if(misses>=3)' in poll and 'downloadIds[entry.key]!=entry.value' in poll
success=block(poll,'DownloadManager.STATUS_SUCCESSFUL','DownloadManager.STATUS_FAILED')
assert 'downloadRetryTracks.remove' not in success
transfer=block(s,'private fun AbTransferLine','// Shared thumbnails')
assert 'общий размер неизвестен' in transfer and 'item.totalBytes>0' in transfer
assert 'bytes.toDouble()/item.totalBytes' in transfer and 'data.downloadPercent' not in transfer
assert 'CircularProgressIndicator' in transfer and 'Загрузка отменена' in transfer
for token in ['PAUSED_WAITING_FOR_NETWORK','PAUSED_QUEUED_FOR_WIFI','PAUSED_WAITING_TO_RETRY']:
    assert token in block(s,'private fun abTransferPhase','private fun AbTransferLine')
assert s.count('AbBookDownloadFeedback(data,')==2
print('PASS: pending UI state precedes enqueue; preparation/error/waiting/progress/completion are observable, unknown totals are explicit, missing jobs require three successful polls')

sizes=block(s,'private object AbAudioSizes','@Composable\nprivate fun AbBookSize')
for token in ['Semaphore(3)','chunked(3)','async(Dispatchers.IO)','withTimeoutOrNull(20_000L)',
              'connectTimeout=4000','readTimeout=4000','next.size>1024','tried.size>1024',
              'probe(source,page,"HEAD")','probe(source,page,"GET")','"Range","bytes=0-0"',
              'connection.disconnect()','first<=last && last<it','type.contains("html")','Content-Encoding']:
    assert token in sizes,token
assert not re.search(r'inputStream|getInputStream|readBytes|readText',sizes)
render=s[s.index('private fun AbBookSize'):]
assert not re.search(r'\.(length|exists|query|openFileDescriptor)\(',render)
assert 'val audioSizes: Map<String,Long>' in s and s.count('abLocalSize(local)')==3
assert 'cursor.getLong(2)' in s and 'LibraryAudioFile' in s
# The range parser must accept full totals, reject a one-byte Content-Length as full size,
# and reject malformed/unknown totals. Exercise the literal range expression extracted from source.
pattern=re.search(r'val match=Regex\("""(.*?)"""\)',sizes).group(1)
for value,expected in [('bytes 0-0/12345',12345),('bytes 100-199/200',200),('bytes 0-0/*',None),
                       ('bytes 9-1/10',None),('bytes 0-12/10',None),('bytes 0-0/0',None),('garbage/999',None)]:
    match=re.search(pattern,value)
    found=None
    if match:
        first,last,total=map(int,match.groups())
        if first<=last<total:found=total
    assert found==expected,(value,found)
print('PASS: byte metadata bounded/background/header-only; valid range total used; malformed/HTML/compressed responses rejected; UI size rendering does not stat files')

assert 'USAGE_MEDIA' in p and 'CONTENT_TYPE_SPEECH' in p
prepare=block(p,'    private fun prepareTrack','    @Suppress("DEPRECATION")\n    private fun requestFocus')
assert prepare.index('player.setAudioAttributes(mediaAttributes)')<prepare.index('player.setDataSource')
assert 'setPlaybackToLocal(mediaAttributes)' in p
focus=block(p,'    private fun requestFocus','    private fun resumePlayback')
for token in ['Build.VERSION.SDK_INT>=26','setAudioAttributes(mediaAttributes)','abandonAudioFocusRequest',
              'AudioManager.STREAM_MUSIC','AUDIOFOCUS_REQUEST_GRANTED']:assert token in focus
receiver=block(p,'    private val ringerReceiver','    private val noisyReceiver')
assert not re.search('pausePlayback|stopPlayback|resumePlayback|setVolume',receiver)
assert 'volumeControlStream=android.media.AudioManager.STREAM_MUSIC' in s
assert 'ACTION_SOUND_SETTINGS' in s and 'AbSettingsSection.SOUND' in s
for forbidden in ['setRingerMode(', 'setStreamVolume(', 'adjustStreamVolume(', 'setInterruptionFilter(']:
    assert forbidden not in s+p
assert s.count('4.0-glass-vivid-1')==2
print('PASS: player/session/focus use media speech attributes; legacy focus retained; ringer changes only logged; no system sound/DND mode is altered')
print('No Gradle project/Android SDK supplied: no APK compilation, device or Redmi/MIUI runtime validation.')
