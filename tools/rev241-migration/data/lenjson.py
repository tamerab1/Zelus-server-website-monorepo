"""Lenient (Gson-style) JSON scanner: //, #, /* */ comments, unquoted keys/values,
trailing commas, single-quoted strings. Yields every integer token with its byte span and
key path so a rewrite can patch numbers in place without touching formatting."""
import re

NUM = re.compile(r'-?\d+(\.\d+)?([eE][-+]?\d+)?')
BARE = re.compile(r'[A-Za-z_$][\w$.\-]*')


def scan(text):
    """Returns list of (start, end, value:int, path:str, is_key:bool, in_string:bool)."""
    out = []
    i, n = 0, len(text)
    stack = []  # each: ['obj'|'arr', current_key, expecting_key]
    pending_key = None

    def cur_path():
        parts = []
        for fr in stack:
            if fr[0] == 'arr':
                parts.append('[]')
            else:
                k = fr[1]
                parts.append('.' + ('{n}' if k is not None and re.fullmatch(r'-?\d+', k) else str(k)))
        return ''.join(parts)

    def value_slot():
        # the frame now holds a value; for objects, the next token is a key again
        if stack and stack[-1][0] == 'obj':
            stack[-1][2] = True

    while i < n:
        c = text[i]
        if c in ' \t\r\n,':
            if c == ',' and stack and stack[-1][0] == 'obj':
                stack[-1][2] = True
            i += 1
            continue
        if c == '/' and text.startswith('//', i) or c == '#':
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if c == '{':
            stack.append(['obj', None, True])
            i += 1
            continue
        if c == '[':
            stack.append(['arr', None, False])
            i += 1
            continue
        if c in '}]':
            stack.pop()
            value_slot()
            i += 1
            continue
        if c == ':' or c == '=':
            if stack and stack[-1][0] == 'obj':
                stack[-1][2] = False
            i += 1
            continue
        expecting_key = bool(stack) and stack[-1][0] == 'obj' and stack[-1][2]
        if c in '"\'':
            j = i + 1
            buf = []
            while j < n and text[j] != c:
                if text[j] == '\\':
                    j += 1
                buf.append(text[j])
                j += 1
            s = ''.join(buf)
            if expecting_key:
                stack[-1][1] = s
                if re.fullmatch(r'-?\d+', s):
                    out.append((i + 1, j, int(s), cur_path() + '{KEY}', True, True))
            else:
                if re.fullmatch(r'-?\d+', s):
                    out.append((i + 1, j, int(s), cur_path() + '(str)', False, True))
                value_slot()
            i = j + 1
            continue
        mnum = NUM.match(text, i)
        if mnum and (c.isdigit() or c == '-'):
            tok = mnum.group(0)
            if expecting_key:
                stack[-1][1] = tok
                out.append((i, mnum.end(), int(tok), cur_path() + '{KEY}', True, False))
            else:
                if mnum.group(1) is None and mnum.group(2) is None:
                    out.append((i, mnum.end(), int(tok), cur_path(), False, False))
                value_slot()
            i = mnum.end()
            continue
        mb = BARE.match(text, i)
        if mb:
            if expecting_key:
                stack[-1][1] = mb.group(0)
            else:
                value_slot()
            i = mb.end()
            continue
        raise ValueError('unexpected %r at %d' % (c, i))
    return out
