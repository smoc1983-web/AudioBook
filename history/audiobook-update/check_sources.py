from pathlib import Path

ROOT = Path(__file__).parent

def check_delimiters(text):
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    i = 0
    def skip_comment(pos):
        if text.startswith('//', pos):
            end = text.find('\n', pos)
            return len(text) if end < 0 else end
        level = 1
        pos += 2
        while pos < len(text) and level:
            if text.startswith('/*', pos): level += 1; pos += 2
            elif text.startswith('*/', pos): level -= 1; pos += 2
            else: pos += 1
        assert level == 0, 'Unterminated comment'
        return pos
    def skip_string(pos):
        if text.startswith('"""', pos):
            end = text.find('"""', pos + 3)
            assert end >= 0, 'Unterminated raw string'
            return end + 3
        quote = text[pos]
        pos += 1
        while pos < len(text):
            if text[pos] == '\\': pos += 2
            elif text[pos] == quote: return pos + 1
            elif quote == '"' and text.startswith('${', pos):
                pos = skip_template(pos + 2)
            else: pos += 1
        raise AssertionError('Unterminated string')
    def skip_template(pos):
        depth = 1
        while pos < len(text):
            if text.startswith('//', pos) or text.startswith('/*', pos): pos = skip_comment(pos)
            elif text[pos] in '\"\'': pos = skip_string(pos)
            elif text[pos] == '{': depth += 1; pos += 1
            elif text[pos] == '}':
                depth -= 1
                pos += 1
                if depth == 0: return pos
            else: pos += 1
        raise AssertionError('Unterminated template')
    while i < len(text):
        if text.startswith('//', i) or text.startswith('/*', i): i = skip_comment(i)
        elif text[i] in '\"\'': i = skip_string(i)
        elif text[i] == '`':
            i = text.index('`', i + 1) + 1
        else:
            char = text[i]
            if char in '([{': stack.append((char, i))
            elif char in pairs:
                line = text.count('\n', 0, i) + 1
                assert stack and stack[-1][0] == pairs[char], f'Mismatched {char} at line {line}'
                stack.pop()
            i += 1
    assert not stack, f'Unclosed delimiter at {stack[-1]}'

main = (ROOT / 'MainActivity.kt').read_text()
service = (ROOT / 'PlaybackService.kt').read_text()
for name, text in [('MainActivity.kt', main), ('PlaybackService.kt', service)]:
    check_delimiters(text)
    print(name + ': delimiters and strings OK')

assert 'MediaPlayer()' not in main
assert service.count('MediaPlayer()') == 1
assert 'PlaybackBridge' not in main + service
assert 'audioFx.attach(' not in main
assert 'onDispose' not in service  # service has no Compose lifecycle dependency
assert 'PlaybackService.state.collectAsState()' in main
assert 'sleepSeconds = state.sleepSeconds' in main
assert 'queueWorker.execute' in service
assert 'audioFx.attach(player.audioSessionId' in service
for action in ['TOGGLE', 'PREVIOUS', 'NEXT', 'REWIND', 'FORWARD']:
    assert f'const val ACTION_{action} =' in service
print('Playback ownership and widget action compatibility: OK')

# Validate every operation remaining in the screen against the controller API.
import re
operations = set(re.findall(r'\b(?:mp|local|online|localPlayback|onlinePlayback)\??\.(\w+)', main))
allowed = {'currentPosition', 'duration', 'isPlaying', 'start', 'pause', 'stop', 'release', 'seekTo'}
assert operations <= allowed, f'Unexpected controller operations: {operations - allowed}'
print('Screen controller operations: OK')

ORIGINAL = Path('/tmp/codex-remote-attachments/01a11f34-0b0d-7318-99b8-868fecab80da/2b38b8fe-b622-48de-bdbe-8d11d72641c0/2-MainActivity.kt').read_text()
# Unrelated parsers, downloads, image loading, library scanning and formatting stay byte-identical.
marker = '@Composable\nfun FullPlayerCover('
assert main[main.index(marker):].rstrip() == ORIGINAL[ORIGINAL.index(marker):].rstrip()
print('Unrelated helpers and download code: unchanged')
