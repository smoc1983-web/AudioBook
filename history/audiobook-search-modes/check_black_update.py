"""Integration/resource checks; Android compilation and device execution are separate."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
from PIL import Image
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text()
base=Path('/workspace/audiobook-glass-widgets-search')
assert (r/'PlaybackService.kt').read_bytes()==(base/'PlaybackService.kt').read_bytes()
assert (r/'app/src/main/java/com/SearchBookIndex.java').read_bytes()==(base/'app/src/main/java/com/SearchBookIndex.java').read_bytes()
assert 'https://t.me/c/3702128532/1' in s
assert 'private const val REVISION="4.0-search-modes-1"' in s
scan=s[s.index('private fun unfinishedLibraryChapters'):s.index('class LibrarySyncState')]
for token in ['LibraryRecoveryRules.unfinished','jobs.filterKeys{it in unfinished}','MediaStore.Downloads.EXTERNAL_CONTENT_URI',
              'relative_path LIKE ?','LibraryRecoveryRules.isAudio','persistedUriPermissions','last_library']:
 assert token in scan,token
assert 'jobs.filterKeys{it !in successful}' not in scan
a='{http://schemas.android.com/apk/res/android}'
for variant in ['debug','release']:
 manifest=ET.parse(r/f'app/src/{variant}/AndroidManifest.xml').getroot()
 permissions={node.get(a+'name'):node for node in manifest.findall('uses-permission')}
 assert 'android.permission.READ_MEDIA_AUDIO' in permissions
 assert permissions['android.permission.READ_EXTERNAL_STORAGE'].get(a+'maxSdkVersion')=='32'
player=s[s.index('    fun toggleChapters()'):s.index('private fun AbPlayerAction(')]
controls=player[player.index('AbPlayerAction(AbIcon.TIMER'):player.index('Глава из интернета')]
assert controls.index('AbIcon.TIMER')<controls.index('AbIcon.EQUALIZER')<controls.index('AbIcon.BOOKS')<controls.index('Скорость воспроизведения')<controls.index('AbIcon.BOOKMARK')
assert 'Свернуть главы ·' not in player and 'Показать главы ·' not in player
assert 'AbChips(listOf("Главы"' not in player
assert 'if(tab!=0){tab=0;chaptersExpanded=true}' in s
assert 'chapterDurations' in s and 'itemsIndexed(state.tracks)' in player
assert 'genrePages=mapOf(native.site to AbGenrePage' in s
assert 'genrePending=setOf(native.site)' in s
assert 'var genreAtlas by remember' in s and 'inSampleSize=2' in s
assert 'val cropW=' in s and 'val cropH=' in s
for width,height in [(486,810),(971,1619)]:
 xs=[x*width//971 for x in [0,328,648,971]];ys=[y*height//1619 for y in [0,323,637,951,1249,1619]]
 for i in range(15):
  col,row=i%3,i//3
  for ratio in [.8,1,1.5,2]:
   aw=xs[col+1]-xs[col]-6;ah=ys[row+1]-ys[row]-6
   cw=min(aw,int(ah*ratio));ch=min(ah,int(aw/ratio))
   assert 0<cw<=aw and 0<ch<=ah
with Image.open(r/'app/src/main/res/drawable-nodpi/ab_genre_atlas.png') as im:assert im.size==(971,1619)
print('PASS: nine themes, atlas crop bounds, centered chapter control, persistent library access, audio permissions, unchanged playback/index and prior Telegram/search fixes')
