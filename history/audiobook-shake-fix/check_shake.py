from pathlib import Path
import re
root=Path(__file__).parent
main=(root/'MainActivity.kt').read_text()
service=(root/'PlaybackService.kt').read_text()
oldmain=Path('/workspace/audiobook-current-crash/MainActivity.kt').read_text()
old=Path('/workspace/audiobook-current-crash/PlaybackService.kt').read_text()
normalized=main.replace('4.0-shake-extend-1','4.0-current-crash-1')
normalized=normalized.replace('''        AbHeading("Продлить таймер встряхиванием",theme)
        Text("Пока работает минутный таймер, встряхните телефон несколько раз: отсчёт начнётся заново с выбранной длительности.",
            color=theme.muted,fontSize=12.sp)''','''        AbHeading("Продление встряхиванием",theme)''')
assert normalized==oldmain
for file in Path('/workspace/audiobook-current-crash/js-check').glob('*.js'):
    assert (root/'js-check'/file.name).read_bytes()==file.read_bytes(),file.name
assert service[:service.index('    private var sleepEndAt')] == old[:old.index('    private var sleepEndAt')]
for start,end in [
    ('    override fun onCreate()', '    private fun parseTracks'),
    ('    private fun startTrack(', '    private fun applySettings'),
    ('    private fun maybeScheduleTimer()', '    private fun updateShakeListener()'),
    ('    private fun publish(', '    override fun onBind')
]:
    assert service[service.index(start):service.index(end)]==old[old.index(start):old.index(end)],start
print('PASS: service API/commands, player, schedule and diagnostics preserved; source parser scripts unchanged')

listener=service[service.index('    private val shakeListener'):service.index('    private val tickRunnable')]
assert 'event.values.size < 3' in listener and '!force.isFinite()' in listener
assert 'if (shakeAboveThreshold) return' in listener
assert listener.index('shakeAboveThreshold = false') < listener.index('strongShakeHits++')
assert 'strongShakeHits >= 3' in listener and '1200L' in listener and '3000L' in listener
assert 'val seconds = sleepRestartSeconds\n                setTimer(seconds, false)' in listener
assert 'last_sleep_seconds' not in listener and '!isPlaying' not in listener
assert 'pausePlayback' not in listener and 'resumePlayback' not in listener
assert 'setTimer(0' not in listener
assert 'sleepEndAt <= 0L' in listener and 'sleepRestartSeconds <= 0' in listener
timer=service[service.index('    private fun setTimer'):service.index('    private fun maybeScheduleTimer')]
assert 'sleepRestartSeconds = if (!chapter && seconds > 0) seconds else 0' in timer
assert 'seconds.toLong() * 1000L' in timer
assert 'updateVolume()' in timer and 'publish()' in timer
assert 'shakeAboveThreshold = false' in timer and 'strongShakeHits = 0' in timer
sensor=service[service.index('    private fun updateShakeListener'):service.index('    private fun releasePlayer')]
assert 'sleepEndAt > 0L' in sensor and 'isPlaying' not in sensor
assert 'Sensor.TYPE_ACCELEROMETER, true' in sensor and '?: sensorManager.getDefaultSensor' in sensor
assert 'SensorManager.SENSOR_DELAY_GAME, handler' in sensor
assert 'sensorManager.unregisterListener(shakeListener)' in sensor
print('PASS: shake restarts the active duration without pausing audio; only timed sleep is eligible')
print('PASS: three distinct finite peaks, faster sensor delivery, wake-up fallback and reset/cooldown guards')
print('Static source checks only. Kotlin compilation and physical sensor events not tested.')
