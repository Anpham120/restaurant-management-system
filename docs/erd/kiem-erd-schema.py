#!/usr/bin/env python3
"""Đối chiếu 5 ERD draw.io (docs/erd/*.drawio) với catalog PostgreSQL của schema đã nạp:
bảng, cột (đủ, không thừa), nullability (trừ cột GEN), PK, FK. Bảng viền đứt (phân hệ khác) chỉ kiểm tên.

    python3 erd/kiem-erd-schema.py --psql "psql -X -At -h 127.0.0.1 -U postgres -d restaurant_core"

--psql: lệnh psql đọc SQL từ stdin và in một dòng JSON (-At). Thoát 0 khi không có lỗi.
"""
import argparse
import glob
import html
import json
import os
import re
import shlex
import subprocess
import sys

CATALOG_SQL = """SELECT json_build_object('columns', json_agg(json_build_object('t', c.table_name, 'c', c.column_name, 'nullable', c.is_nullable = 'YES',
  'pk', EXISTS (SELECT 1 FROM pg_constraint k JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = ANY (k.conkey)
                 WHERE k.contype = 'p' AND k.conrelid = (c.table_name)::regclass AND a.attname = c.column_name),
  'fk', (SELECT string_agg(k.confrelid::regclass::text, ',') FROM pg_constraint k JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = ANY (k.conkey)
                 WHERE k.contype = 'f' AND k.conrelid = (c.table_name)::regclass AND a.attname = c.column_name),
  'uk', EXISTS (SELECT 1 FROM pg_constraint k JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = ANY (k.conkey)
                 WHERE k.contype = 'u' AND array_length(k.conkey, 1) = 1 AND k.conrelid = (c.table_name)::regclass AND a.attname = c.column_name))))
FROM information_schema.columns c JOIN pg_class t ON t.relname = c.table_name AND t.relkind = 'r' AND t.relnamespace = 'public'::regnamespace
WHERE c.table_schema = 'public';"""

ap = argparse.ArgumentParser()
ap.add_argument('--psql', required=True)
a = ap.parse_args()
d = os.path.dirname(os.path.abspath(__file__))
p = subprocess.run(shlex.split(a.psql), input=CATALOG_SQL.encode(), capture_output=True)
if p.returncode:
    sys.exit(p.stderr.decode())
cat = json.loads(p.stdout.decode().strip().splitlines()[-1])
cols = {}      # (table, col) -> {nullable, pk, fk_to, uk}
for c in cat['columns']:
    cols[(c['t'], c['c'])] = dict(nullable=c['nullable'], pk=c['pk'], fk=c['fk'], uk=c['uk'])
tables = {t for t, _ in cols}
seen_tables = {}
checks = errs = 0
for f in sorted(glob.glob(d + '/*.drawio')):
    s = open(f, encoding='utf-8').read()
    for m in re.finditer(r'<mxCell id="t_(\w+)" value="([^"]*)" style="shape=table;([^"]*)"', s):
        tid, tname, style = m.group(1), re.sub(r' \(phân hệ \d\)$', '', html.unescape(m.group(2))), m.group(3)
        external = 'dashed=1' in style
        rows = re.findall(rf'<mxCell id="r_{tid}_(\w+?)_k" value="([^"]*)".*?\n.*?<mxCell id="r_{tid}_\w+?_n" value="([^"]*)".*?\n.*?<mxCell id="r_{tid}_\w+?_t" value="([^"]*)"', s)
        checks += 1
        if tname not in tables:
            print(f'{f}: bảng {tname} không có trong schema'); errs += 1
            continue
        seen_tables.setdefault(tname, []).append((f, external))
        if external:
            continue
        drawn = set()
        for _, key, col, typ in rows:
            col, key, typ = html.unescape(col), html.unescape(key), html.unescape(typ)
            drawn.add(col)
            checks += 3
            meta = cols.get((tname, col))
            if not meta:
                print(f'{f}: {tname}.{col} không có trong schema'); errs += 1; continue
            if 'GEN' not in typ and meta['nullable'] != ('NULL' in typ.split('→')[0].split()):
                print(f'{f}: {tname}.{col} nullability ERD "{typ}" ≠ schema nullable={meta["nullable"]}'); errs += 1
            if ('PK' in key) != meta['pk']:
                print(f'{f}: {tname}.{col} PK ERD "{key}" ≠ schema {meta["pk"]}'); errs += 1
            if ('FK' in key) != bool(meta['fk']) and not ('PK' in key and meta['fk']):
                print(f'{f}: {tname}.{col} FK ERD "{key}" ≠ schema {meta["fk"]}'); errs += 1
        missing = {c for (t, c) in cols if t == tname} - drawn
        checks += 1
        if missing:
            print(f'{f}: {tname} thiếu cột {sorted(missing)}'); errs += 1
not_drawn = tables - {t for t, v in seen_tables.items() if any(not e for _, e in v)}
checks += 1
if not_drawn:
    print('bảng không có ở ERD nào (bản chính):', sorted(not_drawn)); errs += 1
print(f'ERD ↔ schema: {checks} kiểm tra, {errs} lỗi; {len(tables)} bảng')
sys.exit(1 if errs else 0)
