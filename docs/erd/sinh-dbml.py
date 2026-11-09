#!/usr/bin/env python3
"""Sinh DBML (dbdiagram.io) từ catalog PostgreSQL thật + chú thích trong docs/04 + nhóm phân hệ trong docs/02."""
import json, re, sys
from collections import defaultdict, OrderedDict

cat = json.load(open(sys.argv[1]))
schema_sql = open(sys.argv[2], encoding='utf-8').read()
doc02 = open(sys.argv[3], encoding='utf-8').read()
out = sys.argv[4]

def q(s):  # chuỗi DBML trong nháy đơn
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'") + "'"

TYPE = [(r'^character varying\((\d+)\)$', r'varchar(\1)'), (r'^character\((\d+)\)$', r'char(\1)'),
        (r'^timestamp with time zone$', 'timestamptz'), (r'^time without time zone$', 'time'), (r'^integer$', 'int')]
def typ(t):
    for a, b in TYPE:
        if re.match(a, t):
            return re.sub(a, b, t)
    return t if re.fullmatch(r'[\w(),]+', t) else '"' + t + '"'

def default(d):
    if d is None:
        return None
    m = re.fullmatch(r"'((?:[^']|'')*)'(::[\w ]+)?", d)
    if m:
        return q(m.group(1).replace("''", "'"))
    if re.fullmatch(r'-?\d+(\.\d+)?', d) or d in ('true', 'false'):
        return d
    return '`' + d + '`'

# --- chú thích cột trong CREATE TABLE của docs/04 ("col ... -- ghi chú")
col_notes = defaultdict(dict)
for m in re.finditer(r'CREATE TABLE (\w+) \((.*?)\n\);', schema_sql, re.S):
    t = m.group(1)
    for line in m.group(2).split('\n'):
        cm = re.match(r'\s+(\w+)\s+[A-Z].*?--\s*(.+)$', line)
        if cm and cm.group(1).upper() not in ('CONSTRAINT', 'PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN'):
            col_notes[t][cm.group(1)] = cm.group(2).strip()

# --- nhóm phân hệ theo mục "-- n. PHÂN HỆ x …" hoặc "-- n.m. PHÂN HỆ x …" của docs/04 (mỗi bảng đúng một nhóm); tên nhóm theo docs/02
names = {m.group(1): m.group(2).strip() for m in re.finditer(r'^## \d+\. Phân hệ (\d) — ([^\n(]+)', doc02, re.M)}
groups = OrderedDict((f'Phân hệ {n} — {names[n]}', []) for n in sorted(names))
current = None
for line in schema_sql.split('\n'):
    m = re.match(r'^-- \d+(?:\.\d+)?\. (PHÂN HỆ (\d)|[A-ZĐ])', line)
    if m:
        current = m.group(2)
    t = re.match(r'^CREATE TABLE (\w+)', line)
    if t and current:
        groups[f'Phân hệ {current} — {names[current]}'].append(t.group(1))
cols = defaultdict(list)
for c in cat['columns']:
    cols[c['t']].append(c)
cons = defaultdict(list)
for k in cat['cons']:
    cons[k['t']].append(k)
uidx = defaultdict(list)
for i in cat['uidx'] or []:
    uidx[i['t']].append(i)

lines = ['// Sơ đồ CSDL Restaurant Core v4.1 cho https://dbdiagram.io/d — dán toàn bộ file vào ô code.',
         '// Sinh tự động từ catalog PostgreSQL sau khi nạp docs/04-schema-v4.1.sql; ghi chú cột lấy từ comment trong file đó,',
         '// nhóm bảng theo 5 phân hệ của docs/02. CHECK, index một phần và trigger chỉ ghi ở Note (DBML không biểu diễn được).',
         '', 'Project restaurant_core {', "  database_type: 'PostgreSQL'",
         "  Note: 'Restaurant Core v4.1 — 48 bảng. Quy tắc nghiệp vụ nằm trong hàm rc_* và trigger (docs/04).'", '}', '']
refs = []          # (dòng Ref, bảng, bảng đích)
blocks = {}
for t in sorted(cols):
    start = len(lines)
    pk = next((k['cols'] for k in cons[t] if k['type'] == 'p'), [])
    uniq1 = {k['cols'][0] for k in cons[t] if k['type'] == 'u' and len(k['cols']) == 1}
    checks = [k for k in cons[t] if k['type'] == 'c']
    enum_note = {}
    other_checks = []
    for k in checks:
        vals = re.findall(r"'([^']*)'::(?:character varying|text)", k['def'])
        colm = re.findall(r'\((\w+)\)::text', k['def'])
        if vals and re.search(r'ANY \(\(?ARRAY', k['def']) and len(set(colm)) == 1 and ' AND ' not in k['def'] and ' OR ' not in k['def']:
            enum_note[colm[0]] = ' | '.join(vals)
        else:
            other_checks.append(f"{k['name']}: {k['def']}")
    lines.append(f'Table {t} {{')
    for c in cols[t]:
        a = []
        if c['c'] in pk and len(pk) == 1:
            a.append('pk')
        if c['ident']:
            a.append('increment')
        if c['notnull'] and c['c'] not in pk:
            a.append('not null')
        if c['c'] in uniq1:
            a.append('unique')
        gen = c.get('gen') == 's'                       # cột GENERATED ALWAYS AS (...) STORED: không phải default
        d = None if gen else default(c['def'])
        if d and not c['ident']:
            a.append('default: ' + d)
        note = '; '.join(x for x in [col_notes[t].get(c['c']), ('Cột tính toán: GENERATED ALWAYS AS (' + c['def'] + ') STORED') if gen else None, ('Giá trị: ' + enum_note[c['c']]) if c['c'] in enum_note else None] if x)
        if note:
            a.append('note: ' + q(note))
        lines.append(f"  {c['c']} {typ(c['type'])}" + (f" [{', '.join(a)}]" if a else ''))
    idx = []
    if len(pk) > 1:
        idx.append(f"    ({', '.join(pk)}) [pk]")
    for k in cons[t]:
        if k['type'] == 'u' and len(k['cols']) > 1:
            idx.append(f"    ({', '.join(k['cols'])}) [unique, name: {q(k['name'])}]")
    for i in uidx[t]:
        body = re.search(r'USING \w+ \((.*)\)$', i['def'].split(' WHERE ')[0]).group(1)   # bỏ điều kiện WHERE của index một phần
        parts = [p.strip() for p in re.split(r',(?![^(]*\))', body)]
        parts = [p if re.fullmatch(r'\w+', p) else '`' + p + '`' for p in parts]
        extra = f", note: {q('WHERE ' + i['pred'])}" if i['pred'] else ''
        idx.append(f"    ({', '.join(parts)}) [unique, name: {q(i['name'])}{extra}]")
    if idx:
        lines += ['', '  indexes {'] + idx + ['  }']
    tnote = other_checks + [f"{k['name']}: {k['def']}" for k in cons[t] if k['type'] == 'x']
    if tnote:
        lines += ['', "  Note: '''", *['  ' + x.replace("'''", "\\'''") for x in tnote], "  '''"]
    lines += ['}', '']
    blocks[t] = lines[start:]
    for k in cons[t]:
        if k['type'] == 'f':
            if len(k['cols']) == 1:
                refs.append((f"Ref {k['name']}: {t}.{k['cols'][0]} > {k['ft']}.{k['fcols'][0]}", t, k['ft']))
            else:
                refs.append((f"Ref {k['name']}: {t}.({', '.join(k['cols'])}) > {k['ft']}.({', '.join(k['fcols'])})", t, k['ft']))
lines += ['// ---------- Khóa ngoại (' + str(len(refs)) + ')'] + [r[0] for r in refs] + ['']

grouped = set()
for i, (name, tables) in enumerate(groups.items(), 1):
    tables = [x for x in tables if x in cols]
    grouped |= set(tables)
    lines += [f'TableGroup "P{i} · {name.split("—", 1)[1].strip()}" {{'] + ['  ' + x for x in tables] + ['}', '']
missing = sorted(set(cols) - grouped)
if missing:
    lines += ['TableGroup "Khác" {'] + ['  ' + x for x in missing] + ['}', '']
open(out, 'w', encoding='utf-8').write('\n'.join(lines))

# File riêng cho từng phân hệ: bảng của phân hệ + bảng phân hệ khác mà nó trỏ tới (nhóm "Liên kết ngoài"), như sơ đồ docs/02
header = lines[:9]
for i, (name, tables) in enumerate(groups.items(), 1):
    own = set(tables)
    outside = sorted({ft for _, t, ft in refs if t in own and ft not in own})
    keep = own | set(outside)
    part = [f'// Phân hệ {i}: {name.split("—", 1)[1].strip()} — {len(own)} bảng, kèm {len(outside)} bảng liên kết ngoài.'] + header[1:]
    for t in tables + outside:
        part += blocks[t]
    part += [r for r, t, ft in refs if t in keep and ft in keep] + ['']
    part += [f'TableGroup "P{i} · {name.split("—", 1)[1].strip()}" {{'] + ['  ' + x for x in tables] + ['}', '']
    if outside:
        part += ['TableGroup "Liên kết ngoài" {'] + ['  ' + x for x in outside] + ['}', '']
    open(out.replace('.dbml', f'-p{i}.dbml'), 'w', encoding='utf-8').write('\n'.join(part))
print('bảng', len(cols), '| ref', len(refs), '| nhóm', {k.split('—')[0].strip(): len(v) for k, v in groups.items()}, '| ngoài nhóm', missing)
