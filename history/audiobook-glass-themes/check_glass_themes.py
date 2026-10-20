"""Integration and conservative numeric palette checks; not an Android rendering test."""
from pathlib import Path
import re
import struct

root = Path(__file__).parent
base = Path('/workspace/audiobook-txt-reports')
s = (root / 'MainActivity.kt').read_text()
old = (base / 'MainActivity.kt').read_text()

def section(text, start, end):
    i = text.index(start)
    return text[i:text.index(end, i)]

themes = section(s, 'private val AbGlassThemes', 'private fun abTheme(')
entries = themes.split('AbTheme(')[1:]
assert len(entries) == 5
assert [re.match(r'(\d),"([^"]+)"', e).groups() for e in entries] == [
    ('1', 'Перламутр'), ('2', 'Ледяная ночь'), ('3', 'Сиреневые сумерки'),
    ('4', 'Янтарь'), ('5', 'Изумруд')]
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
assert 'colorFilter=if(uri==null)filter else null' in s
assert 'isAppearanceLightStatusBars=theme.light' in s
assert 'isAppearanceLightNavigationBars=theme.light' in s

png = (root / 'app/src/main/res/drawable-nodpi/ab_glass_landscape.png').read_bytes()
assert png[:8] == b'\x89PNG\r\n\x1a\n'
w, h = struct.unpack('>II', png[16:24])
assert h > w >= 720
for path in (root / 'js-check').glob('*'):
    assert path.read_bytes() == (base / 'js-check' / path.name).read_bytes(), path.name
for relative in ['PlaybackService.kt', 'app/src/main/java/com/SearchBookIndex.java',
                 'app/src/debug/AndroidManifest.xml', 'app/src/release/AndroidManifest.xml',
                 'app/src/main/res/xml/audiobook_diagnostic_paths.xml',
                 'app/src/main/res/values/audiobook_branding.xml', 'rename_launcher.py']:
    assert (root / relative).read_bytes() == (base / relative).read_bytes(), relative
print('PASS: exactly five themes, new drawable present; gallery URI workflow, splash, TXT export, parsers and playback unchanged')

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

# These are conservative extrema of the static backdrop/surface parameters.
# Use all-black/all-white input and combine maximum reflections, including maxima
# that do not coincide spatially, to check a lower bound on normal-size text contrast.
white, black = [1, 1, 1, 1], [0, 0, 0, 1]
for entry in entries:
    ident, name = re.match(r'(\d),"([^"]+)"', entry).groups()
    light = ident == '1'
    bg, panel, field, accent, text, muted, border, primary, reflection, scenery = [
        rgba(x) for x in re.findall(r'Color\(0x([A-F0-9]+)\)', entry)]
    floors = [100., 100., 100.]
    for photo in (white, black):
        custom = over(bg, over(photo, bg, .72), .82 if light else .74)
        tone = luminance(photo)
        standard = ([.20 * tone + 200 / 255, .19 * tone + 196 / 255, .17 * tone + 189 / 255, 1]
                    if light else [min(1, x * 1.3 * tone) for x in scenery[:3]] + [1])
        standard = over(bg, standard, .20 if light else .74)
        for backdrop in (custom, standard):
            floors[0] = min(floors[0], *(contrast(fg, backdrop) for fg in (text, muted, accent)))
            for surface in (panel, field):
                glass = over(surface, backdrop, max(.84, surface[3] - .035))
                glass = over(white, glass, .22 if light else .07)
                glass = over(reflection, glass, .13)
                floors[1] = min(floors[1], *(contrast(fg, glass) for fg in (text, muted, accent)))
            button = over(primary, backdrop, .965)
            button = over(white, button, .05)
            button = over(reflection, button, .065)
            floors[2] = min(floors[2], contrast(white, button))
    assert min(floors) >= 4.5, (name, floors)
    print(f'PASS: {name}: numeric contrast floors (backdrop / glass / primary) ' +
          ' / '.join(f'{x:.2f}:1' for x in floors))
print('Numeric palette/compositing verification; device appearance and Android compilation still require the full project.')
