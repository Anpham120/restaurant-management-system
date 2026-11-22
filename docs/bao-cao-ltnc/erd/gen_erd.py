"""Sinh ERD (PlantUML) từ catalog của PostgreSQL: đọc bảng, cột, khóa chính, khóa ngoại thật trong database
rồi viết file .puml theo từng phân hệ và một sơ đồ tổng quan. Không vẽ tay: đổi schema chạy lại là sơ đồ khớp.

  python3 gen_erd.py catalog.json   (catalog.json do query catalog.sql xuất ra)
Render: plantuml -tpng *.puml (cú pháp PlantUML, dán được vào planttext.com).
"""
import json
import sys

MODULES = {
    'nhan-su': ('Nhân sự và bảo mật', ['departments', 'employees', 'accounts', 'roles', 'permissions', 'account_roles',
                                       'role_permissions', 'shifts', 'shift_assignments', 'attendances',
                                       'attendance_adjustments', 'login_throttles']),
    'thuc-don': ('Thực đơn', ['menu_categories', 'menu_items', 'recipes', 'recipe_ingredients', 'daily_menus',
                              'daily_menu_items']),
    'ban-hang': ('Bán hàng và thanh toán', ['dining_tables', 'table_sessions', 'orders', 'order_batches', 'order_items',
                                            'order_item_events', 'invoices', 'invoice_items', 'payment_requests',
                                            'payment_webhook_events', 'payments']),
    'kho': ('Kho và bếp', ['ingredients', 'kitchen_ingredient_types', 'stock_receipts', 'stock_receipt_items',
                           'inventory_batches', 'stock_issues', 'stock_issue_items', 'kitchen_preparations',
                           'kitchen_preparation_outputs', 'kitchen_batches', 'stock_reservations', 'stock_returns',
                           'stock_return_items', 'stock_adjustments', 'stock_adjustment_items', 'stock_counts',
                           'stock_count_items', 'stock_movements']),
    'tai-chinh': ('Tài chính', ['operating_expenses', 'daily_cash_reconciliations']),
}
HEAD = ['@startuml', 'hide circle', 'skinparam linetype ortho', 'skinparam shadowing false',
        'skinparam defaultFontName "DejaVu Sans"', 'skinparam entity {', '  BackgroundColor #EAF2FB',
        '  BorderColor #1565C0', '}']
# Cột kiểm tra/audit lặp lại ở nhiều bảng: không vẽ để sơ đồ đọc được (vẫn có trong database diagram chi tiết)
NOISE = {'created_at', 'updated_at', 'version'}


def entity(t, cols, pks, fks, brief=False):
    out = [f'entity "{t}" as {t} {{']
    for c in cols:
        if c['name'] in pks:
            out.append(f"  * {c['name']} : {c['type']} <<PK>>")
    out.append('  --')
    if not brief:
        for c in cols:
            if c['name'] in pks or c['name'] in NOISE:
                continue
            mark = '* ' if c['notnull'] else ''
            fk = ' <<FK>>' if c['name'] in fks else ''
            out.append(f"  {mark}{c['name']} : {c['type']}{fk}")
    out.append('}')
    return out


def rel(fk, cols_of):
    """Quan hệ crow's foot: bên con (nhiều) }o hoặc }| ; bên cha || (FK NOT NULL) hoặc |o (FK cho phép NULL).
    FK có ràng buộc UNIQUE thì quan hệ 1-1."""
    notnull = all(next(c for c in cols_of[fk['table']] if c['name'] == col)['notnull'] for col in fk['columns'])
    parent = '||' if notnull else 'o|'
    child = '|o' if fk['unique'] else '}o'
    return f"{fk['table']} {child}--{parent} {fk['ref']} : {','.join(fk['columns'])}"


def main(path):
    cat = json.load(open(path, encoding='utf-8'))
    cols_of = {t: v['columns'] for t, v in cat.items()}
    pk_of = {t: set(v['pk']) for t, v in cat.items()}
    fks = [dict(fk, table=t) for t, v in cat.items() for fk in v['fks']]
    fk_cols = {t: {c for fk in v['fks'] for c in fk['columns']} for t, v in cat.items()}
    for key, (title, tables) in MODULES.items():
        lines = HEAD + [f'title ERD phân hệ {title}']
        ext = sorted({fk['ref'] for fk in fks if fk['table'] in tables and fk['ref'] not in tables})
        for t in tables:
            lines += entity(t, cols_of[t], pk_of[t], fk_cols[t])
        for t in ext:   # bảng của phân hệ khác: chỉ vẽ khóa chính để thấy quan hệ
            lines += [f'entity "{t}" as {t} #F3F6FA {{', '  * id : bigint <<PK>>', '}']
        lines += [rel(fk, cols_of) for fk in fks if fk['table'] in tables]
        lines.append('@enduml')
        open(f'erd-{key}.puml', 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    # Tổng quan: mọi bảng (chỉ tên), gom theo phân hệ, mọi quan hệ
    lines = HEAD + [f'title ERD tổng quan ({len(cat)} bảng, khóa ngoại thật trong database)', 'left to right direction']
    for key, (title, tables) in MODULES.items():
        lines.append(f'package "{title}" {{')
        lines += [f'  entity {t}' for t in tables]
        lines.append('}')
    lines += [f"{fk['table']} }}o--|| {fk['ref']}" for fk in fks if fk['ref'] != fk['table']]
    lines.append('@enduml')
    open('erd-tong-quan.puml', 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(len(cat), 'bảng,', len(fks), 'khóa ngoại')


if __name__ == '__main__':
    main(sys.argv[1])
