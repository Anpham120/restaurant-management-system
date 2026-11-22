"""Sinh dữ liệu mẫu cho báo cáo Lập trình nâng cao bằng cách gọi REST API thật của hệ thống (không INSERT tay).

Mọi bản ghi đi qua đúng tầng Service (kiểm tra nghiệp vụ, trigger, state machine), nên dữ liệu luôn hợp lệ và phủ nhiều
trạng thái: nhân viên ACTIVE/INACTIVE/LEFT, tài khoản LOCKED, phân ca SCHEDULED/COMPLETED/CANCELLED, phiếu kho
DRAFT/CONFIRMED/ISSUED/RECEIVED/CANCELLED, món PENDING/ACCEPTED/COOKING/READY/SERVED/REJECTED/CANCELLED, hóa đơn
PAID (tiền mặt, QR)/PAYMENT_PENDING/WAIVED/UNCOLLECTED, sự cố thanh toán, khoản chi PAID/VOIDED, đối soát nhiều phiên bản.

Chạy trên môi trường riêng của báo cáo (không phải production):
  BASE=http://127.0.0.1:28080 MANAGER_PASSWORD=... SEPAY_API_KEY=... python3 seed_baocao.py
Script chỉ dùng thư viện chuẩn của Python.
"""
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone

BASE = os.environ.get('BASE', 'http://127.0.0.1:28080')
MANAGER = os.environ.get('MANAGER_USER', 'manager')
MANAGER_PW = os.environ['MANAGER_PASSWORD']
STAFF_PW = os.environ.get('STAFF_PASSWORD', 'NhanVien@2026')
SEPAY_KEY = os.environ.get('SEPAY_API_KEY', '')
BANK = os.environ.get('SEPAY_BANK_ACCOUNT', '0010000000355')
random.seed(2026)
RUN = str(int(time.time()))[-6:]
VN = timezone(timedelta(hours=7))
TODAY = (datetime.now(VN) - timedelta(hours=4)).date()   # ngày bán bắt đầu 04:00 giờ Việt Nam
stats = {}


def count(key):
    stats[key] = stats.get(key, 0) + 1


class ApiError(Exception):
    def __init__(self, code, body):
        super().__init__(f'HTTP {code}: {body[:200]}')
        self.code, self.body = code, body


def call(method, path, body=None, token=None, headers=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            data = json.loads(raw) if raw else {}
            return data.get('data', data)
    except urllib.error.HTTPError as e:
        raise ApiError(e.code, e.read().decode())


def soft(label, fn, *a, **k):
    """Bước được phép lỗi (ca lỗi cố ý hoặc hết hàng): ghi lại, không dừng."""
    try:
        return fn(*a, **k)
    except ApiError as e:
        try:
            msg = json.loads(e.body)['error']['message'][:45]
        except Exception:
            msg = ''
        count(f'lỗi dự kiến: {label} ({e.code}) {msg}')
        return None


def login(user, pw):
    return call('POST', '/api/auth/login', {'username': user, 'password': pw})['accessToken']


def login_staff(user):
    """Tài khoản mới phải đổi mật khẩu lần đầu: đăng nhập bằng mật khẩu tạm rồi đổi sang mật khẩu thật."""
    try:
        return login(user, STAFF_PW)
    except ApiError:
        t = login(user, 'TamThoi@2026')
        return call('POST', '/api/auth/change-password',
                    {'currentPassword': 'TamThoi@2026', 'newPassword': STAFF_PW}, t)['accessToken']


# ---------------------------------------------------------------- 1. Nhân sự
HO = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Ngô']
DEM = ['Văn', 'Thị', 'Minh', 'Thu', 'Quốc', 'Ngọc', 'Hữu', 'Thanh']
TEN = ['An', 'Bình', 'Chi', 'Dũng', 'Hà', 'Hải', 'Hương', 'Khoa', 'Lan', 'Linh', 'Mai', 'Nam', 'Phúc', 'Quân',
       'Sơn', 'Trang', 'Tuấn', 'Vy', 'Yến', 'Long', 'Thảo', 'Kiên', 'Nga', 'Duy']


def seed_staff(tk):
    deps = {}
    for name in ['Quản lý', 'Phục vụ', 'Bếp', 'Thu ngân', 'Kho']:
        d = soft('phòng ban trùng', call, 'POST', '/api/departments', {'name': name}, tk)
        if d:
            deps[name] = d['id']; count('departments')
    for d in call('GET', '/api/departments', token=tk):
        deps.setdefault(d['name'], d['id'])
    plan = [('Phục vụ', 'WAITER')] * 9 + [('Bếp', 'KITCHEN')] * 6 + [('Thu ngân', 'CASHIER')] * 4 + \
           [('Kho', 'MANAGER')] * 2 + [('Quản lý', 'MANAGER')] * 2 + [('Phục vụ', 'CASHIER')]
    staff = []
    for i, (dep, role) in enumerate(plan, 1):
        user = f'nv{i:02d}'
        roles = [role] if not (dep == 'Phục vụ' and role == 'CASHIER') else ['WAITER', 'CASHIER']
        body = {'employeeCode': f'NV{i:03d}', 'fullName': f'{random.choice(HO)} {random.choice(DEM)} {TEN[i - 1]}',
                'departmentId': deps[dep], 'gender': random.choice(['Nam', 'Nữ']),
                'dateOfBirth': str(date(1990 + i % 12, 1 + i % 12, 1 + i % 27)), 'phone': f'09{random.randint(10**7, 10**8 - 1)}',
                'email': f'{user}@atlas.example', 'address': f'{i} Cầu Giấy, Hà Nội',
                'hireDate': str(TODAY - timedelta(days=30 * (1 + i % 18))),
                'username': user, 'initialPassword': 'TamThoi@2026', 'roles': roles}
        e = soft('nhân viên trùng', call, 'POST', '/api/employees', body, tk)
        if e:
            count('employees'); staff.append(e)
    all_emp = call('GET', '/api/employees?size=100', token=tk)['items']
    by_user = {e['username']: e for e in all_emp if e.get('username')}
    # Trạng thái đa dạng: 2 tạm nghỉ, 2 đã nghỉ việc, 2 tài khoản bị khóa, 1 đặt lại mật khẩu, 1 đổi vai trò
    for u in ['nv08', 'nv14']:
        e = by_user[u]
        soft('cập nhật', call, 'PUT', f"/api/employees/{e['id']}",
             {'fullName': e['fullName'], 'departmentId': e.get('departmentId') or deps['Phục vụ'], 'workStatus': 'INACTIVE'}, tk)
    for u in ['nv09', 'nv19']:
        soft('nghỉ việc', call, 'POST', f"/api/employees/{by_user[u]['id']}/leave", {'endDate': str(TODAY - timedelta(days=3))}, tk)
    for u in ['nv07', 'nv13']:
        soft('khóa', call, 'POST', f"/api/accounts/{by_user[u]['accountId']}/lock", None, tk)
    soft('đặt lại mật khẩu', call, 'POST', f"/api/accounts/{by_user['nv06']['accountId']}/reset-password", {'newPassword': 'DatLai@2026'}, tk)
    soft('đổi vai trò', call, 'PUT', f"/api/accounts/{by_user['nv05']['accountId']}/roles", {'roles': ['WAITER', 'CASHIER']}, tk)
    return by_user


def seed_shifts(tk, by_user):
    shifts = []
    for name, s, e, br in [('Ca sáng', '06:00', '14:00', 30), ('Ca chiều', '14:00', '22:00', 30),
                           ('Ca tối', '17:00', '23:00', 15), ('Ca đêm', '22:00', '06:00', 30), ('Ca hành chính', '08:00', '17:00', 60)]:
        sh = soft('ca trùng', call, 'POST', '/api/shifts',
                  {'name': name, 'startTime': s, 'endTime': e, 'breakMinutes': br, 'active': name != 'Ca đêm'}, tk)
        if sh:
            shifts.append(sh); count('shifts')
    shifts = [s for s in call('GET', '/api/shifts', token=tk) if s.get('active')]
    active = [e for u, e in by_user.items() if u.startswith('nv') and e.get('workStatus', 'ACTIVE') == 'ACTIVE']
    assigns = []
    for day in range(-6, 4):
        d = TODAY + timedelta(days=day)
        for e in active:
            if random.random() < 0.75:
                a = soft('trùng ca', call, 'POST', '/api/shift-assignments',
                         {'employeeId': e['id'], 'shiftId': random.choice(shifts)['id'], 'workDate': str(d), 'note': None}, tk)
                if a:
                    assigns.append(a); count('shift_assignments')
    for a in [a for a in assigns if a['workDate'] < str(TODAY)]:
        if random.random() < 0.08:
            soft('hủy ca', call, 'POST', f"/api/shift-assignments/{a['id']}/cancel", {'reason': 'Đổi lịch theo đề nghị'}, tk)
            continue
        start = datetime.fromisoformat(a['scheduledStartAt'].replace('Z', '+00:00'))
        end = datetime.fromisoformat(a['scheduledEndAt'].replace('Z', '+00:00'))
        late = timedelta(minutes=random.choice([0, 0, 0, 5, 12, 25]))
        status = 'ABSENT' if random.random() < 0.07 else 'PRESENT'
        body = {'shiftAssignmentId': a['id'], 'status': status, 'note': 'Đi muộn' if late.seconds > 600 else None}
        if status == 'PRESENT':
            body.update(actualStartAt=(start + late).isoformat(), actualEndAt=end.isoformat())
        at = soft('chấm công', call, 'POST', '/api/attendances', body, tk)
        if at:
            count('attendances')
            if status == 'PRESENT' and random.random() < 0.06:
                soft('điều chỉnh', call, 'POST', f"/api/attendances/{at['id']}/adjustments",
                     {'status': 'PRESENT', 'actualStartAt': start.isoformat(), 'actualEndAt': end.isoformat(),
                      'reason': 'Quên chấm công, quản lý xác nhận có mặt đúng giờ'}, tk)
                count('attendance_adjustments')
    for a in [a for a in assigns if a['workDate'] > str(TODAY)][:6]:
        soft('hủy ca', call, 'POST', f"/api/shift-assignments/{a['id']}/cancel", {'reason': 'Nhân viên xin nghỉ phép'}, tk)


# ---------------------------------------------------------------- 2. Kho
INGREDIENTS = [  # tên, đơn vị gốc, theo dõi hạn dùng, đơn vị nhập, hệ số quy đổi, giá/đơn vị nhập
    ('Thịt bò thăn', 'g', True, 'kg', 1000, 280000), ('Thịt lợn ba chỉ', 'g', True, 'kg', 1000, 140000),
    ('Ức gà', 'g', True, 'kg', 1000, 95000), ('Tôm sú', 'g', True, 'kg', 1000, 320000),
    ('Cá hồi', 'g', True, 'kg', 1000, 450000), ('Mực ống', 'g', True, 'kg', 1000, 220000),
    ('Gạo tám', 'g', False, 'bao 10kg', 10000, 220000), ('Bánh phở', 'g', True, 'kg', 1000, 25000),
    ('Bún tươi', 'g', True, 'kg', 1000, 20000), ('Trứng gà', 'quả', True, 'vỉ 10', 10, 35000),
    ('Rau muống', 'g', True, 'kg', 1000, 18000), ('Cải thảo', 'g', True, 'kg', 1000, 22000),
    ('Hành lá', 'g', True, 'kg', 1000, 40000), ('Rau thơm', 'g', True, 'kg', 1000, 60000),
    ('Hành tây', 'g', True, 'kg', 1000, 25000), ('Tỏi', 'g', False, 'kg', 1000, 50000),
    ('Ớt', 'g', True, 'kg', 1000, 45000), ('Chanh', 'quả', True, 'kg (20 quả)', 20, 30000),
    ('Nước mắm', 'ml', False, 'chai 500ml', 500, 45000), ('Dầu ăn', 'ml', False, 'can 2l', 2000, 90000),
    ('Đường', 'g', False, 'kg', 1000, 22000), ('Muối', 'g', False, 'kg', 1000, 8000),
    ('Nước dùng bò', 'ml', True, 'l', 1000, 30000), ('Đậu phụ', 'miếng', True, 'hộp 10', 10, 25000),
    ('Nấm hương', 'g', False, 'kg', 1000, 260000), ('Bia Hà Nội', 'lon', True, 'thùng 24', 24, 280000),
    ('Nước ngọt', 'lon', True, 'thùng 24', 24, 190000), ('Trà đá', 'cốc', False, 'mẻ 50', 50, 25000),
    ('Kem tươi', 'ml', True, 'hộp 1l', 1000, 120000), ('Bột chiên giòn', 'g', False, 'gói 1kg', 1000, 40000),
]
PREPARED = [('Thịt bò thái', 'Thịt bò thăn'), ('Gà luộc xé', 'Ức gà'), ('Tôm bóc vỏ', 'Tôm sú'),
            ('Rau sơ chế', 'Rau muống'), ('Hành phi', 'Hành tây'), ('Mực làm sạch', 'Mực ống')]


def seed_inventory(tk, ktk):
    ing, direct, prepared = {}, {}, {}
    for name, unit, exp, *_ in INGREDIENTS:
        r = soft('nguyên liệu trùng', call, 'POST', '/api/ingredients',
                 {'name': name, 'baseUnit': unit, 'minQuantity': 500 if unit in ('g', 'ml') else 10, 'expiryTracked': exp}, tk)
        if r:
            ing[name] = r['id']; count('ingredients')
        t = soft('loại bếp', call, 'POST', '/api/kitchen-ingredient-types',
                 {'rawIngredientId': ing.get(name), 'name': f'{name} (dùng thẳng)', 'handlingType': 'DIRECT',
                  'minimumQuantity': 100 if unit in ('g', 'ml') else 5}, tk)
        if t:
            direct[name] = t['id']; count('kitchen_ingredient_types')
    for pname, raw in PREPARED:
        t = soft('loại bếp', call, 'POST', '/api/kitchen-ingredient-types',
                 {'rawIngredientId': ing[raw], 'name': pname, 'handlingType': 'PREPARED', 'shelfLifeHours': 24,
                  'minimumQuantity': 100}, tk)
        if t:
            prepared[pname] = t['id']; count('kitchen_ingredient_types')
    # 12 phiếu nhập: 10 xác nhận (tạo lô RAW), 1 nháp, 1 nháp bị xóa
    suppliers = ['Công ty Thực phẩm Sạch Hà Nội', 'HTX Rau an toàn Đông Anh', 'Đại lý Hải sản Biển Đông',
                 'Siêu thị MM Mega Market', 'Nhà phân phối Bia Hà Nội']
    lots = []
    names = [n for n, *_ in INGREDIENTS]
    for k in range(12):
        pick = names if k < 2 else random.sample(names, random.randint(3, 6))
        items = []
        for name in pick:
            _, unit, exp, iu, conv, price = next(x for x in INGREDIENTS if x[0] == name)
            items.append({'ingredientId': ing[name], 'inputQuantity': 80 if k < 2 else random.randint(2, 10), 'inputUnit': iu,
                          'conversionFactor': conv, 'unitPricePerInputUnit': price, 'lotCode': f'L{RUN}-{k:02d}-{ing[name]}',
                          'expiryDate': str(TODAY + timedelta(days=random.randint(5, 40))) if exp else None})
        r = call('POST', '/api/stock-receipts', {'requestKey': f'seed-r-{RUN}-{k}', 'supplierName': random.choice(suppliers),
                                                  'note': f'Nhập hàng đợt {k + 1}', 'items': items}, tk)
        count('stock_receipts')
        if k == 11:
            soft('xóa nháp', call, 'DELETE', f"/api/stock-receipts/{r['id']}", None, tk)
        elif k != 10:
            r = call('POST', f"/api/stock-receipts/{r['id']}/confirm", {}, tk)
            lots += [l for l in r['lines'] if l.get('inventoryBatchId')]
    # Xuất kho cho bếp: mỗi nguyên liệu xuất DIRECT một phần; nguyên liệu có loại sơ chế xuất thêm PREPARE
    issues = []
    by_ing = {}
    for l in lots:
        by_ing.setdefault(l['ingredientId'], []).append(l)
    # 4 phiếu xuất phủ MỌI nguyên liệu (DIRECT 60% lô, nguyên liệu có loại sơ chế xuất thêm 20% PREPARE) → đã xuất, bếp nhận.
    # Thêm 2 phiếu nhỏ để có trạng thái khác: 1 phiếu xuất rồi hủy (hoàn RAW), 1 phiếu còn nháp.
    groups = [list(by_ing.items())[i::4] for i in range(4)]
    plans = [(g, 0.6, 'full') for g in groups] + [(groups[0][:2], 0.05, 'cancel'), (groups[1][:2], 0.05, 'draft')]
    for gi, (grp, ratio, mode) in enumerate(plans):
        items = []
        for ing_id, ls in grp:
            l = min(ls, key=lambda x: x.get('expiryDate') or '9999')   # FEFO: lô hết hạn sớm nhất trước
            items.append({'inventoryBatchId': l['inventoryBatchId'], 'quantity': max(1, round(l['baseQuantity'] * ratio)), 'fulfillmentMode': 'DIRECT'})
            name = next(n for n, i in ing.items() if i == ing_id)
            if mode == 'full' and any(raw_n == name for _, raw_n in PREPARED):
                items.append({'inventoryBatchId': l['inventoryBatchId'], 'quantity': round(l['baseQuantity'] * 0.2), 'fulfillmentMode': 'PREPARE'})
        iss = soft('phiếu xuất', call, 'POST', '/api/stock-issues', {'requestKey': f'seed-i-{RUN}-{gi}', 'note': f'Xuất bếp đợt {gi + 1}', 'items': items}, tk)
        if not iss:
            continue
        count('stock_issues')
        if mode == 'draft':
            continue
        iss = call('POST', f"/api/stock-issues/{iss['id']}/issue", None, tk)
        if mode == 'cancel':
            soft('hủy xuất', call, 'POST', f"/api/stock-issues/{iss['id']}/cancel", {'reason': 'Xuất nhầm số lượng'}, tk)
            continue
        iss = call('POST', f"/api/stock-issues/{iss['id']}/receive", None, ktk)
        issues.append(iss)
    # Sơ chế từ các dòng PREPARE
    for iss in issues:
        for line in iss['lines']:
            if line.get('fulfillmentMode') == 'PREPARE' and line.get('wipRemainingQuantity'):
                raw_name = next(n for n, i in ing.items() if i == line['ingredientId'])
                pname = next(p for p, r in PREPARED if r == raw_name)
                q = line['wipRemainingQuantity']
                pr = soft('sơ chế', call, 'POST', '/api/kitchen-preparations',
                          {'requestKey': f"seed-p-{RUN}-{line['id']}", 'stockIssueItemId': line['id'], 'inputQuantity': q,
                           'lossReason': 'Hao hụt khi sơ chế', 'outputs': [{'kitchenIngredientTypeId': prepared[pname], 'outputQuantity': round(q * 0.85)}]}, ktk)
                if pr:
                    count('kitchen_preparations')
                    if random.random() < 0.8:
                        soft('xác nhận sơ chế', call, 'POST', f"/api/kitchen-preparations/{pr['id']}/confirm", None, ktk)
    return ing, direct, prepared, lots, issues


def seed_stock_exceptions(tk, ktk, lots, issues):
    # 1 phiếu hoàn trả từ dòng DIRECT, 2 phiếu hủy hàng (lô RAW), 2 phiếu kiểm kê (1 xác nhận, 1 nháp)
    direct_lines = [l for i in issues for l in i['lines'] if l.get('fulfillmentMode') == 'DIRECT' and l.get('kitchenBatchId')]
    for n, line in enumerate(direct_lines[:6]):
        rt = soft('hoàn trả', call, 'POST', '/api/stock-returns',
                  {'requestKey': f"seed-rt-{RUN}-{n}", 'reason': 'Bếp dùng không hết, trả lại kho',
                   'items': [{'sourceIssueItemId': line['id'], 'directKitchenBatchId': line['kitchenBatchId'], 'quantity': max(1, round(line['quantity'] * 0.1))}]}, ktk)
        if rt:
            count('stock_returns')
            if n < 4:
                soft('xác nhận hoàn trả', call, 'POST', f"/api/stock-returns/{rt['id']}/confirm", None, tk)
    for n, (typ, why) in enumerate([('DAMAGE', 'Rơi vỡ khi vận chuyển'), ('EXPIRED', 'Quá hạn sử dụng'), ('QUALITY', 'Không đạt chất lượng'),
                                    ('DAMAGE', 'Bao bì rách, hàng bị ẩm'), ('OTHER', 'Chuột cắn, hủy theo biên bản')]):
        l = lots[n * 7 % len(lots)]
        adj = soft('hủy hàng', call, 'POST', '/api/stock-adjustments',
                   {'requestKey': f'seed-a-{RUN}-{n}', 'adjustmentType': typ, 'reason': why,
                    'items': [{'rawBatchId': l['inventoryBatchId'], 'quantity': max(1, round(l['baseQuantity'] * 0.05)), 'note': why}]}, ktk)
        if adj:
            count('stock_adjustments')
            if n < 4:
                soft('xác nhận hủy', call, 'POST', f"/api/stock-adjustments/{adj['id']}/confirm", None, tk)
    for n in range(5):
        pick = random.sample(lots, 4)
        cnt = soft('kiểm kê', call, 'POST', '/api/stock-counts',
                   {'requestKey': f'seed-c-{RUN}-{n}', 'note': f'Kiểm kê định kỳ {n + 1}',
                    'items': [{'rawBatchId': l['inventoryBatchId'], 'actualQuantity': max(0, round(l['baseQuantity'] * random.uniform(0.3, 0.45))),
                               'reason': 'Chênh lệch khi đếm thực tế'} for l in pick]}, tk)
        if cnt:
            count('stock_counts')
            if n < 3:
                soft('xác nhận kiểm kê', call, 'POST', f"/api/stock-counts/{cnt['id']}/confirm", None, tk)


# ---------------------------------------------------------------- 3. Thực đơn
MENU = {
    'Món khai vị': [('Gỏi cuốn tôm', 45000, ['Tôm bóc vỏ', 'Rau thơm']), ('Nem rán', 55000, ['Thịt lợn ba chỉ', 'Hành lá']),
                    ('Đậu phụ chiên', 35000, ['Đậu phụ', 'Dầu ăn']), ('Mực chiên giòn', 95000, ['Mực làm sạch', 'Bột chiên giòn'])],
    'Món chính': [('Phở bò tái', 65000, ['Thịt bò thái', 'Bánh phở', 'Nước dùng bò']), ('Bún chả', 60000, ['Thịt lợn ba chỉ', 'Bún tươi']),
                  ('Cơm gà xé', 55000, ['Gà luộc xé', 'Gạo tám']), ('Cơm rang dưa bò', 70000, ['Thịt bò thái', 'Gạo tám', 'Trứng gà']),
                  ('Cá hồi áp chảo', 185000, ['Cá hồi', 'Chanh']), ('Tôm rang muối', 165000, ['Tôm sú', 'Muối', 'Ớt']),
                  ('Bò lúc lắc', 145000, ['Thịt bò thăn', 'Hành tây']), ('Lẩu nấm', 250000, ['Nấm hương', 'Cải thảo', 'Đậu phụ'])],
    'Món rau': [('Rau muống xào tỏi', 45000, ['Rau sơ chế', 'Tỏi']), ('Cải thảo luộc', 35000, ['Cải thảo'])],
    'Đồ uống': [('Bia Hà Nội', 25000, ['Bia Hà Nội']), ('Nước ngọt', 20000, ['Nước ngọt']), ('Trà đá', 5000, ['Trà đá']),
                ('Nước chanh', 25000, ['Chanh', 'Đường'])],
    'Tráng miệng': [('Kem tươi', 30000, ['Kem tươi']), ('Chè đậu', 30000, ['Đường'])],
    'Món theo mùa': [('Lẩu cá (ngừng bán)', 280000, ['Cá hồi'])],
}
QTY = {'g': 120, 'ml': 250, 'quả': 1, 'lon': 1, 'cốc': 1, 'miếng': 2}


def seed_menu(tk, direct, prepared):
    unit_of = {n: u for n, u, *_ in INGREDIENTS}
    items = []
    for sort, (cat, dishes) in enumerate(MENU.items(), 1):
        c = call('POST', '/api/menu-categories', {'name': cat, 'sortOrder': sort, 'active': True}, tk); count('menu_categories')
        for name, price, recipe in dishes:
            m = call('POST', '/api/menu-items', {'categoryId': c['id'], 'name': name, 'price': price, 'active': True}, tk); count('menu_items')
            ingr = []
            for r in recipe:
                if r in prepared:
                    ingr.append({'kitchenIngredientTypeId': prepared[r], 'quantityPerServing': 100})
                else:
                    ingr.append({'kitchenIngredientTypeId': direct[r], 'quantityPerServing': QTY.get(unit_of[r], 1)})
            call('POST', f"/api/menu-items/{m['id']}/recipes", {'ingredients': ingr}, tk); count('recipes')
            if name in ('Bún chả', 'Phở bò tái'):   # đổi công thức: phiên bản 2
                call('POST', f"/api/menu-items/{m['id']}/recipes", {'ingredients': ingr[:1] + [dict(ingr[-1], quantityPerServing=ingr[-1]['quantityPerServing'] + 20)]}, tk)
                count('recipes')
            items.append((m, cat))
            if name.endswith('(ngừng bán)'):
                call('PUT', f"/api/menu-items/{m['id']}", {'categoryId': c['id'], 'name': name, 'price': price, 'active': False}, tk)
    menu = soft('thực đơn hôm nay', call, 'GET', '/api/daily-menus/today', None, tk)
    if not menu:
        menu = call('POST', '/api/daily-menus', {'businessDate': str(TODAY), 'fromCatalog': True}, tk)
    if menu['status'] == 'DRAFT':
        menu = call('POST', f"/api/daily-menus/{menu['id']}/publish", None, tk)
    menu = call('GET', '/api/daily-menus/today', token=tk)
    for it in menu['items']:                                         # hạn mức cho món giá cao
        if it['price'] >= 145000:
            soft('hạn mức', call, 'PUT', f"/api/daily-menu-items/{it['dailyMenuItemId']}", {'quotaTotal': 12}, tk)
    pause = next(i for i in menu['items'] if i['name'] == 'Chè đậu')
    soft('tạm dừng', call, 'POST', f"/api/daily-menu-items/{pause['dailyMenuItemId']}/pause", {'reason': 'Hết đậu xanh'}, tk)
    # Thực đơn 4 ngày tới: 2 ngày đã công bố trước, 2 ngày còn nháp
    for d in range(1, 5):
        tm = soft('thực đơn ngày tới', call, 'POST', '/api/daily-menus', {'businessDate': str(TODAY + timedelta(days=d)), 'fromCatalog': True}, tk)
        if tm:
            count('daily_menus')
            if d <= 2:
                soft('công bố trước', call, 'POST', f"/api/daily-menus/{tm['id']}/publish", None, tk)
    return call('GET', '/api/daily-menus/today', token=tk)


# ---------------------------------------------------------------- 4. Bán hàng
def seed_sales(tk, wtk, ktk, ctk, menu):
    sellable = [i for i in menu['items'] if not i.get('paused')]
    tables = []
    for n in range(1, 31):
        t = soft('bàn trùng', call, 'POST', '/api/tables', {'code': f'B{n:02d}', 'capacity': random.choice([2, 4, 4, 6, 8])}, tk)
        if t:
            tables.append(t); count('dining_tables')
    t = call('POST', '/api/tables', {'code': 'VIP-01', 'capacity': 12}, tk); tables.append(t)
    call('POST', f"/api/tables/{call('POST', '/api/tables', {'code': 'B99', 'capacity': 2}, tk)['id']}/deactivate", None, tk)
    kinds = ['cash'] * 60 + ['qr'] * 16 + ['qr_wait'] * 3 + ['waive'] * 5 + ['abandon'] * 4 + ['empty'] * 5 + ['open'] * 8 + ['mismatch'] * 2 + ['after_cancel'] * 2
    random.shuffle(kinds)
    for n, kind in enumerate(kinds):
        free = [t for t in call('GET', '/api/tables', token=wtk) if not t.get('currentSessionId')]
        if not free:
            count('hết bàn trống'); break
        table = random.choice(free)
        s = soft('mở bàn', call, 'POST', f"/api/tables/{table['id']}/sessions", None, wtk)
        if not s:
            continue
        count('table_sessions')
        oid = s['orderId']
        order_items = []
        for b in range(random.randint(1, 3)):
            pick = random.sample(sellable, random.randint(1, 4))
            res = soft('gọi món', call, 'POST', f'/api/orders/{oid}/batches',
                       {'requestKey': f'seed-b-{RUN}-{n}-{b}', 'items': [{'dailyMenuItemId': i['dailyMenuItemId'], 'quantity': random.randint(1, 3),
                                                                          'note': random.choice([None, None, 'Ít cay', 'Không hành'])} for i in pick]}, wtk)
            if res:
                count('order_batches'); order_items += res['items']
        if kind == 'empty':
            for it in order_items:
                soft('hủy món', call, 'POST', f"/api/order-items/{it['id']}/cancel", {'reason': 'Khách đổi ý'}, wtk)
            soft('đóng phiên rỗng', call, 'POST', f"/api/sessions/{s['sessionId']}/close-empty", None, wtk)
            continue
        served = 0
        for j, it in enumerate(order_items):
            r = random.random()
            if kind == 'open' and j == len(order_items) - 1:
                stage = random.choice(['PENDING', 'ACCEPTED', 'COOKING', 'READY'])
            elif r < 0.05:
                soft('từ chối', call, 'POST', f"/api/order-items/{it['id']}/reject", {'reason': 'Bếp quá tải món này'}, ktk); continue
            elif r < 0.09:
                soft('hủy món', call, 'POST', f"/api/order-items/{it['id']}/cancel", {'reason': 'Khách đổi món'}, wtk); continue
            else:
                stage = 'SERVED'
            acc = soft('nhận món', call, 'POST', f"/api/order-items/{it['id']}/accept", None, ktk)
            if stage == 'PENDING' or not acc or acc.get('result') == 'REJECTED':
                continue
            if stage == 'ACCEPTED':
                continue
            soft('nấu', call, 'POST', f"/api/order-items/{it['id']}/start-cooking", None, ktk)
            if stage == 'COOKING':
                continue
            soft('xong', call, 'POST', f"/api/order-items/{it['id']}/finish", None, ktk)
            if stage == 'READY':
                continue
            if soft('bưng', call, 'POST', f"/api/order-items/{it['id']}/serve", None, wtk):
                served += 1
            if random.random() < 0.03:
                soft('hủy ngoại lệ', call, 'POST', f"/api/order-items/{it['id']}/cancel-exception", {'reason': 'Món bị lỗi, đổi món khác'}, tk)
        if kind == 'open':
            continue
        if kind == 'abandon':
            soft('khách bỏ về', call, 'POST', f"/api/sessions/{s['sessionId']}/abandon", {'reason': 'Khách bỏ về không thanh toán'}, tk)
            continue
        if served == 0:
            if not soft('đóng phiên rỗng', call, 'POST', f"/api/sessions/{s['sessionId']}/close-empty", None, wtk):
                soft('miễn thu', call, 'POST', f'/api/orders/{oid}/waive', {'reason': 'Không có món phục vụ'}, tk)
            continue
        inv = soft('chốt hóa đơn', call, 'POST', f'/api/orders/{oid}/invoice', None, ctk)
        if not inv:
            continue
        count('invoices')
        if random.random() < 0.08:
            inv = call('POST', f"/api/invoices/{inv['invoiceId']}/reopen", {'reason': 'Khách gọi thêm món'}, ctk)
            inv = soft('chốt lại', call, 'POST', f'/api/orders/{oid}/invoice', None, ctk) or inv
        if kind == 'waive':
            soft('miễn thu', call, 'POST', f'/api/orders/{oid}/waive', {'reason': 'Khách VIP, quản lý miễn thu'}, tk)
        elif kind == 'cash':
            due = inv['total']
            soft('thu thiếu', call, 'POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': 1000}, ctk) if n % 9 == 0 else None
            soft('thu tiền mặt', call, 'POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': -(-int(due) // 50000) * 50000}, ctk)
        else:
            qr = soft('mở QR', call, 'POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'QR'}, ctk)
            if not qr or kind == 'qr_wait':
                continue
            amount = int(qr['amountDue'])
            if kind == 'after_cancel':
                req = call('GET', f"/api/invoices/{inv['invoiceId']}", token=ctk)['paymentRequests'][-1]
                soft('hủy QR', call, 'POST', f"/api/payment-requests/{req.get('requestId') or req.get('id')}/cancel", {'reason': 'Khách chuyển sang trả tiền mặt'}, ctk)
            if kind == 'mismatch':
                amount -= 10000
            hook = {'id': int(f'{RUN}{n:03d}'), 'gateway': 'Vietcombank', 'transactionDate': datetime.now(VN).strftime('%Y-%m-%d %H:%M:%S'),
                    'accountNumber': BANK, 'code': qr['paymentReference'], 'content': f"{qr['paymentReference']} thanh toan",
                    'transferType': 'in', 'transferAmount': amount, 'accumulated': 0, 'referenceCode': f'FT{RUN}{n:03d}'}
            soft('webhook SePay', call, 'POST', '/api/webhooks/sepay', hook, headers={'Authorization': f'Apikey {SEPAY_KEY}'})
            count('payment_webhook_events')
            if kind == 'after_cancel':
                soft('thu tiền mặt', call, 'POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': int(qr['amountDue'])}, ctk)
    # Giải quyết 1 sự cố thanh toán, để mở các sự cố còn lại
    inc = call('GET', '/api/payment-incidents?status=OPEN', token=tk)
    if inc.get('items'):
        soft('xử lý sự cố', call, 'POST', f"/api/payment-incidents/{inc['items'][0]['id']}/resolve",
             {'note': 'Đã gọi khách, hoàn lại tiền chuyển thừa'}, tk)


# ---------------------------------------------------------------- 5. Tài chính
def seed_finance(tk):
    rows = [('INGREDIENT', 'Mua rau bổ sung chợ Long Biên', 450000, 'CASH', 'CASH_DRAWER'), ('UTILITY', 'Tiền điện tháng 10', 3850000, 'BANK_TRANSFER', 'BANK'),
            ('UTILITY', 'Tiền nước tháng 10', 620000, 'BANK_TRANSFER', 'BANK'), ('RENT', 'Tiền thuê mặt bằng tháng 11', 25000000, 'BANK_TRANSFER', 'OWNER'),
            ('SUPPLIES', 'Mua khăn giấy, túi nilon', 380000, 'CASH', 'CASH_DRAWER'), ('MAINTENANCE', 'Sửa máy lạnh tầng 2', 1200000, 'CASH', 'OWNER'),
            ('SUPPLIES', 'Mua gas bếp', 960000, 'CASH', 'CASH_DRAWER'), ('OTHER', 'Phí vệ sinh môi trường', 150000, 'CASH', 'CASH_DRAWER'),
            ('INGREDIENT', 'Mua đá viên', 120000, 'CASH', 'CASH_DRAWER'), ('MAINTENANCE', 'Thay bóng đèn khu B', 240000, 'CASH', 'CASH_DRAWER'),
            ('SUPPLIES', 'In thực đơn mới', 650000, 'BANK_TRANSFER', 'BANK'), ('OTHER', 'Mua hoa trang trí', 300000, 'CASH', 'CASH_DRAWER')]
    exps = []
    for n, (cat, desc, amt, pm, fund) in enumerate(rows):
        paid = (datetime.now(timezone.utc) - timedelta(hours=random.randint(1, 140))).isoformat()
        e = soft('khoản chi', call, 'POST', '/api/expenses', {'requestKey': f'seed-e-{RUN}-{n}', 'category': cat, 'description': desc, 'amount': amt,
                                                             'paymentMethod': pm, 'fundSource': fund, 'paidAt': paid}, tk)
        if e:
            exps.append(e); count('operating_expenses')
    soft('hủy khoản chi', call, 'POST', f"/api/expenses/{exps[-1]['id']}/void", {'reason': 'Ghi nhầm, đã nhập lại'}, tk)
    soft('quỹ quầy sai', call, 'POST', '/api/expenses', {'requestKey': f'seed-e-{RUN}-x', 'category': 'OTHER', 'description': 'Sai quỹ', 'amount': 1000,
                                                     'paymentMethod': 'BANK_TRANSFER', 'fundSource': 'CASH_DRAWER'}, tk)
    for d in range(1, 4):
        day = TODAY - timedelta(days=d)
        soft('đối soát', call, 'POST', '/api/cash-reconciliations', {'businessDate': str(day), 'openingCash': 2000000, 'actualCash': 2000000 + 10000 * d, 'reason': 'Lệch do chi quỹ quầy chưa ghi sổ đầu ca'}, tk)
        count('daily_cash_reconciliations')
    soft('đối soát', call, 'POST', '/api/cash-reconciliations', {'businessDate': str(TODAY), 'openingCash': 2000000, 'actualCash': 2000000,
                                                               'reason': 'Kiểm lần đầu buổi trưa, chưa cộng tiền khách'}, tk)
    soft('đối soát lại', call, 'POST', '/api/cash-reconciliations', {'businessDate': str(TODAY), 'openingCash': 2000000, 'actualCash': 2150000,
                                                                   'reason': 'Thừa 150.000đ khách không lấy tiền thối', 'revisionReason': 'Đếm lại cuối ngày'}, tk)
    count('daily_cash_reconciliations')


def seed_login_failures():
    # Đăng nhập sai: bộ đếm theo tên đăng nhập (bảng login_throttles). nv20 sai 5 lần nên bị khóa tạm 15 phút.
    for user, times in [('nv20', 5), ('nv21', 2), ('nv22', 1), ('khach-la', 3), ('admin', 4)]:
        for _ in range(times):
            soft('đăng nhập sai', call, 'POST', '/api/auth/login', {'username': user, 'password': 'sai-mat-khau'})
        count('login_throttles')


def main():
    t0 = time.time()
    tk = login(MANAGER, MANAGER_PW)
    by_user = seed_staff(tk)
    seed_shifts(tk, by_user)
    wtk, ktk, ctk = login_staff('nv01'), login_staff('nv10'), login_staff('nv16')
    ing, direct, prepared, lots, issues = seed_inventory(tk, ktk)
    seed_stock_exceptions(tk, ktk, lots, issues)
    menu = seed_menu(tk, direct, prepared)
    seed_sales(tk, wtk, ktk, ctk, menu)
    seed_finance(tk)
    seed_login_failures()
    print(f'Xong sau {time.time() - t0:.0f} giây')
    for k, v in sorted(stats.items()):
        print(f'  {k}: {v}')


if __name__ == '__main__':
    main()
