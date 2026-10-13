"""Parse the Mimikara N3 vocabulary sheet (Google Sheets PDF export) using word positions.

Layout: one page, a header row "Bài N <category> <count>", then a column-header row with
blocks of (STT, Kanji, [Hiragana,] Meaning); each lesson spans one or more blocks.
Rows are anchored on the STT number of each block; other words go to the nearest STT row.
"""
import re, sys, json, html
from collections import defaultdict

bbox, raw, out = sys.argv[1], sys.argv[2], sys.argv[3]
src = open(bbox, encoding='utf-8').read()
words = [dict(x0=float(a), y0=float(b), x1=float(c), y1=float(d), t=html.unescape(t)) for a, b, c, d, t in
         re.findall(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>', src)]
for w in words:
    w['yc'] = (w['y0'] + w['y1']) / 2

hdr_y = min(w['y0'] for w in words if w['t'] == 'STT')
headers = sorted([w for w in words if abs(w['y0'] - hdr_y) < 0.3 and w['t'] in ('STT', 'Kanji', 'Hiragana', 'Meaning')], key=lambda w: w['x0'])
blocks = []
for w in headers:
    if w['t'] == 'STT':
        blocks.append({'x': w['x0'], 'cols': [('STT', w['x0'])]})
    else:
        blocks[-1]['cols'].append((w['t'], w['x0']))
for i, b in enumerate(blocks):
    b['end'] = blocks[i + 1]['x'] if i + 1 < len(blocks) else 1e9

def block_index(x):
    for i, b in enumerate(blocks):
        if b['x'] - 1.0 <= x < b['end'] - 1.0:
            return i
    return None

def col_for(b, x):
    name = b['cols'][0][0]
    for n, cx in b['cols']:
        if x >= cx - 1.0:
            name = n
    return name

# Lessons: text of the first raw line, positions of the "Bài" words.
first = open(raw, encoding='utf-8').readline()
declared = [(int(n), cat.strip(), int(c)) for n, cat, c in re.findall(r'Bài (\d+):?\s+(.+?)\s+(\d+)(?=\s+Bài|\s*$)', first.strip())]
bai = sorted([w for w in words if w['t'] == 'Bài' and w['y0'] < hdr_y], key=lambda w: w['x0'])
assert len(bai) == len(declared), (len(bai), declared)
lessons = []
for (num, cat, count), w in zip(declared, bai):
    lessons.append({'number': num, 'category': cat, 'declared': count, 'first_block': block_index(w['x0'])})
block_lesson = {}
for i, l in enumerate(lessons):
    last = lessons[i + 1]['first_block'] - 1 if i + 1 < len(lessons) else len(blocks) - 1
    for b in range(l['first_block'], last + 1):
        block_lesson[b] = l['number']

body = [w for w in words if w['y0'] > hdr_y + 0.3 and block_index(w['x0']) is not None]
anchors = defaultdict(list)  # block -> STT words
for w in body:
    bi = block_index(w['x0'])
    if col_for(blocks[bi], w['x0']) == 'STT' and w['t'].isdigit():
        anchors[bi].append(w)

entries, stray = [], []
cells = defaultdict(lambda: defaultdict(list))
for w in body:
    bi = block_index(w['x0'])
    col = col_for(blocks[bi], w['x0'])
    if col == 'STT' and w['t'].isdigit():
        continue
    if not anchors[bi]:
        stray.append(w); continue
    a = min(anchors[bi], key=lambda s: abs(s['yc'] - w['yc']))
    if abs(a['yc'] - w['yc']) > 0.7:  # rows are ~1.2 units apart
        stray.append(dict(w, block=bi, col=col)); continue
    cells[(bi, a['t'])][col].append(w)

for bi in sorted(anchors):
    for a in sorted(anchors[bi], key=lambda s: s['yc']):
        c = cells[(bi, a['t'])]
        txt = lambda n: ' '.join(w['t'] for w in sorted(c.get(n, []), key=lambda w: w['x0']))
        entries.append({'lesson': block_lesson[bi], 'block': bi, 'stt': int(a['t']), 'kanji': txt('Kanji'),
                        'hiragana': txt('Hiragana'), 'meaning': txt('Meaning'),
                        'block_has_hiragana': any(n == 'Hiragana' for n, _ in blocks[bi]['cols'])})

json.dump({'lessons': lessons, 'entries': entries, 'stray': [w['t'] for w in stray]},
          open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('words', len(words), 'blocks', len(blocks), 'lessons', len(lessons), 'rows', len(entries), 'stray words', len(stray))
