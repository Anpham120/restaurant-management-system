"""Sinh mã DBML (ngôn ngữ của dbdiagram.io) từ catalog thật của PostgreSQL, mỗi phân hệ một file.
Dán file .dbml vào https://dbdiagram.io/d là ra database diagram; không vẽ tay, đổi schema thì chạy lại.

  python3 gen_dbml.py catalog.json      (catalog.json do câu truy vấn catalog.sql xuất ra)

Quy ước:
- Bảng của phân hệ khác mà phân hệ này trỏ tới được thêm vào (chỉ khóa chính) và đặt trong nhóm "Phân hệ khác".
- Khóa ngoại ghi người thao tác (created_by_id, confirmed_by_id, actor_id...) tới employees có ở hầu hết bảng;
  cột vẫn được liệt kê nhưng không vẽ đường nối (trừ phân hệ Nhân sự), để sơ đồ không dồn hết về employees.
"""
import json
import sys

MODULES = [   # mỗi sơ đồ một cụm nghiệp vụ nhỏ (2–8 bảng) để đọc được
    ('p1a-tai-khoan', 'Nhân sự: nhân viên, tài khoản, phân quyền',
     ['departments', 'employees', 'accounts', 'roles', 'account_roles', 'permissions', 'role_permissions', 'login_throttles']),
    ('p1b-ca-lam', 'Nhân sự: ca làm, chấm công', ['shifts', 'shift_assignments', 'attendances', 'attendance_adjustments']),
    ('p2-thuc-don', 'Thực đơn', ['menu_categories', 'menu_items', 'recipes', 'recipe_ingredients', 'daily_menus', 'daily_menu_items']),
    ('p3a-goi-mon', 'Bán hàng: bàn, phiên, gọi món',
     ['dining_tables', 'table_sessions', 'orders', 'order_batches', 'order_items', 'order_item_events']),
    ('p3b-thanh-toan', 'Bán hàng: hóa đơn, thanh toán',
     ['invoices', 'invoice_items', 'payment_requests', 'payment_webhook_events', 'payments']),
    ('p4a-nhap-xuat', 'Kho: nhập kho, xuất cho bếp',
     ['ingredients', 'kitchen_ingredient_types', 'stock_receipts', 'stock_receipt_items', 'inventory_batches', 'stock_issues',
      'stock_issue_items']),
    ('p4b-bep', 'Kho: sơ chế, lô bếp, giữ nguyên liệu, hoàn trả',
     ['kitchen_preparations', 'kitchen_preparation_outputs', 'kitchen_batches', 'stock_reservations', 'stock_returns',
      'stock_return_items']),
    ('p4c-kiem-ke', 'Kho: hủy hàng, kiểm kê, sổ kho',
     ['stock_adjustments', 'stock_adjustment_items', 'stock_counts', 'stock_count_items', 'stock_movements']),
    ('p5-tai-chinh', 'Tài chính', ['operating_expenses', 'daily_cash_reconciliations']),
]
TYPES = {'character varying': 'varchar', 'timestamp with time zone': 'timestamptz', 'integer': 'int',
         'time without time zone': 'time', 'character': 'char'}


def dbml_type(t):
    for k, v in TYPES.items():
        if t == k or t.startswith(k + '('):
            return v + t[len(k):]
    return t


def is_audit(fk):
    return fk['ref'] == 'employees' and all(c.endswith('_by_id') or c == 'actor_id' for c in fk['columns'])


def table_block(t, info, brief=False, uniq=()):
    """uniq: các bộ cột được khóa ngoại kép tham chiếu tới (trong DB đã có UNIQUE, DBML cần khai báo lại)."""
    pk = info['pk']
    uniq = list(uniq)
    uniq1 = set()
    for fk in info['fks']:                       # khóa ngoại có UNIQUE (quan hệ một - một)
        if fk['unique'] and len(fk['columns']) == 1:
            uniq1.add(fk['columns'][0])
        elif fk['unique'] and tuple(fk['columns']) not in uniq:
            uniq.append(tuple(fk['columns']))
    out = [f'Table {t} {{']
    for c in info['columns']:
        if brief and c['name'] not in pk:
            continue
        attrs = []
        if c['name'] in pk and len(pk) == 1:
            attrs.append('pk')
        if c['notnull'] and c['name'] not in pk:
            attrs.append('not null')
        if c['name'] in uniq1 and c['name'] not in pk:
            attrs.append('unique')
        out.append(f"  {c['name']} {dbml_type(c['type'])}" + (f" [{', '.join(attrs)}]" if attrs else ''))
    idx = ([f"    ({', '.join(pk)}) [pk]"] if len(pk) > 1 else []) + [f"    ({', '.join(u)}) [unique]" for u in uniq]
    if idx:
        out += ['  indexes {'] + idx + ['  }']
    out.append('}')
    return out


def ref_line(t, fk, cols):
    """dbdiagram: ">" nhiều - một, "-" một - một (khóa ngoại có UNIQUE); thêm "?" khi cột khóa ngoại cho phép NULL
    (quan hệ tùy chọn: dòng con có thể không thuộc dòng cha nào)."""
    nullable = not all(next(c for c in cols if c['name'] == n)['notnull'] for n in fk['columns'])
    op = ('?' if nullable else '') + ('-' if fk['unique'] else '>')
    if len(fk['columns']) == 1:
        return f"Ref: {t}.{fk['columns'][0]} {op} {fk['ref']}.{fk['ref_columns'][0]}"
    return f"Ref: {t}.({', '.join(fk['columns'])}) {op} {fk['ref']}.({', '.join(fk['ref_columns'])})"


def main(path):
    cat = json.load(open(path, encoding='utf-8'))
    for key, title, tables in MODULES:
        refs = [(t, fk) for t in tables for fk in cat[t]['fks'] if key.startswith('p1') or not is_audit(fk)]
        outside = sorted({fk['ref'] for _, fk in refs if fk['ref'] not in tables})
        lines = [f'// Phân hệ {title}: {len(tables)} bảng. Sinh tự động từ catalog PostgreSQL (gen_dbml.py).',
                 '// Dán toàn bộ file vào https://dbdiagram.io/d để xem database diagram.', '',
                 'Project atlas_restaurant {', "  database_type: 'PostgreSQL'", '}', '']
        uniq = {}
        for _, fk in refs:
            if len(fk['ref_columns']) > 1 and tuple(fk['ref_columns']) not in uniq.setdefault(fk['ref'], []):
                uniq[fk['ref']].append(tuple(fk['ref_columns']))
        for t in tables:
            lines += table_block(t, cat[t], uniq=uniq.get(t, ())) + ['']
        for t in outside:
            lines += table_block(t, cat[t], brief=True, uniq=uniq.get(t, ())) + ['']
        lines += [ref_line(t, fk, cat[t]['columns']) for t, fk in refs] + ['']
        lines += [f'TableGroup "{title}" {{'] + [f'  {t}' for t in tables] + ['}', '']
        if outside:
            lines += ['TableGroup "Phân hệ khác" {'] + [f'  {t}' for t in outside] + ['}', '']
        open(f'dbml-{key}.dbml', 'w', encoding='utf-8').write('\n'.join(lines))
        print(key, len(tables), 'bảng,', len(refs), 'ref,', len(outside), 'bảng ngoài')


if __name__ == '__main__':
    main(sys.argv[1])
