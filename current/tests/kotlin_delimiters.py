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
