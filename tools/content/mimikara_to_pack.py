"""Normalize the parsed Mimikara N3 vocabulary sheet and build a candidate content pack.

Input: JSON from mimikara_parse_pdf.py. Output (to a directory outside Git):
  normalized.csv       every row of the file with its status and issues
  pack-candidate.json  n3sensei-content-pack v1 with the importable rows
  report.json          counts and issues for the import report

Rules (see docs/CONTENT_FORMAT.md): text is kept as in the file apart from trimming and
collapsing whitespace; nothing is translated, no furigana or part of speech is invented.
"""
import csv, json, re, sys, unicodedata
from collections import Counter

parsed, out_dir = sys.argv[1], sys.argv[2]
d = json.load(open(parsed, encoding='utf-8'))

KANA = re.compile(r'^[぀-ゟ゠-ヿー・]+$')
KATAKANA = re.compile(r'^[゠-ヿー・]+$')
HAS_KANJI = re.compile(r'[一-鿿々]')

def clean(s):
    return re.sub(r'\s+', ' ', unicodedata.normalize('NFC', s or '')).strip()

# Corrections of errors in the source sheet, decided by the project owner (13/10/2026).
# Each one was checked against a rendering of the PDF: the error is in the sheet itself.
CORRECTIONS = {
    90: {'reading': ('作者', 'さくしゃ'), 'why': 'Ô cách đọc ghi chữ Hán "作者" thay vì kana'},
    750: {'reading': ('すませる・済ます', 'すませる・すます'), 'why': 'Cách đọc dạng thứ hai ghi chữ Hán "済ます"'},
    796: {'word': ('パートナ', 'パートナー'), 'why': 'Từ thiếu trường âm, cách đọc trong bảng là パートナー'},
}

rows, kept = [], []
for e in d['entries']:
    word, reading, meaning = clean(e['kanji']), clean(e['hiragana']), clean(e['meaning'])
    issues, transforms, status = [], [], 'ok'
    fix = CORRECTIONS.get(e['stt'])
    if fix:
        for field in ('word', 'reading'):
            if field in fix:
                old, new = fix[field]
                current = word if field == 'word' else reading
                assert current == old, (e['stt'], field, current, old)  # the sheet changed: re-check by hand
                if field == 'word': word = new
                else: reading = new
                transforms.append(f'CORRECTED_{field.upper()}')
    if not word:
        issues.append('MISSING_WORD'); status = 'rejected'
    if not meaning:
        issues.append('MISSING_MEANING'); status = 'rejected'
    if not reading:
        if not e['block_has_hiragana'] and KATAKANA.match(word):
            reading = word
            transforms.append('READING_FROM_KATAKANA_WORD')  # the file has no reading column for this lesson
        elif KANA.match(word):
            reading = word
            transforms.append('READING_FROM_KANA_WORD')  # accepted by the owner
        else:
            issues.append('MISSING_READING'); status = 'rejected'
    elif not KANA.match(reading):
        issues.append('READING_NOT_KANA'); status = 'rejected'
    if word and reading and not HAS_KANJI.search(word) and word != reading and status == 'ok':
        issues.append('WORD_READING_DIFFER'); status = 'needs_confirmation'
    rows.append(dict(stt=e['stt'], lesson=e['lesson'], word=word, reading=reading, meaning=meaning,
                     status=status, issues=';'.join(issues), transforms=';'.join(transforms)))

# Same word + reading more than once: keep both, flag (they may be different senses).
pairs = Counter((r['word'], r['reading']) for r in rows if r['status'] != 'rejected')
for r in rows:
    if r['status'] != 'rejected' and pairs[(r['word'], r['reading'])] > 1:
        r['transforms'] = ';'.join(filter(None, [r['transforms'], 'SAME_WORD_AND_READING_KEPT']))

with open(f'{out_dir}/normalized.csv', 'w', newline='', encoding='utf-8') as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader(); w.writerows(rows)

lessons = d['lessons']
pack = {
    'format': 'n3sensei-content-pack', 'formatVersion': 1,
    'pack': {'id': 'mimikara-n3-vocab', 'title': 'Từ vựng Mimikara N3 (880 từ)', 'version': 1,
             'jlptLevel': 'N3', 'defaultSourceId': 'mimikara-n3-sheet'},
    'sources': [{'id': 'mimikara-n3-sheet',
                 'name': 'Bảng "880 từ vựng Mimikara N3" (Google Sheets, chia sẻ bởi VTI Mirai), theo giáo trình Mimikara Oboeru N3',
                 'url': None, 'license': 'Chưa xác định — giáo trình thương mại; chưa có giấy phép phân phối',
                 'attribution': None, 'licenseVerified': False}],
    'lessons': [],
}
for l in lessons:
    vocab = [r for r in rows if r['lesson'] == l['number'] and r['status'] != 'rejected']
    pack['lessons'].append({
        'id': f"mimikara-n3-l{l['number']:02d}", 'number': l['number'],
        # The card already shows "Bài N · N3": the title names the pack and the sheet's group.
        'title': f"Mimikara N3 · {l['category']}", 'description': None,
        'vocabulary': [{'id': f"mimikara-n3-v{r['stt']:03d}", 'expression': r['word'], 'reading': r['reading'],
                        'furigana': None, 'meaningVi': r['meaning'], 'partOfSpeech': None, 'note': None,
                        'tags': [], 'examples': []} for r in vocab],
        'kanji': [], 'grammar': [],
    })
json.dump(pack, open(f'{out_dir}/pack-candidate.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

per_lesson = Counter(r['lesson'] for r in rows)
report = {
    'rows_in_file': len(rows),
    'status': dict(Counter(r['status'] for r in rows)),
    'issues': dict(Counter(i for r in rows for i in r['issues'].split(';') if i)),
    'transforms': dict(Counter(t for r in rows for t in r['transforms'].split(';') if t)),
    'lessons': [{'number': l['number'], 'category': l['category'], 'declared': l['declared'], 'rows': per_lesson[l['number']]} for l in lessons],
    'corrections': {stt: c['why'] for stt, c in CORRECTIONS.items()},
    'flagged_rows': [r for r in rows if r['status'] != 'ok' or r['issues'] or (r['transforms'] and 'KATAKANA' not in r['transforms'])],
}
json.dump(report, open(f'{out_dir}/report.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(json.dumps({k: v for k, v in report.items() if k != 'flagged_rows' and k != 'lessons'}, ensure_ascii=False))
for r in report['flagged_rows']:
    print(r['stt'], f"L{r['lesson']}", r['word'], '|', r['reading'], '|', r['meaning'], '|', r['status'], r['issues'], r['transforms'])
