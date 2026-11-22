"""Sinh câu SQL lấy 5 dòng mẫu của mỗi bảng (cột tiêu biểu) và thống kê số dòng theo trạng thái, từ catalog.json."""
import json, sys
cat = json.load(open(sys.argv[1], encoding='utf-8'))
SKIP = {'created_at', 'updated_at', 'password_hash', 'raw_payload', 'request_hash', 'version'}
def pick(cols):
    names = [c['name'] for c in cols if c['name'] not in SKIP]
    return names[:6]
parts = []
for t in sorted(cat):
    cols = pick(cat[t]['columns'])
    order = 'id' if any(c['name'] == 'id' for c in cat[t]['columns']) else cols[0]
    sel = ', '.join(f'"{c}"' for c in cols)
    parts.append(f"SELECT '{t}' AS t, (SELECT count(*) FROM {t}) AS n, "
                 f"(SELECT coalesce(json_agg(r), '[]') FROM (SELECT {sel} FROM {t} ORDER BY {order} LIMIT 5) r) AS rows")
print('SELECT json_agg(x) FROM (\n' + '\nUNION ALL\n'.join(parts) + '\n) x;')
