"""Chạy bộ test case chức năng qua REST API thật (môi trường báo cáo, máy 2) và ghi kết quả từng ca:
mã, phân hệ, mục đích, input (giá trị cụ thể), output mong muốn, output thực tế, trạng thái, ngày test.
  BASE=http://127.0.0.1:28080 MANAGER_PASSWORD=... SEPAY_API_KEY=... python3 tc_runner.py > ket-qua.json
"""
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = os.environ.get('BASE', 'http://127.0.0.1:28080')
MPW = os.environ['MANAGER_PASSWORD']
KEY = os.environ.get('SEPAY_API_KEY', '')
VN = timezone(timedelta(hours=7))
RUN = str(int(time.time()))[-6:]
results = []


def call(method, path, body=None, token=None, headers=None):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {'raw': raw[:120]}


def short(code, body):
    err = (body or {}).get('error') or {}
    if err:
        return f"HTTP {code} {err.get('code', '')}: {err.get('message', '')[:90]}"
    d = (body or {}).get('data')
    if isinstance(d, dict):
        keys = ['status', 'result', 'workStatus', 'mustChangePassword', 'replayed', 'currentRecipeVersion', 'total', 'totalAmount', 'roles']
        shown = {k: d[k] for k in keys if k in d}
        if isinstance(d.get('items'), list) and d['items'] and isinstance(d['items'][0], dict) and 'status' in d['items'][0]:
            shown['items[0].status'] = d['items'][0]['status']
        return f'HTTP {code}; ' + ', '.join(f'{k}={v}' for k, v in shown.items())
    return f'HTTP {code}'


def tc(code, module, purpose, inp, expected_status, expected_text, fn, check=None):
    """fn() -> (http_code, body, thực tế bổ sung). Đạt khi mã HTTP đúng và check(body) (nếu có) đúng."""
    try:
        status, body, extra = fn()
        ok = status == expected_status and (check is None or check(body))
        actual = short(status, body) + (f'; {extra}' if extra else '')
    except Exception as e:  # lỗi của chính script test
        ok, actual = False, f'Lỗi khi chạy: {e}'
    results.append({'ma': code, 'phan_he': module, 'muc_dich': purpose, 'input': inp,
                    'mong_muon': f'HTTP {expected_status}; {expected_text}', 'thuc_te': actual,
                    'trang_thai': 'Đạt' if ok else 'Không đạt', 'ngay': datetime.now(VN).strftime('%d/%m/%Y %H:%M')})


def data(body):
    return (body or {}).get('data') or {}


def main():
    _, b = call('POST', '/api/auth/login', {'username': 'manager', 'password': MPW}); M = data(b)['accessToken']
    W = data(call('POST', '/api/auth/login', {'username': 'nv01', 'password': 'NhanVien@2026'})[1])['accessToken']
    K = data(call('POST', '/api/auth/login', {'username': 'nv10', 'password': 'NhanVien@2026'})[1])['accessToken']
    C = data(call('POST', '/api/auth/login', {'username': 'nv16', 'password': 'NhanVien@2026'})[1])['accessToken']

    # ---------------- Đăng nhập, bảo mật
    tc('TC01', 'Đăng nhập', 'Đăng nhập thành công', 'username=manager, password=<mật khẩu đúng>', 200, 'trả accessToken, roles=[MANAGER]',
       lambda: (*call('POST', '/api/auth/login', {'username': 'manager', 'password': MPW}), ''),
       lambda b: data(b).get('roles') == ['MANAGER'] and bool(data(b).get('accessToken')))
    tc('TC02', 'Đăng nhập', 'Sai mật khẩu', 'username=nv02, password=sai12345', 401, 'INVALID_CREDENTIALS, thông báo chung',
       lambda: (*call('POST', '/api/auth/login', {'username': 'nv02', 'password': 'sai12345'}), ''))
    tc('TC03', 'Đăng nhập', 'Tên đăng nhập không tồn tại trả cùng thông báo', 'username=khongtontai, password=abc12345', 401,
       'INVALID_CREDENTIALS giống TC02 (không lộ tài khoản)', lambda: (*call('POST', '/api/auth/login', {'username': 'khongtontai', 'password': 'abc12345'}), ''))
    tc('TC04', 'Đăng nhập', 'Bỏ trống mật khẩu', 'username=manager, password=""', 400, 'VALIDATION_ERROR',
       lambda: (*call('POST', '/api/auth/login', {'username': 'manager', 'password': ''}), ''))

    def lockout():
        u = f'thu{RUN}'
        for _ in range(5):
            call('POST', '/api/auth/login', {'username': u, 'password': 'sai12345'})
        return (*call('POST', '/api/auth/login', {'username': u, 'password': 'sai12345'}), 'lần thứ 6')
    tc('TC05', 'Đăng nhập', 'Sai 5 lần thì khóa tạm 15 phút', 'username=thu<số>, sai mật khẩu 6 lần liên tiếp', 429, 'TOO_MANY_ATTEMPTS ở lần thứ 6', lockout)
    tc('TC06', 'Đăng nhập', 'Tài khoản bị khóa không đăng nhập được', 'username=nv07 (LOCKED), password=TamThoi@2026', 401, 'INVALID_CREDENTIALS',
       lambda: (*call('POST', '/api/auth/login', {'username': 'nv07', 'password': 'TamThoi@2026'}), ''))
    tc('TC07', 'Phân quyền', 'Phục vụ không xem được báo cáo tài chính', 'token nv01 (WAITER), GET /api/reports/revenue-by-day', 403, 'FORBIDDEN',
       lambda: (*call('GET', '/api/reports/revenue-by-day?from=2026-11-01&to=2026-11-30', token=W), ''))
    tc('TC08', 'Phân quyền', 'Không có token', 'GET /api/tables, không gửi Authorization', 401, 'UNAUTHORIZED',
       lambda: (*call('GET', '/api/tables'), ''))

    # ---------------- Nhân sự
    tc('TC09', 'Nhân sự', 'Thêm nhân viên mới', f'employeeCode=NVT{RUN}, fullName=Trần Văn Test, departmentId=3, username=t{RUN}, roles=[WAITER]', 201,
       'nhân viên ACTIVE, mustChangePassword=true',
       lambda: (*call('POST', '/api/employees', {'employeeCode': f'NVT{RUN}', 'fullName': 'Trần Văn Test', 'departmentId': 3,
                                                 'username': f't{RUN}', 'initialPassword': 'TamThoi@2026', 'roles': ['WAITER']}, M), ''),
       lambda b: data(b).get('workStatus') == 'ACTIVE' and data(b).get('mustChangePassword') is True)
    tc('TC10', 'Nhân sự', 'Trùng tên đăng nhập', 'username=nv01 (đã có), employeeCode=NVX' + RUN, 409, 'DUPLICATE',
       lambda: (*call('POST', '/api/employees', {'employeeCode': f'NVX{RUN}', 'fullName': 'Trùng Tên', 'departmentId': 3,
                                                 'username': 'nv01', 'initialPassword': 'TamThoi@2026', 'roles': ['WAITER']}, M), ''))
    tc('TC11', 'Nhân sự', 'Vai trò không tồn tại', 'roles=[ADMIN]', 422, 'UNKNOWN_ROLE',
       lambda: (*call('POST', '/api/employees', {'employeeCode': f'NVY{RUN}', 'fullName': 'Sai Vai Trò', 'departmentId': 3,
                                                 'username': f'y{RUN}', 'initialPassword': 'TamThoi@2026', 'roles': ['ADMIN']}, M), ''))
    shifts = data(call('GET', '/api/shifts', token=M)[1])
    emp = [e for e in data(call('GET', '/api/employees?size=100', token=M)[1])['items'] if e['employeeCode'] == f'NVT{RUN}'][0]
    day = (datetime.now(VN) + timedelta(days=20)).date().isoformat()
    tc('TC12', 'Nhân sự', 'Phân ca hợp lệ', f"employeeId={emp['id']}, shiftId={shifts[0]['id']} ({shifts[0]['name']}), workDate={day}", 201, 'phân ca SCHEDULED',
       lambda: (*call('POST', '/api/shift-assignments', {'employeeId': emp['id'], 'shiftId': shifts[0]['id'], 'workDate': day}, M), ''),
       lambda b: data(b).get('status') == 'SCHEDULED')
    tc('TC13', 'Nhân sự', 'Phân ca trùng giờ cùng ngày', f"cùng nhân viên, shiftId={shifts[0]['id']}, workDate={day} lần 2", 409, 'OVERLAP',
       lambda: (*call('POST', '/api/shift-assignments', {'employeeId': emp['id'], 'shiftId': shifts[0]['id'], 'workDate': day}, M), ''))

    # ---------------- Thực đơn
    cats = data(call('GET', '/api/menu-categories', token=M)[1])
    tc('TC14', 'Thực đơn', 'Thêm món mới', f"categoryId={cats[0]['id']}, name=Món thử {RUN}, price=55000", 201, 'món có công thức phiên bản 1',
       lambda: (*call('POST', '/api/menu-items', {'categoryId': cats[0]['id'], 'name': f'Món thử {RUN}', 'price': 55000, 'active': True}, M), ''),
       lambda b: data(b).get('currentRecipeVersion') == 1)
    tc('TC15', 'Thực đơn', 'Giá có phần lẻ (VND phải là số nguyên)', 'price=55000.5', 400, 'VALIDATION_ERROR',
       lambda: (*call('POST', '/api/menu-items', {'categoryId': cats[0]['id'], 'name': f'Món lẻ {RUN}', 'price': 55000.5, 'active': True}, M), ''))
    tc('TC16', 'Thực đơn', 'Trùng tên nhóm món (không phân biệt hoa thường)', 'name=MÓN CHÍNH', 409, 'DUPLICATE',
       lambda: (*call('POST', '/api/menu-categories', {'name': 'MÓN CHÍNH', 'sortOrder': 9, 'active': True}, M), ''))
    menu = data(call('GET', '/api/daily-menus/today', token=M)[1])
    paused = next(i for i in menu['items'] if i.get('paused'))
    good = next(i for i in menu['items'] if i['name'] == 'Bia Hà Nội')

    # ---------------- Bán hàng
    free = [t for t in data(call('GET', '/api/tables', token=W)[1]) if not t.get('currentSessionId')]
    s = data(call('POST', f"/api/tables/{free[0]['id']}/sessions", token=W)[1])
    tc('TC17', 'Bán hàng', 'Mở bàn đang có khách', f"tableId={free[0]['id']} (vừa mở phiên)", 409, 'bàn đã có khách',
       lambda: (*call('POST', f"/api/tables/{free[0]['id']}/sessions", token=W), ''))
    oid = s['orderId']
    tc('TC18', 'Bán hàng', 'Gọi món số lượng 0', f'orderId={oid}, Bia Hà Nội, quantity=0', 400, 'VALIDATION_ERROR',
       lambda: (*call('POST', f'/api/orders/{oid}/batches', {'requestKey': f'tc18-{RUN}', 'items': [{'dailyMenuItemId': good['dailyMenuItemId'], 'quantity': 0}]}, W), ''))
    tc('TC19', 'Bán hàng', 'Gọi món đang tạm dừng', f"dailyMenuItemId={paused['dailyMenuItemId']} ({paused['name']}), quantity=1", 409, 'món tạm dừng',
       lambda: (*call('POST', f'/api/orders/{oid}/batches', {'requestKey': f'tc19-{RUN}', 'items': [{'dailyMenuItemId': paused['dailyMenuItemId'], 'quantity': 1}]}, W), ''))
    body = {'requestKey': f'tc20-{RUN}', 'items': [{'dailyMenuItemId': good['dailyMenuItemId'], 'quantity': 2}]}
    tc('TC20', 'Bán hàng', 'Gọi món hợp lệ', f'orderId={oid}, Bia Hà Nội x2, requestKey=tc20-{RUN}', 201, 'tạo 1 dòng món PENDING, giá 25000',
       lambda: (*call('POST', f'/api/orders/{oid}/batches', body, W), ''),
       lambda b: data(b)['items'][0]['status'] == 'PENDING')
    tc('TC21', 'Bán hàng', 'Gửi lặp cùng requestKey (chống gửi trùng)', f'gửi lại đúng body TC20', 201, 'replayed=true, không tạo dòng mới',
       lambda: (*call('POST', f'/api/orders/{oid}/batches', body, W), ''), lambda b: data(b).get('replayed') is True)
    order = data(call('GET', f'/api/orders/{oid}', token=W)[1])
    iid = [i for b2 in order.get('batches', []) for i in b2.get('items', [])] or order.get('items', [])
    iid = iid[0]['id']
    tc('TC22', 'Bán hàng', 'Chốt hóa đơn khi món chưa xong', f'orderId={oid} (món PENDING)', 409, 'còn món chưa kết thúc',
       lambda: (*call('POST', f'/api/orders/{oid}/invoice', token=C), ''))
    tc('TC23', 'Bếp', 'Bếp nhận món', f'itemId={iid}', 200, 'result=ACCEPTED, giữ nguyên liệu',
       lambda: (*call('POST', f'/api/order-items/{iid}/accept', token=K), ''), lambda b: data(b).get('result') == 'ACCEPTED')
    tc('TC24', 'Bếp', 'Nhận lại món đã nhận', f'itemId={iid} lần 2', 409, 'INVALID_STATE',
       lambda: (*call('POST', f'/api/order-items/{iid}/accept', token=K), ''))
    call('POST', f'/api/order-items/{iid}/start-cooking', token=K)
    tc('TC25', 'Bán hàng', 'Phục vụ hủy món đang nấu', f'itemId={iid} (COOKING), lý do=Khách đổi ý', 409, 'chỉ Manager hủy ngoại lệ',
       lambda: (*call('POST', f'/api/order-items/{iid}/cancel', {'reason': 'Khách đổi ý'}, W), ''))
    call('POST', f'/api/order-items/{iid}/finish', token=K)
    tc('TC26', 'Bán hàng', 'Bưng món đã xong', f'itemId={iid} (READY)', 200, 'món SERVED',
       lambda: (*call('POST', f'/api/order-items/{iid}/serve', token=W), ''), lambda b: data(b).get('status') == 'SERVED')
    _, b = call('POST', f'/api/orders/{oid}/invoice', token=C); inv = data(b)
    tc('TC27', 'Thanh toán', 'Thu tiền mặt thiếu', f"invoiceId={inv.get('invoiceId')}, tổng={inv.get('total')}, cashReceived=10000", 422, 'tiền đưa nhỏ hơn số phải thu',
       lambda: (*call('POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': 10000}, C), ''))
    tc('TC28', 'Thanh toán', 'Thu tiền mặt đủ', f"invoiceId={inv.get('invoiceId')}, cashReceived=100000", 201, 'status=PAID, bàn trống lại',
       lambda: (*call('POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': 100000}, C), ''),
       lambda b: data(b).get('status') == 'PAID')
    tc('TC29', 'Thanh toán', 'Thu tiền lần hai', f"invoiceId={inv.get('invoiceId')} đã PAID", 409, 'hóa đơn không còn chờ thu',
       lambda: (*call('POST', f"/api/invoices/{inv['invoiceId']}/payments", {'method': 'CASH', 'cashReceived': 100000}, C), ''))
    hook = {'id': int(RUN), 'transferType': 'in', 'transferAmount': 1000, 'code': 'RC1Q1', 'content': 'thu', 'accountNumber': '0010000000355'}
    tc('TC30', 'Thanh toán', 'Webhook SePay sai API key', 'Authorization: Apikey sai-khoa', 401, 'từ chối, không ghi nhận',
       lambda: (*call('POST', '/api/webhooks/sepay', hook, headers={'Authorization': 'Apikey sai-khoa'}), ''))
    tc('TC31', 'Thanh toán', 'Webhook không thấy mã tham chiếu', 'API key đúng, code=RC1Q1 không tồn tại, 1000đ', 200, 'result=MISMATCH (mở sự cố)',
       lambda: (*call('POST', '/api/webhooks/sepay', hook, headers={'Authorization': f'Apikey {KEY}'}), ''),
       lambda b: data(b).get('result') == 'MISMATCH')
    tc('TC32', 'Thanh toán', 'Webhook gửi lặp cùng mã giao dịch', f"id={hook['id']} lần 2", 200, 'cùng kết quả, không tạo bản ghi mới',
       lambda: (*call('POST', '/api/webhooks/sepay', hook, headers={'Authorization': f'Apikey {KEY}'}), ''),
       lambda b: data(b).get('result') == 'MISMATCH')

    # ---------------- Kho
    ing = data(call('GET', '/api/ingredients', token=M)[1])[0]
    rbody = {'requestKey': f'tc-r-{RUN}', 'supplierName': 'NCC thử', 'items': [{'ingredientId': ing['id'], 'inputQuantity': 2, 'inputUnit': 'kg',
             'conversionFactor': 1000, 'unitPricePerInputUnit': 100000, 'lotCode': f'TC{RUN}', 'expiryDate': (datetime.now(VN) + timedelta(days=10)).date().isoformat()}]}
    _, b = call('POST', '/api/stock-receipts', rbody, M); rid = data(b).get('id')
    tc('TC33', 'Kho', 'Lập phiếu nhập', f"{ing['name']} 2 kg x 100000đ, hệ số 1000", 201, 'phiếu DRAFT, tổng 200000',
       lambda: (*call('POST', '/api/stock-receipts', rbody, M), 'gửi lặp cùng requestKey trả lại phiếu cũ'), lambda b: data(b).get('id') == rid)
    tc('TC34', 'Kho', 'Xác nhận phiếu nhập', f'receiptId={rid}', 200, 'CONFIRMED, tạo lô RAW',
       lambda: (*call('POST', f'/api/stock-receipts/{rid}/confirm', {}, M), ''), lambda b: data(b).get('status') == 'CONFIRMED')
    tc('TC35', 'Kho', 'Xác nhận phiếu nhập lần hai', f'receiptId={rid} đã CONFIRMED', 409, 'không xác nhận lặp',
       lambda: (*call('POST', f'/api/stock-receipts/{rid}/confirm', {}, M), ''))
    tc('TC36', 'Kho', 'Bếp không được lập phiếu nhập', 'token nv10 (KITCHEN), POST /api/stock-receipts', 403, 'FORBIDDEN',
       lambda: (*call('POST', '/api/stock-receipts', dict(rbody, requestKey=f'tc-k-{RUN}'), K), ''))

    # ---------------- Tài chính
    ebody = {'requestKey': f'tc-e-{RUN}', 'category': 'SUPPLIES', 'description': 'Mua khăn lau', 'amount': 150000, 'paymentMethod': 'CASH', 'fundSource': 'CASH_DRAWER'}
    _, b = call('POST', '/api/expenses', ebody, M); eid = data(b).get('id')
    tc('TC37', 'Tài chính', 'Chi từ két nhưng chuyển khoản', 'fundSource=CASH_DRAWER, paymentMethod=BANK_TRANSFER, 50000', 422, 'INVALID_DATA',
       lambda: (*call('POST', '/api/expenses', dict(ebody, requestKey=f'tc-e2-{RUN}', paymentMethod='BANK_TRANSFER', amount=50000), M), ''))
    tc('TC38', 'Tài chính', 'Hủy khoản chi', f'expenseId={eid}, lý do=Ghi nhầm', 200, 'status=VOIDED',
       lambda: (*call('POST', f'/api/expenses/{eid}/void', {'reason': 'Ghi nhầm'}, M), ''), lambda b: data(b).get('status') == 'VOIDED')
    tc('TC39', 'Tài chính', 'Hủy khoản chi lần hai', f'expenseId={eid} đã VOIDED', 409, 'ALREADY_VOIDED',
       lambda: (*call('POST', f'/api/expenses/{eid}/void', {'reason': 'Ghi nhầm'}, M), ''))
    future = (datetime.now(VN) + timedelta(days=3)).date().isoformat()
    tc('TC40', 'Tài chính', 'Đối soát ngày tương lai', f'businessDate={future}, openingCash=0, actualCash=0', 422, 'không đối soát ngày chưa tới',
       lambda: (*call('POST', '/api/cash-reconciliations', {'businessDate': future, 'openingCash': 0, 'actualCash': 0}, M), ''))
    print(json.dumps(results, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
