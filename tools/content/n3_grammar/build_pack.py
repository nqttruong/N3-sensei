"""Build the N3 grammar content pack.

Metadata (order, pattern name, URL) comes from the public index page
https://www.vnjpclub.com/mimi-kara-n3-bunpo/ (saved in vnjpclub-index.json). The detail pages
are not public (loaded after a VIP check), so meanings, structures, explanations and examples
are written for N3 Sensei (part1..part4.py) and labelled as such.

Version 2 adds the points of the user's N3.pdf grammar cards that the 110 do not have
(pdf_new_points.py: pattern, conjugation and short gloss only) after the 110, keeps the ids
of the 110 (learning progress) and records which PDF cards each point covers
(pdf_mapping.py). The whole list is then cut into lessons of 7 in that order.

Usage: python3 -I tools/content/n3_grammar/build_pack.py <out.json> [report.json]
"""
import importlib.util, json, re, sys, unicodedata
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
from pdf_mapping import MAPPING  # noqa: E402
from pdf_new_points import NEW_POINTS  # noqa: E402
index = json.loads((HERE / 'vnjpclub-index.json').read_text(encoding='utf-8'))
entries = []
for part in sorted(HERE.glob('part*.py')):
    spec = importlib.util.spec_from_file_location(part.stem, part)
    mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
    entries += mod.ENTRIES

JA = re.compile(r'[぀-ヿ一-鿿]')
def clean(s): return re.sub(r'\s+', ' ', unicodedata.normalize('NFC', s)).strip()

problems, notes = [], []
by_n = {e['n']: e for e in entries}
if sorted(by_n) != [r['order'] for r in index] or len(by_n) != len(entries):
    problems.append('entry numbers do not match the 110 index items one to one')
for e in entries:
    for f in ('pattern', 'structure', 'meaning', 'usage'):
        if not clean(e.get(f, '')): problems.append(f"{e['n']}: missing {f}")
    if len(e['examples']) < 2: problems.append(f"{e['n']}: fewer than 2 examples")
    for ja, vi in e['examples']:
        if not JA.search(ja) or not ja.endswith(('。', '？', '?', '！')): problems.append(f"{e['n']}: odd Japanese example {ja}")
        if JA.search(vi) and not re.search(r'[a-zA-Zà-ỹ]', vi): problems.append(f"{e['n']}: translation is not Vietnamese: {vi}")
all_ex = [ja for e in entries for ja, _ in e['examples']]
dups = {x for x in all_ex if all_ex.count(x) > 1}
if dups: problems.append(f'duplicate example sentences: {dups}')

# Patterns that share a name in the source index: kept as separate entries (different uses).
names = {}
for r in index: names.setdefault(re.sub(r'\s+', '', r['title']), []).append(r['order'])
same_name = {k: v for k, v in names.items() if len(v) > 1}

LESSON_SIZE = 7
PDF_LICENSE = ('Chỉ lấy tên mẫu, cách chia và nghĩa ngắn từ thẻ ngữ pháp trong tệp N3.pdf của người dùng (bộ thẻ Anki "N3"); '
               'không lấy giải thích, mẹo nhớ, câu ví dụ. Chưa xác minh quyền sử dụng.')
pack = {
    'format': 'n3sensei-content-pack', 'formatVersion': 1,
    'pack': {'id': 'n3-grammar', 'title': 'Ngữ pháp N3 (danh mục Mimikara N3 + bổ sung từ N3.pdf)', 'version': 2,
             'jlptLevel': 'N3', 'defaultSourceId': 'n3sensei-grammar'},
    # The pack needs a default source; each item also names its own source below.
    'sources': [{
        'id': 'n3sensei-grammar',
        'name': 'N3 Sensei tự soạn · danh mục theo VNJPCLUB "Mimikara N3 ngữ pháp"',
        'url': 'https://www.vnjpclub.com/mimi-kara-n3-bunpo/',
        'license': 'Giải thích, cấu trúc và ví dụ do N3 Sensei tự soạn, chưa qua kiểm duyệt chuyên môn. Chỉ tên mẫu và thứ tự lấy từ danh mục công khai của nguồn.',
        'attribution': None, 'licenseVerified': False,
    }],
    'examples': [], 'lessons': [],
}
# Which PDF cards each existing point covers (traceability only: the app ignores the field).
covers = {}
for card, (cat, target) in MAPPING.items():
    if cat in (1, 2):
        for gid in target: covers.setdefault(gid, []).append({'card': card, 'category': cat})
points = []
for r in index:
    e = by_n[r['order']]
    gid = f"n3g-g{e['n']:03d}"
    src = f"vnjp-np-{e['n']:03d}"
    pack['sources'].append({
        'id': src,
        'name': f"N3 Sensei tự soạn · đối chiếu mục {e['n']} \"{clean(r['title'])}\" trong danh mục VNJPCLUB Mimikara N3 ngữ pháp",
        'url': r['url'],
        'license': 'Giải thích, cấu trúc và ví dụ do N3 Sensei tự soạn, chưa qua kiểm duyệt chuyên môn. Chỉ tên mẫu và thứ tự lấy từ danh mục công khai của nguồn.',
        'attribution': None, 'licenseVerified': False,
    })
    ex_ids = []
    for k, (ja, vi) in enumerate(e['examples'], 1):
        xid = f"{gid}-ex{k}"
        pack['examples'].append({'id': xid, 'japanese': clean(ja), 'furigana': None, 'translationVi': clean(vi), 'sourceId': src})
        ex_ids.append(xid)
    points.append({
        'id': gid, 'pattern': clean(e['pattern']), 'structure': clean(e['structure']), 'meaningVi': clean(e['meaning']),
        'usage': clean(e['usage']), 'cautionNote': clean(e['caution']) if e.get('caution') else None,
        'examples': ex_ids, 'exercises': [], 'sourceId': src,
        'origin': {'list': 'vnjpclub-mimikara-n3', 'order': e['n'], 'pdfCards': covers.get(gid, [])},
    })

new_cards = [card for card, (cat, _) in MAPPING.items() if cat in (3, 4)]
if sorted(new_cards) != sorted(NEW_POINTS) or len(NEW_POINTS) != len(new_cards):
    problems.append('pdf_new_points.py does not match the category 3/4 cards of pdf_mapping.py')
for n, card in enumerate(sorted(NEW_POINTS, key=lambda c: (c[:2], int(c[3:]))), start=len(index) + 1):
    pattern, structure, meaning, caution = NEW_POINTS[card]
    gid = f"n3g-g{n:03d}"
    src = f"n3pdf-g-{card.replace('.', '-')}"
    pack['sources'].append({
        'id': src, 'name': f"N3.pdf · Ngữ pháp, nhóm {card[:2]} thẻ {int(card[3:])} (tên mẫu, cách chia, nghĩa ngắn)",
        'url': None, 'license': PDF_LICENSE, 'attribution': None, 'licenseVerified': False,
    })
    for f, v in (('pattern', pattern), ('structure', structure), ('meaning', meaning)):
        if not clean(v): problems.append(f'{card}: missing {f}')
    points.append({
        'id': gid, 'pattern': clean(pattern), 'structure': clean(structure), 'meaningVi': clean(meaning),
        'usage': None, 'cautionNote': clean(caution) if caution else None,
        'examples': [], 'exercises': [], 'sourceId': src,
        'origin': {'list': 'n3-pdf', 'card': card, 'category': MAPPING[card][0]},
    })

total_lessons = -(-len(points) // LESSON_SIZE)
for i in range(total_lessons):
    chunk = points[i * LESSON_SIZE:(i + 1) * LESSON_SIZE]
    first, last = i * LESSON_SIZE + 1, i * LESSON_SIZE + len(chunk)
    pack['lessons'].append({'id': f"n3g-l{i + 1:02d}", 'number': i + 1, 'title': f"Ngữ pháp N3 · mục {first}–{last}",
                            'description': f'7 mẫu mỗi bài theo thứ tự: {len(index)} mẫu theo danh mục Mimikara N3, sau đó {len(NEW_POINTS)} mẫu bổ sung từ N3.pdf.',
                            'vocabulary': [], 'kanji': [], 'grammar': chunk})

if problems:
    print('PROBLEMS:', *problems, sep='\n  '); sys.exit(1)
Path(sys.argv[1]).write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding='utf-8')
report = {'index_items': len(index), 'grammar_110': len(entries), 'pdf_new': len(NEW_POINTS), 'grammar': len(points),
          'examples': len(pack['examples']), 'lessons': len(pack['lessons']),
          'lesson_sizes': [len(l['grammar']) for l in pack['lessons']],
          'pdf_categories': {str(c): sum(1 for v in MAPPING.values() if v[0] == c) for c in (1, 2, 3, 4, 5, 'compare')},
          'same_name_in_index': same_name,
          'pattern_differs_from_index': [{'n': r['order'], 'index': clean(r['title']), 'pack': clean(by_n[r['order']]['pattern'])} for r in index]}
if len(sys.argv) > 2: Path(sys.argv[2]).write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding='utf-8')
print(json.dumps({k: v for k, v in report.items() if k != 'pattern_differs_from_index'}, ensure_ascii=False))
