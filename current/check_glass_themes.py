from workspace_paths import WORKSPACE
from widget_check_support import baseline_bytes
"""Five-wallpaper integration and center-text contrast checks; not an Android rendering test."""
from pathlib import Path
import re
import struct

root = Path(__file__).parent
base = (WORKSPACE / 'audiobook-txt-reports')
s = (root / 'MainActivity.kt').read_text()
old = (base / 'MainActivity.kt').read_text()

def section(text, start, end):
    i = text.index(start)
    return text[i:text.index(end, i)]

themes = section(s, 'private val AbGlassThemes', 'private fun abTheme(')
entries = themes.split('AbTheme(')[1:]
assert len(entries) == 9
assert [re.match(r'(\d),"([^"]+)"', e).groups() for e in entries] == [
    ('1', 'Перламутр'), ('2', 'Ледяная ночь'), ('3', 'Сиреневые сумерки'),
    ('4', 'Янтарь'), ('5', 'Изумруд'),('6','Чёрный обсидиан'),('7','Чёрный оникс'),('8','Чёрный лёд'),('9','Чёрный аметист')]
assert not re.search(r'R\.drawable\.bg_(night|nature|light|tropics)', s)
assert 'listOf("Ночь","Природа","Премиум","Светлая","Тропики")' not in s
assert 'AbThemePicker(data.skin,actions.skin)' in s

# Photo picking, persistable URI grants and independent reset remain byte-for-byte intact.
for start, end in [
    ('    var customBackgroundUri by remember', '    var appPage by'),
    ('        chooseBackground = {', '    val modernDurations'),
    ('private fun AudioBookRoot(', '// A committed marker survives abrupt process termination.'),
    ('private object AbDiagnosticReportFiles', '// Public bibliographic metadata'),
]:
    assert section(s, start, end) == section(old, start, end), start
skin = section(s, '    fun setAppSkin(', '    var customBackgroundUri')
assert 'custom_background_uri' not in skin and 'clearBackground' not in skin
assert 'putInt("glass_theme", appSkin)' in skin
assert 'ColorMatrix' not in s and 'abSceneryFilter' not in s
assert 'Найдите\\nсвою историю' not in s
assert '.clickable(onClick=submit)' in s and 'onSearch={submit?.invoke()}' in s
assert 'AbDialog("Источники поиска"' in s and 'AbBookSources.searchOrder.forEach' in s
assert 'isAppearanceLightStatusBars=theme.light' in s
assert 'isAppearanceLightNavigationBars=theme.light' in s

resources = re.findall(r'R.drawable.(ab_glass_[a-z]+)', themes)
assert len(resources)==len(set(resources))==9
from PIL import Image, ImageStat
import hashlib
hashes=set()
for resource in resources:
    path=root / ('app/src/main/res/drawable-nodpi/'+resource+'.png')
    png=path.read_bytes()
    assert png[:8]==b'\x89PNG\r\n\x1a\n'
    w,h=struct.unpack('>II',png[16:24])
    assert h>w>=720
    hashes.add(hashlib.sha256(png).hexdigest())
    with Image.open(path) as art:
        saturation=ImageStat.Stat(art.convert('HSV')).mean[1]/255
        assert saturation>(.15 if resources.index(resource)<5 else .015),(resource,saturation)
assert len(hashes)==9
for path in (root / 'js-check').glob('*'):
    assert path.read_bytes() == (base / 'js-check' / path.name).read_bytes(), path.name
for relative in ['PlaybackService.kt', 'app/src/main/java/com/SearchBookIndex.java',
                 'app/src/debug/AndroidManifest.xml', 'app/src/release/AndroidManifest.xml',
                 'app/src/main/res/xml/audiobook_diagnostic_paths.xml',
                 'app/src/main/res/values/audiobook_branding.xml', 'rename_launcher.py']:
    assert baseline_bytes(root / relative) == baseline_bytes(base / relative), relative
print('PASS: nine themes with distinct wallpapers, including four black palettes; gallery URI workflow, splash, TXT export, parsers and playback unchanged')

def rgba(value):
    n = int(value, 16)
    return [(n >> 16 & 255) / 255, (n >> 8 & 255) / 255, (n & 255) / 255, (n >> 24 & 255) / 255]

def over(front, back, alpha):
    return [front[k] * alpha + back[k] * (1 - alpha) for k in range(3)] + [1]

def luminance(color):
    return sum((x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4) * weight
               for x, weight in zip(color[:3], [.2126, .7152, .0722]))

def contrast(a, b):
    low, high = sorted([luminance(a), luminance(b)])
    return (high + .05) / (low + .05)

# Numeric check of the central text region at mid-height, where the bright rim
# sheen and radial edge lights do not contribute for a wide control. This does
# not assert contrast over arbitrary artwork, along flares, or for all glyphs.
white, black = [1,1,1,1], [0,0,0,1]
for entry in entries:
    ident,name=re.match(r'(\d),"([^\"]+)"',entry).groups()
    bg,panel,field,accent,text,muted,border,primary,reflection=[
        rgba(x) for x in re.findall(r'Color\(0x([A-F0-9]+)\)',entry)]
    minimum=100.
    for backdrop in (white,black):
        for surface in (panel,field):
            glass=over(surface,backdrop,max(.86,surface[3]-.025))
            minimum=min(minimum,*(contrast(fg,glass) for fg in (text,muted,accent)))
    button=contrast(white,primary)
    assert minimum>=4.5 and button>=4.5,(name,minimum,button)
    print(f'PASS: {name}: center text contrast {minimum:.2f}:1; primary base {button:.2f}:1')
print('Numeric central-region checks only; complete on-device appearance and Kotlin compilation require the Android project.')
