"""Chương 3: phân tích và thiết kế (cơ sở dữ liệu + hệ thống)."""
import json, os
from ltnc_lib import *
from ltnc_data import MODULES, MEANING

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAT = json.load(open(f'{R}/erd/catalog.json', encoding='utf-8'))
MAU = {x['t']: x for x in json.load(open(f'{R}/du-lieu-mau/mau.json', encoding='utf-8'))}
TT = json.load(open(f'{R}/du-lieu-mau/trang-thai.json', encoding='utf-8'))
_tc = [0]


def tc_next(title):
    _tc[0] += 1
    return TC(f'Bảng 3.{_tc[0]}. {title}')


_fc = [0]


def fig(path, title, **k):
    _fc[0] += 1
    return FIG(path, f'Hình 3.{_fc[0]}. {title}', **k)


def entity_tables():
    out = []
    for mod, tables in MODULES:
        out.append(tc_next(f'Thực thể phân hệ {mod}'))
        rows = [['Bảng (thực thể)', 'Ý nghĩa', 'Khóa chính', 'Khóa ngoại tới']]
        for t in tables:
            c = CAT[t]
            refs = sorted({fk['ref'] for fk in c['fks']})
            rows.append([t, MEANING[t], ', '.join(c['pk']), ', '.join(refs) or '—'])
        out.append(T([2200, 3472, 1100, 2300], [[brk(c) for c in r] for r in rows]))
    return out


def attribute_tables():
    """Thuộc tính của các thực thể chính (đủ cột, kiểu, bắt buộc)."""
    out = []
    for t in ['employees', 'accounts', 'menu_items', 'daily_menu_items', 'orders', 'order_items', 'invoices', 'payments',
              'inventory_batches', 'stock_issues']:
        c = CAT[t]
        pk, fkc = set(c['pk']), {col for fk in c['fks'] for col in fk['columns']}
        out.append(tc_next(f'Thuộc tính bảng {t}'))
        rows = [['Thuộc tính', 'Kiểu dữ liệu', 'Bắt buộc', 'Khóa']]
        for col in c['columns']:
            k = 'PK' if col['name'] in pk else ('FK' if col['name'] in fkc else '')
            rows.append([col['name'], col['type'], 'có' if col['notnull'] else 'không', k])
        out.append(T([2900, 3172, 1300, 1700], rows))
    return out


def relation_table():
    rows = [['Bảng con', 'Khóa ngoại', 'Bảng cha', 'Quan hệ', 'Bắt buộc']]
    for mod, tables in MODULES:
        for t in tables:
            cols = {c['name']: c for c in CAT[t]['columns']}
            for fk in CAT[t]['fks']:
                need = all(cols[x]['notnull'] for x in fk['columns'])
                rows.append([t, ', '.join(fk['columns']), fk['ref'], '1 - 1' if fk['unique'] else 'n - 1', 'có' if need else 'không'])
    return [tc_next(f'Các mối quan hệ (khóa ngoại) trong cơ sở dữ liệu ({len(rows) - 1} quan hệ)'), T([2400, 2700, 2172, 900, 900], rows)]


def short(v):
    import datetime, re as _re
    if v is None:
        return ''
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, float):
        if v == int(v):
            return f'{int(v):,}'.replace(',', '.')
        return f'{v:,.2f}'.replace(',', ' ').replace('.', ',').replace(' ', '.')
    s = str(v)
    if _re.fullmatch(r'\d{4}-\d\d-\d\dT[\d:.]+[+-]\d\d:\d\d', s):   # giờ UTC -> giờ Việt Nam
        t = datetime.datetime.fromisoformat(s).astimezone(datetime.timezone(datetime.timedelta(hours=7)))
        return t.strftime('%d/%m/%y %H:%M')
    if _re.fullmatch(r'\d{4}-\d\d-\d\d', s):
        return f'{s[8:]}/{s[5:7]}/{s[:4]}'
    return s if len(s) <= 24 else s[:22] + '…'


def brk(name):
    return name.replace('_', '_\u200b')     # cho phép xuống dòng sau dấu gạch dưới ở tiêu đề cột


def sample_tables():
    out = []
    for mod, tables in MODULES:
        out.append(H3(f'3.1.{7 + MODULES.index((mod, tables))}. Dữ liệu mẫu phân hệ {mod}'))
        for t in tables:
            m = MAU[t]
            if not m['rows']:
                out.append(B(f'Bảng {t}: {m["n"]} dòng. Đây là bảng dự phòng cho phân quyền chi tiết; ứng dụng hiện phân quyền theo vai trò nên không ghi vào bảng này, và nhóm không chèn dữ liệu bằng tay.'))
                continue
            cols = list(m['rows'][0].keys())
            out.append(tc_next(f'Dữ liệu mẫu bảng {t} (tổng {m["n"]} dòng)'))
            w = 9072 // len(cols)
            out.append(T([w] * (len(cols) - 1) + [9072 - w * (len(cols) - 1)], [[brk(c) for c in cols]] + [[short(r[c]) for c in cols] for r in m['rows']], keep=True))
    return out


def status_table():
    from collections import defaultdict
    g = defaultdict(list)
    for x in TT:
        if x['v'] is not None:
            g[(x['t'], x['c'])].append(f"{x['v']} ({x['n']})")
    rows = [['Bảng', 'Cột', 'Giá trị (số dòng)']] + [[t, c, ', '.join(v)] for (t, c), v in g.items()]
    return [tc_next('Phân bố trạng thái của dữ liệu sinh ra'), T([2500, 1900, 4672], rows)]


UC = [
    ('UC01', 'Đăng nhập', 'Mọi nhân viên', 'Nhân viên đăng nhập để dùng chức năng theo vai trò.', 'Có tài khoản.',
     'Nhận token, vào màn hình theo vai trò.',
     ['Nhập tên đăng nhập, mật khẩu.', 'Hệ thống kiểm bộ đếm sai, tài khoản và mật khẩu.', 'Hệ thống phát JWT, xóa bộ đếm sai.', 'Nếu phải đổi mật khẩu: chuyển màn đổi mật khẩu.'],
     ['Sai tên hoặc mật khẩu: báo lỗi chung, tăng bộ đếm sai.', 'Sai 5 lần trong 15 phút: khóa tạm 15 phút (429).', 'Tài khoản bị khóa hoặc nhân viên nghỉ việc: từ chối.']),
    ('UC02', 'Mở bàn và gọi món', 'Phục vụ', 'Phục vụ mở phiên cho khách và gửi món xuống bếp theo nhiều đợt.', 'Đã đăng nhập; thực đơn hôm nay đã công bố.',
     'Có phiên OPEN, đơn OPEN và các dòng món PENDING.',
     ['Chọn bàn trống, mở bàn.', 'Chọn món, số lượng, ghi chú.', 'Gửi đợt gọi món.', 'Hệ thống kiểm món đang bán, đủ nguyên liệu; tạo dòng món PENDING với giá chụp tại thời điểm gọi.'],
     ['Bàn đã có khách: 409.', 'Món tạm dừng hoặc không đủ nguyên liệu: 409, không tạo đợt.', 'Gửi lặp cùng requestKey: trả lại đợt cũ.', 'Vượt hạn mức: vẫn gửi, kèm cảnh báo.']),
    ('UC03', 'Bếp xử lý món', 'Bếp', 'Bếp nhận món, nấu và báo xong để phục vụ mang ra.', 'Có món PENDING trong hàng đợi.',
     'Món READY; nguyên liệu đã trừ đúng một lần.',
     ['Xem hàng đợi (món cũ nhất trước).', 'Nhận món: hệ thống chiếm suất, giữ nguyên liệu FEFO.', 'Bắt đầu nấu: trừ phần đã giữ, ghi sổ kho.', 'Báo nấu xong.'],
     ['Thiếu suất hoặc nguyên liệu: món REJECTED kèm lý do.', 'Bếp từ chối: nhập lý do, món REJECTED.', 'Nhận lại món đã nhận: 409.']),
    ('UC04', 'Chốt hóa đơn và thu tiền mặt', 'Thu ngân', 'Thu ngân lập hóa đơn và thu tiền mặt của khách.', 'Mọi món đã kết thúc, có món đã phục vụ.',
     'Hóa đơn PAID, phiên đóng, bàn trống.',
     ['Chốt hóa đơn: hệ thống tính tổng từ các món đã phục vụ.', 'Nhập tiền khách đưa.', 'Hệ thống ghi lần thu, tính tiền thối, đóng phiên, trả bàn.'],
     ['Còn món chưa xong: 409.', 'Tiền đưa thiếu: 422.', 'Thu lần hai hoặc hai người cùng bấm: chỉ một lần thành công.']),
    ('UC05', 'Thanh toán chuyển khoản QR', 'Thu ngân, SePay', 'Khách quét QR chuyển khoản; SePay báo về để hệ thống tự ghi nhận.', 'Hóa đơn PAYMENT_PENDING.',
     'Hóa đơn PAID hoặc sự cố được ghi để quản lý xử lý.',
     ['Thu ngân chọn QR: hệ thống tạo yêu cầu WAITING_TRANSFER, mã tham chiếu, ảnh QR.', 'Khách chuyển khoản.', 'SePay gửi webhook.', 'Hệ thống đối chiếu tài khoản, mã, số tiền; ghi lần thu, đóng phiên.'],
     ['Sai API key: 401.', 'Lệch tiền, không thấy mã: sự cố MISMATCH.', 'Yêu cầu đã hủy: sự cố AFTER_CANCEL.', 'Webhook gửi lặp: trả kết quả cũ.']),
    ('UC06', 'Nhập kho và xuất cho bếp', 'Quản lý, Bếp', 'Nhập nguyên liệu theo lô, xuất cho bếp theo FEFO, bếp xác nhận nhận hàng.', 'Đã khai báo nguyên liệu và loại dùng ở bếp.',
     'Có lô RAW; bếp có lô dùng thẳng hoặc hàng chờ sơ chế.',
     ['Lập phiếu nhập (lô, hạn dùng, quy đổi đơn vị, đơn giá).', 'Xác nhận: tạo lô RAW, ghi sổ.', 'Lập và xuất phiếu xuất theo FEFO.', 'Bếp nhận hàng.'],
     ['Lô quá hạn: 422.', 'Xác nhận lặp: 409.', 'Sai thứ tự FEFO hoặc vượt tồn: 409.', 'Bếp lập phiếu nhập: 403.']),
    ('UC07', 'Phân ca và chấm công', 'Quản lý', 'Lập lịch làm việc, chấm công và điều chỉnh công.', 'Đã khai báo ca làm.',
     'Phân ca COMPLETED có chấm công; lịch sử điều chỉnh được lưu.',
     ['Chọn nhân viên, ca, ngày.', 'Hệ thống kiểm trùng giờ, tạo phân ca SCHEDULED.', 'Sau ca: chấm công có mặt hoặc vắng.', 'Khi cần: điều chỉnh công kèm lý do.'],
     ['Trùng giờ ca khác: 409 OVERLAP.', 'Nhân viên không ACTIVE hoặc ca ngừng: 422.', 'Chấm lặp: 409.']),
]

ACTIVITY = [
    ('act-dang-nhap', 'đăng nhập', 'Hệ thống kiểm tên đăng nhập có đang bị khóa tạm không trước khi kiểm mật khẩu, để kẻ dò mật khẩu không thử tiếp được. Sai tên hay sai mật khẩu đều trả cùng thông báo. Tài khoản mới phải đổi mật khẩu trước khi dùng chức năng khác; đổi xong thì token cũ hết hiệu lực.'),
    ('act-goi-mon', 'mở bàn và gọi món', 'Mỗi đợt gọi món mang một requestKey: mạng chập chờn khiến phục vụ bấm gửi lại thì hệ thống trả lại đợt cũ, không tạo món trùng. Món chỉ được nhận khi đang bán và đủ nguyên liệu; vượt hạn mức suất vẫn gửi được để bếp quyết định, kèm cảnh báo.'),
    ('act-che-bien', 'bếp xử lý món', 'Nhận món là lúc hệ thống chiếm suất và giữ nguyên liệu theo lô hết hạn sớm nhất. Nấu là lúc trừ thật nguyên liệu và ghi sổ kho, đúng một lần. Thiếu suất hoặc nguyên liệu thì món bị từ chối kèm lý do, phục vụ báo khách đổi món.'),
    ('act-thanh-toan-tien-mat', 'chốt hóa đơn và thu tiền mặt', 'Tổng tiền do hệ thống tính từ các món đã phục vụ, theo giá chụp lúc gọi. Lần thu khóa hóa đơn trong giao dịch, nên hai thu ngân bấm cùng lúc chỉ một người thu được.'),
    ('act-thanh-toan-qr', 'thanh toán chuyển khoản QR', 'Luồng có bốn bên: thu ngân, hệ thống, khách và SePay. Hệ thống chỉ ghi nhận tiền khi webhook đúng API key, đúng tài khoản nhận, đúng mã tham chiếu và số tiền; mọi trường hợp lệch đều thành sự cố để quản lý xử lý, không tự đóng hóa đơn.'),
    ('act-kho', 'nhập kho, xuất cho bếp, bếp nhận', 'Phiếu đi qua các trạng thái DRAFT, CONFIRMED/ISSUED, RECEIVED. Xuất kho bắt buộc chọn lô hết hạn sớm nhất. Hàng dùng thẳng thành lô bếp; hàng cần sơ chế nằm ở khu chờ sơ chế cho tới khi bếp lập phiếu sơ chế.'),
    ('act-cham-cong', 'phân ca và chấm công', 'Phân ca chụp lại giờ bắt đầu, kết thúc theo giờ Việt Nam để sau này đổi giờ ca không làm sai lịch cũ. Mỗi lần điều chỉnh công lưu lại giá trị cũ và mới.'),
]


DBD = [('p1a-tai-khoan', 'nhân sự: nhân viên, tài khoản, phân quyền'), ('p1b-ca-lam', 'nhân sự: ca làm, chấm công'),
       ('p2-thuc-don', 'thực đơn'), ('p3a-goi-mon', 'bán hàng: bàn, phiên, gọi món'), ('p3b-thanh-toan', 'bán hàng: hóa đơn, thanh toán'),
       ('p4a-nhap-xuat', 'kho: nhập kho, xuất cho bếp'), ('p4b-bep', 'kho: sơ chế, lô bếp, giữ nguyên liệu, hoàn trả'),
       ('p4c-kiem-ke', 'kho: hủy hàng, kiểm kê, sổ kho'), ('p5-tai-chinh', 'tài chính')]


def chapter3():
    U = f'{R}/uml'
    E = f'{R}/erd'
    C = f'{R}/code-anh'
    out = [H1('3. Phân tích và thiết kế'),
           H2('3.1. Thiết kế cơ sở dữ liệu'),
           B(f'Cơ sở dữ liệu PostgreSQL gồm {len(CAT)} bảng, chia theo 5 phân hệ. Các mục dưới đây không viết tay mà sinh từ catalog của chính database đang chạy, nên luôn khớp với schema thật.'),
           H3('3.1.1. Thực thể, thuộc tính và khóa chính'),
           B('Mỗi bảng là một thực thể; khóa chính là cột id (số tự tăng), riêng các bảng nối như account_roles dùng khóa ghép. Bảng dưới liệt kê thực thể của từng phân hệ, ý nghĩa, khóa chính và các bảng được tham chiếu.')]
    out += entity_tables()
    out += [B('Thuộc tính chi tiết (tên cột, kiểu, bắt buộc, khóa) của các thực thể trung tâm:')]
    out += attribute_tables()
    out += [H3('3.1.2. Mối quan hệ'),
            B('Quan hệ được thể hiện bằng khóa ngoại. Phần lớn là quan hệ nhiều - một (nhiều dòng bảng con thuộc một dòng bảng cha), ví dụ nhiều order_items thuộc một order_batches. Quan hệ một - một khi khóa ngoại có ràng buộc UNIQUE, ví dụ mỗi table_sessions có đúng một orders. Quan hệ nhiều - nhiều được tách bằng bảng nối, ví dụ accounts và roles qua account_roles. Cột "Bắt buộc" cho biết khóa ngoại có cho phép NULL hay không.')]
    out += relation_table()
    out += [H3('3.1.3. Chuẩn hóa dạng 3NF'),
            B('Thiết kế được kiểm theo ba dạng chuẩn của mô hình quan hệ [14]:'),
            L('1NF: mọi cột chứa giá trị nguyên tố, không có nhóm lặp. Ví dụ một đợt gọi nhiều món không lưu danh sách món trong một cột mà tách thành nhiều dòng order_items.'),
            L('2NF: mọi bảng có khóa chính một cột id, nên không có phụ thuộc vào một phần khóa. Các bảng nối khóa ghép (account_roles) chỉ chứa hai cột khóa.'),
            L('3NF: không có thuộc tính phụ thuộc bắc cầu vào khóa qua một thuộc tính không khóa. Ví dụ tên phòng ban không lưu trong employees mà ở departments, employees chỉ giữ department_id; tên vai trò ở roles, không lặp ở accounts.'),
            B('Có ba chỗ cố ý lưu bản chụp, không vi phạm 3NF vì đó là dữ liệu lịch sử tại một thời điểm chứ không phải bản sao phải đồng bộ:'),
            L('order_items.item_name_snapshot, unit_price: tên và giá món lúc khách gọi. Quản lý đổi giá sau đó không làm sai hóa đơn cũ.'),
            L('invoice_items và invoices.total: số tiền chốt lúc lập hóa đơn, là chứng từ kế toán không được đổi.'),
            L('shift_assignments lưu giờ bắt đầu, kết thúc thực tế của ca tại ngày làm, để đổi giờ ca mẫu không làm sai lịch đã phân.'),
            H3('3.1.4. Sinh mã sơ đồ cơ sở dữ liệu bằng code'),
            B('Sơ đồ cơ sở dữ liệu không vẽ tay mà viết bằng DBML, ngôn ngữ mô tả schema của dbdiagram.io [16], và mã DBML cũng được sinh bằng code từ database thật qua ba bước:'),
            numbered(['Câu truy vấn catalog.sql đọc bảng, cột, kiểu dữ liệu, khóa chính, khóa ngoại (theo đúng thứ tự cột) từ pg_catalog của PostgreSQL và xuất JSON.',
                      'Script gen_dbml.py đọc JSON, viết mỗi cụm nghiệp vụ một file .dbml: khai báo Table với cột, kiểu, pk, not null, unique; mỗi khóa ngoại một dòng Ref với ký hiệu quan hệ của DBML: ">" nhiều - một, "-" một - một, thêm "?" khi cột khóa ngoại cho phép NULL (quan hệ tùy chọn).',
                      'Script render_dbdiagram.py dùng Playwright mở dbdiagram.io, dán mã DBML, kiểm tra không có lỗi cú pháp, chọn thuật toán tự sắp xếp Pipeline của dbdiagram rồi chụp sơ đồ. Đổi schema chỉ cần chạy lại ba bước là sơ đồ khớp.']),
            fig(f'{C}/gen-01-catalog-sql.png', 'Câu truy vấn catalog.sql đọc cấu trúc database', maxw=5760000),
            fig(f'{C}/gen-02-gen-dbml.png', 'gen_dbml.py: sinh dòng Ref theo loại quan hệ', maxw=5760000),
            fig(f'{C}/gen-02b-dbml-mau.png', 'Mã DBML sinh ra cho phân hệ Thực đơn', maxw=5760000, maxh=8000000),
            fig(f'{C}/gen-02c-render-dbdiagram.png', 'render_dbdiagram.py: dán DBML vào dbdiagram.io và chụp sơ đồ', maxw=5760000),
            H3('3.1.5. Sơ đồ thực thể liên kết (ERD) và database diagram'),
            B('Sơ đồ do dbdiagram.io vẽ từ mã DBML ở mục 3.1.4, dùng ký hiệu chân chim: đầu một gạch là phía "một", đầu chạc ba là phía "nhiều", vòng tròn là quan hệ tùy chọn (cột khóa ngoại cho phép NULL). Mỗi bảng ghi đủ cột và kiểu dữ liệu PostgreSQL; biểu tượng chìa khóa là khóa chính, biểu tượng mắt xích là khóa ngoại, NN là bắt buộc (NOT NULL).'),
            B('Để sơ đồ đọc được, 49 bảng được chia thành 9 cụm nghiệp vụ, mỗi cụm 2 đến 8 bảng. Bảng của cụm khác mà cụm đang xét trỏ tới được vẽ thêm, chỉ có khóa chính. Các cột ghi người thao tác (created_by_id, confirmed_by_id...) tới employees có ở hầu hết bảng nên chỉ vẽ đường nối trong cụm Nhân sự; đầy đủ mọi quan hệ ở bảng quan hệ của mục 3.1.2.')]
    for key, name in DBD:
        xoay = f'{E}/dbml-{key}-xoay.png'      # sơ đồ dàn ngang: xoay dọc trang cho chữ to hơn
        out.append(fig(xoay if os.path.exists(xoay) else f'{E}/dbml-{key}.png', f'ERD {name}', maxw=5760000, maxh=7800000))
    out += [H3('3.1.6. Sinh dữ liệu bằng code'),
            B('Dữ liệu không chèn bằng câu INSERT tay. Script seed_baocao.py gọi REST API của chính hệ thống như người dùng thật: đăng nhập theo vai trò, tạo nhân viên, ca làm, nguyên liệu, phiếu nhập, xuất, thực đơn, rồi mô phỏng 105 lượt khách với đủ kịch bản: thu tiền mặt, QR thành công, QR đang chờ, lệch tiền, khách bỏ về, miễn thu, hủy món, bếp từ chối. Vì đi qua tầng Service, mọi dòng dữ liệu đều đã qua kiểm tra nghiệp vụ, trigger và máy trạng thái; dữ liệu không hợp lệ không thể lọt vào.'),
            fig(f'{C}/gen-03-seed-goi-api.png', 'Hàm call() và soft() của seed_baocao.py', maxw=5760000),
            fig(f'{C}/gen-04-seed-ban-hang.png', 'Đoạn sinh dữ liệu bán hàng nhiều kịch bản', maxw=5760000, maxh=8000000),
            B(f'Chạy một lần mất khoảng 35 giây, sinh {sum(x["n"] for x in MAU.values())} dòng trên {len(CAT)} bảng. Bảng dưới cho thấy dữ liệu phủ hầu hết các trạng thái của từng cột trạng thái:')]
    out += status_table()
    out += [B('Câu SQL lấy 5 dòng mẫu của từng bảng cũng sinh bằng code (gen_sample_sql.py) từ catalog, chọn 6 cột đầu, bỏ các cột kỹ thuật như mật khẩu băm, nội dung webhook thô.'),
            fig(f'{C}/gen-05-mau-sql.png', 'gen_sample_sql.py sinh câu truy vấn dữ liệu mẫu', maxw=5760000)]
    out += sample_tables()
    out += [H2('3.2. Thiết kế hệ thống'),
            H3('3.2.1. Biểu đồ use case tổng quát (mức khung)'),
            B('Mức khung cho thấy các nhóm chức năng lớn và tác nhân dùng chúng. Phục vụ, Bếp, Thu ngân, Quản lý đều là Nhân viên (quan hệ tổng quát hóa, mũi tên tam giác rỗng) nên cùng dùng use case Đăng nhập; mỗi vai trò nối với nhóm chức năng của mình bằng đường liền. SePay là hệ thống ngoài, tham gia nhóm thanh toán.'),
            fig(f'{U}/uc-tong-quat.png', 'Biểu đồ use case tổng quát', maxh=6500000),
            H3('3.2.2. Biểu đồ use case chi tiết (mức đỉnh)'),
            B('Mỗi phân hệ có một biểu đồ chi tiết theo ký hiệu UML: tác nhân hình người que nối với use case bằng đường liền; «include» là phần luôn thực hiện kèm, mũi tên nét đứt đi từ use case gốc tới phần được gộp; «extend» là phần chỉ xảy ra trong trường hợp riêng, mũi tên đi từ phần mở rộng về use case gốc.')]
    for key, name in [('ban-hang', 'phục vụ và bếp'), ('thanh-toan', 'hóa đơn và thanh toán'), ('nhan-su', 'nhân sự và bảo mật'), ('thuc-don', 'thực đơn'), ('kho', 'kho và bếp'), ('tai-chinh', 'tài chính')]:
        out.append(fig(f'{U}/uc-{key}.png', f'Use case chi tiết: {name}', maxh=7000000))
    out += [H3('3.2.3. Đặc tả use case chính')]
    for code, name, actor, desc, pre, post, flow, alt in UC:
        out += [tc_next(f'Đặc tả {code}: {name}'),
                T([2200, 6872], [['Mục', 'Nội dung'], ['Mã, tên', f'{code}: {name}'], ['Tác nhân', actor], ['Mô tả', desc],
                                 ['Tiền điều kiện', pre], ['Hậu điều kiện', post],
                                 ['Luồng chính', ' '.join(f'({i}) {s}' for i, s in enumerate(flow, 1))],
                                 ['Luồng ngoại lệ', ' '.join(f'({chr(96 + i)}) {s}' for i, s in enumerate(alt, 1))]])]
    out += [H3('3.2.4. Biểu đồ hoạt động (activity)'),
            B('Mỗi use case chính có một biểu đồ hoạt động, chia làn (swimlane) theo người hoặc hệ thống thực hiện bước đó.')]
    for key, name, text in ACTIVITY:
        out += [B(f'Activity {name}: {text}'), fig(f'{U}/{key}.png', f'Activity: {name}', maxh=7600000)]
    out += [H3('3.2.5. Biểu đồ lớp theo kiến trúc dự án'),
            B('Hệ thống không dùng MVC kiểu máy chủ tự sinh giao diện. Kiến trúc gồm hai phần tách biệt giao tiếp qua REST/JSON:'),
            L('Frontend là ứng dụng một trang (SPA) React: các trang trong frontend/src/pages chỉ hiển thị và gọi API qua api/client.ts, không chứa nghiệp vụ.'),
            L('Backend là Modular Monolith: một ứng dụng Spring Boot chia thành các module theo nghiệp vụ, ranh giới module do Spring Modulith kiểm tra [15]:'),
            L2('common (phản hồi chung, xử lý lỗi, khóa chống gửi trùng, đo thời gian bằng AOP) và security (JWT, người dùng hiện tại) dùng chung.'),
            L2('Năm module nghiệp vụ employee, menu, order, inventory, finance; mỗi module sở hữu một nhóm bảng, chỉ module chủ được ghi bảng đó.'),
            L2('Module khác chỉ được gọi interface ở package gốc của module chủ, không được dùng package con (controller, service, repository, entity, dto).'),
            B('Phụ thuộc giữa các module đi một chiều menu ← inventory ← order ← finance, không có vòng. Khi module phía trước cần dữ liệu của module phía sau, module phía trước khai báo interface và module phía sau cài đặt (đảo phụ thuộc). Ví dụ order khai báo OrderInvoiceAccess để hỏi trạng thái hóa đơn, finance cài đặt bằng OrderInvoiceAccessImpl; nhờ vậy order không phụ thuộc finance. Lớp ModularityTest kiểm các luật này mỗi lần build, vi phạm thì build đỏ.'),
            fig(f'{U}/class-kien-truc-xoay.png', 'Biểu đồ lớp kiến trúc Modular Monolith và giao tiếp giữa các module', maxw=5760000, maxh=7800000),
            B('Bên trong mỗi module, mã chia tầng Controller → Service → Repository → Entity:'),
            L('Controller (@RestController): nhận request, kiểm dữ liệu bằng @Valid, kiểm quyền bằng @PreAuthorize, lấy người thực hiện từ token qua CurrentUser rồi gọi Service. Không truy cập database.'),
            L('Service: interface liệt kê nghiệp vụ, lớp Impl cài đặt, mỗi phương thức ghi là một giao dịch @Transactional; nghiệp vụ chỉ nằm ở tầng này.'),
            L('Repository (Spring Data JPA): đọc ghi bảng; các truy vấn khóa dòng có hậu tố ForUpdate. Truy vấn chỉ đọc ghép nhiều bảng nằm ở QueryRepository.'),
            L('Entity: lớp ánh xạ bảng; DTO dạng record tách dữ liệu vào ra API khỏi Entity.'),
            fig(f'{U}/class-ban-hang-xoay.png', 'Biểu đồ lớp các tầng trong module order (gọi món, bếp)', maxw=5760000, maxh=7800000),
            B('Quan hệ hợp thành CustomerOrder 1 - n OrderBatch 1 - n OrderItem khớp với bảng orders, order_batches, order_items. OrderItemStatusChanger gom việc đổi trạng thái món và ghi nhật ký order_item_events vào một chỗ, để mọi lần đổi trạng thái đều đi đúng máy trạng thái.'),
            B('Phần thanh toán dùng mẫu Strategy [13]: PaymentServiceImpl giữ một Map<PaymentMethod, PaymentStrategy>; Spring tiêm mọi lớp cài PaymentStrategy (CashPaymentStrategy, QrPaymentStrategy), nên thêm phương thức thanh toán mới chỉ cần thêm một lớp, không sửa PaymentServiceImpl. Tiền chuyển khoản đi đường riêng: SePay gọi webhook, SepayWebhookService kiểm khóa API rồi chuyển QrTransferProcessor ghi nhận; cả hai đường thu đều kết thúc ở InvoiceSettlement, nơi đóng hóa đơn và gọi OrderBillingApi để đóng đơn, trả bàn.'),
            fig(f'{U}/class-thanh-toan.png', 'Biểu đồ lớp thanh toán (mẫu Strategy)', maxw=5760000),
            H3('3.2.6. Biểu đồ triển khai'),
            B('Trình duyệt kết nối HTTPS tới nginx ở máy 1; nginx chuyển vào Traefik trong cụm Kubernetes (k3s); Traefik đưa / tới pod giao diện và /api tới pod backend; backend kết nối PostgreSQL. Máy 2 chứa công cụ: Jenkins kiểm thử và đóng gói, Argo CD triển khai theo Git, bộ giám sát, kho sao lưu. Chi tiết ở mục 4.4.'),
            fig(f'{U}/deploy-xoay.png', 'Biểu đồ triển khai', maxw=5760000, maxh=7800000)]
    return out
