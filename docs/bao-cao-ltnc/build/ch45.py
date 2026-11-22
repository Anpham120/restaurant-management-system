"""Chương 4 (triển khai) và chương 5 (kết quả, kết luận), tài liệu tham khảo."""
import json, os
from ltnc_lib import *

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = 'https://docs.google.com/spreadsheets/d/1VI1XD4DCVjGwWdVOkKhn3D6lPUqS4fjdb_ct4YBF5H8/edit'
REPO = 'https://github.com/Anpham120/restaurant-management-system'
TCS = json.load(open(f'{R}/test/ket-qua.json', encoding='utf-8'))
_t, _f = [0], [0]


def tc4(title, ch=4):
    _t[0] += 1
    return TC(f'Bảng {ch}.{_t[0]}. {title}')


_fc = {}


def fig4(path, title, ch=4, **k):
    _fc[ch] = _fc.get(ch, 0) + 1
    return FIG(path, f'Hình {ch}.{_fc[ch]}. {title}', **k)


CODE = [
    ('tv-1-an-qr-sepay', 'Phạm Duy An', 'Ghi nhận tiền chuyển khoản QR: QrTransferProcessor.recordVerifiedTransfer',
     'Đây là chỗ hệ thống nhận tiền thật từ ngân hàng: khách quét QR chuyển khoản, SePay gọi webhook, phương thức này quyết định có ghi nhận đã thu hay không. Sai ở đây là mất tiền hoặc thu hai lần, nên mọi trường hợp bất thường đều phải được xử lý.',
     ['Dòng 44: @Transactional, ghi sự kiện, lần thu, đóng hóa đơn trong cùng một giao dịch.',
      'Dòng 50–54: chống ghi trùng. SePay có thể gửi lại cùng một giao dịch nhiều lần; lockTransactionCode dùng pg_advisory_xact_lock khóa theo mã giao dịch, lần gửi sau thấy kết quả đã ghi thì trả lại kết quả cũ, không thu lần hai.',
      'Dòng 58–63: tìm hóa đơn theo mã tham chiếu in trên QR (dạng RC<hóa đơn>Q<n>), khóa hóa đơn và yêu cầu thanh toán để thu ngân không thu tiền mặt chen vào cùng lúc.',
      'Dòng 65 và 79–90: decideResult phân loại kết quả. Không thấy mã tham chiếu hoặc lệch số tiền thì MISMATCH; yêu cầu QR đã hủy hoặc hóa đơn không còn chờ thu thì AFTER_CANCEL; tiền vào sai tài khoản nhà hàng cũng là MISMATCH. Chỉ khi đúng hết mới PROCESSED.',
      'Dòng 67–68: mọi giao dịch, kể cả bất thường, đều lưu vào payment_webhook_events kèm nội dung gốc, để quản lý đối soát ở màn hình sự cố chuyển khoản.',
      'Dòng 69–75: PROCESSED thì ghi Payment, đánh dấu yêu cầu đã thu, rồi InvoiceSettlement đóng hóa đơn, đóng đơn và trả bàn qua OrderBillingApi. Màn hình thu ngân tự chuyển sang đã thu mà không cần bấm gì.']),
    ('tv-2-hieu-dang-nhap', 'Nguyễn Quang Hiếu', 'Đăng nhập: AuthServiceImpl.login',
     'Cửa vào của hệ thống, quyết định ai được dùng chức năng nào. Đoạn code chặn dò mật khẩu và không để lộ tài khoản nào tồn tại.',
     ['Dòng 50–54: kiểm khóa tạm trước khi kiểm mật khẩu; sai quá 5 lần thì trả 429, kể cả khi lần này nhập đúng.',
      'Dòng 55–56: không thấy tài khoản vẫn so mật khẩu với một mã băm giả (dummyHash), để thời gian phản hồi như nhau, kẻ tấn công không đoán được tên đăng nhập có tồn tại hay không.',
      'Dòng 57–63: tài khoản khóa, nhân viên nghỉ việc, không có vai trò đều trả cùng một lỗi INVALID_CREDENTIALS và tăng bộ đếm sai (invalidCredentialsError).',
      'Dòng 64–67: đăng nhập đúng thì xóa bộ đếm, phát JWT chứa vai trò và tokenVersion; đổi mật khẩu hay khóa tài khoản làm tăng tokenVersion nên token cũ hết hiệu lực ngay.']),
    ('tv-3-le-anh-han-muc', 'Lê Anh', 'Đổi hạn mức suất bán: DailyMenuServiceImpl.updateDailyMenuQuotas',
     'Hạn mức suất quyết định bếp còn nhận được bao nhiêu món. Đoạn code bảo đảm đổi hạn mức hàng loạt mà không làm sai số suất đã bán và không ghi đè thao tác tạm dừng món của bếp.',
     ['Dòng 161–163: chỉ đổi khi thực đơn ngày chưa đóng; cả lần đổi là một giao dịch.',
      'Dòng 164–166: findAllByIdForUpdate khóa mọi món cần đổi trước khi đọc. Nếu không khóa, bản đọc cũ có thể ghi đè trạng thái tạm dừng do bếp vừa bật.',
      'Dòng 167–172: món không thuộc thực đơn thì báo lỗi và không đổi dòng nào (tất cả hoặc không gì).',
      'Dòng 295–303: changeQuotaNotBelowUsed hỏi module order số suất đã nhận qua interface QuotaUsageCounter; hạn mức mới thấp hơn số đã nhận thì từ chối.']),
    ('tv-4-tuan-anh-fefo', 'Đỗ Tuấn Anh', 'Giữ nguyên liệu theo FEFO: KitchenStockAllocatorImpl',
     'Khi bếp nhận món, kho phải giữ đúng lượng nguyên liệu theo công thức, ưu tiên lô sắp hết hạn để giảm hàng hỏng. Đây là thuật toán lập kế hoạch rồi mới ghi.',
     ['Dòng 51–55: lấy công thức món qua interface DailyMenuLookup của module menu, khóa các lô còn dùng được (truy vấn @Lock PESSIMISTIC_WRITE), rồi lập kế hoạch.',
      'Dòng 126–151: với mỗi nguyên liệu tính lượng cần = định lượng một suất × số suất, trừ phần đã giữ; lọc lô còn hạn, còn tồn, sắp xếp FEFO rồi lấy dần từng lô tới khi đủ. Thiếu thì trả lý do, món bị từ chối.',
      'Dòng 153–161: chỉ khi mọi nguyên liệu đều đủ mới ghi phần giữ vào lô và bảng stock_reservations, nên không bao giờ giữ dở dang.',
      'Dòng 166–175: bộ so sánh earliestExpiryFirst: hạn dùng sớm trước, lô không có hạn để cuối, cùng hạn thì lô nhập trước (id nhỏ) trước.']),
    ('tv-5-duc-anh-doi-soat', 'Bùi Đào Đức Anh', 'Đối soát tiền mặt cuối ngày: CashReconciliationServiceImpl.reconcileCashForDay',
     'Cuối ngày quản lý đếm tiền trong két và so với sổ. Đoạn code tính số tiền đúng ra phải có và lưu lịch sử mỗi lần lập lại.',
     ['Dòng 34–39: không đối soát ngày ở tương lai, bắt buộc có quỹ đầu ngày và số tiền thực đếm.',
      'Dòng 40: lockBusinessDate dùng pg_advisory_xact_lock khóa theo ngày, hai quản lý lập cùng ngày thì chạy lần lượt, không trùng phiên bản.',
      'Dòng 41–46: phiên bản mới = phiên bản lớn nhất + 1; lập lại (phiên bản từ 2) bắt buộc có lý do lập lại.',
      'Dòng 47–53: tiền sổ sách = quỹ đầu ngày + tiền mặt thu trong ngày − chi từ két. Tiền đếm lệch sổ mà không ghi lý do thì từ chối.',
      'Dòng 54–59: bản hiện hành chuyển SUPERSEDED, ghi bản mới CURRENT, nên luôn xem lại được các lần đối soát trước.']),
]

UI = [
    ('01-dang-nhap', 'Màn hình đăng nhập', 'Nhập tên đăng nhập và mật khẩu; tài khoản mới được chuyển sang màn đổi mật khẩu.'),
    ('02-tong-quan', 'Trang tổng quan của quản lý', 'Doanh thu hôm nay, số bàn đang phục vụ, việc cần xử lý (món chờ lâu, hóa đơn chờ thu, sự cố chuyển khoản), món bán chạy so với hạn mức.'),
    ('03-so-do-ban', 'Sơ đồ bàn', 'Mỗi ô là một bàn: trống, đang có khách, chờ thanh toán, kèm số món đang chờ và tiền tạm tính. Bấm bàn trống để mở bàn.'),
    ('04-goi-mon', 'Màn hình gọi món', 'Bên trái là thực đơn hôm nay (món tạm dừng, hết suất bị làm mờ), bên phải là các đợt đã gửi với trạng thái từng món; phục vụ bưng món READY, hủy món chưa nấu.'),
    ('05-bep', 'Màn hình bếp', 'Các cột chờ nhận, đang làm, xong chờ bưng; thẻ món đổi màu khi chờ quá 10 và 15 phút.'),
    ('06-hoa-don-cho-thu', 'Hóa đơn chờ thu với mã QR', 'Thu ngân chọn QR: hệ thống hiện ảnh QR kèm mã tham chiếu, màn hình tự chuyển sang đã thu khi SePay báo về.'),
    ('07-hoa-don-da-thu', 'Hóa đơn đã thanh toán', 'Chi tiết dòng món, tổng tiền, tiền khách đưa và tiền thối.'),
    ('08-mon-an', 'Quản lý món ăn', 'Danh sách món theo nhóm, giá, phiên bản công thức; thêm món, đổi giá, khai báo công thức.'),
    ('09-thuc-don-ngay', 'Thực đơn ngày', 'Món đang bán hôm nay, hạn mức, số suất đã bán, còn lại, tạm dừng.'),
    ('11-phieu-nhap', 'Phiếu nhập kho', 'Danh sách phiếu nhập theo trạng thái; lập phiếu theo lô, hạn dùng, đơn vị nhập và quy đổi.'),
    ('13-ton-kho', 'Tồn kho và cảnh báo hạn dùng', 'Lô nguyên liệu sắp hết hạn, dưới mức tối thiểu.'),
    ('14-nhan-vien', 'Quản lý nhân viên', 'Danh sách nhân viên, phòng ban, vai trò, trạng thái tài khoản; thêm, khóa, đặt lại mật khẩu.'),
    ('15-ca-lam', 'Lịch phân ca theo tuần', 'Mỗi cột một ngày, mỗi thẻ một ca của một nhân viên.'),
    ('17-khoan-chi', 'Khoản chi', 'Ghi chi theo nhóm, nguồn tiền, phương thức; hủy khoản chi ghi nhầm.'),
    ('18-doi-soat', 'Đối soát tiền mặt', 'So tiền đếm thực tế với sổ (đầu ca + thu tiền mặt - chi từ két); lệch phải ghi lý do, lập lại tạo phiên bản mới.'),
    ('19-bao-cao', 'Báo cáo doanh thu', 'Doanh thu theo ngày, theo món, hóa đơn không thu được, chi theo nhóm.'),
    ('20-su-co-thanh-toan', 'Sự cố chuyển khoản', 'Giao dịch lệch tiền hoặc đến sau khi hủy yêu cầu; quản lý ghi chú và đánh dấu đã xử lý.'),
]


def chapter4():
    C = f'{R}/code-anh'
    out = [H1('4. Triển khai'),
           H2('4.1. Kế hoạch và phân công'),
           B('Nhóm phân công theo vòng đời nghiệp vụ: mỗi người chịu trách nhiệm chính một phân hệ, gồm cả code backend, giao diện và kiểm thử của phân hệ đó. Nhóm trưởng điều phối kiểm thử tích hợp và duyệt các thay đổi dùng chung.'),
           tc4('Phân công và đánh giá thành viên'),
           T([600, 2000, 1450, 3122, 950, 950], [['TT', 'Họ tên', 'MSSV', 'Nhiệm vụ', 'Hoàn thành', 'Đánh giá'],
               ['1', 'Phạm Duy An', 'BIT240002', 'Nhóm trưởng. Vòng đời đơn hàng: bàn, phiên, gọi món, bếp, hóa đơn, thanh toán tiền mặt, QR SePay, webhook; điều phối kiểm thử tích hợp. Toàn bộ quy trình triển khai: máy chủ, CI/CD Jenkins, GitOps Argo CD trên Kubernetes k3s, reverse proxy nginx và Traefik, giám sát, cảnh báo, sao lưu và khôi phục', '…%', '…'],
               ['2', 'Bùi Đào Đức Anh', 'BIT240025', 'Tài chính: khoản chi, đối soát tiền mặt, báo cáo doanh thu', '…%', '…'],
               ['3', 'Đỗ Tuấn Anh', 'BIT240015', 'Kho: nguyên liệu, nhập theo lô, xuất FEFO, bếp nhận, sơ chế, hoàn trả, hủy hàng, kiểm kê, báo cáo kho', '…%', '…'],
               ['4', 'Lê Anh', 'BIT240017', 'Thực đơn: nhóm món, món, công thức theo phiên bản, thực đơn ngày, hạn mức, tạm dừng món', '…%', '…'],
               ['5', 'Nguyễn Quang Hiếu', 'BIT240091', 'Nhân sự và bảo mật: nhân viên, tài khoản, Spring Security, JWT, phân quyền, ca làm, chấm công', '…%', '…']]),
           B('Mức đánh giá dùng thang Khá, Tốt theo mức độ hoàn thành nhiệm vụ.'),
           H2('4.2. Cài đặt chương trình'),
           B(f'Mã nguồn đặt tại {REPO} [10]. Backend chia package theo phân hệ (order, menu, inventory, employee, finance, security), mỗi package có controller, service (interface và impl), repository, entity, dto; ranh giới giữa các phân hệ được kiểm tự động bằng Spring Modulith. Frontend chia theo màn hình trong frontend/src/pages, dùng chung lớp gọi API frontend/src/api.'),
           B('Mỗi thành viên chọn một đoạn code tiêu biểu nhất trong phần việc của mình. Ảnh chụp trực tiếp từ mã nguồn, có số dòng để đối chiếu.')]
    for k, (key, who, title, why, notes) in enumerate(CODE, 1):
        out += [H3(f'4.2.{k}. {who}: {title.split(":")[0]}'), B(why)]
        if key in AN_FLOW:
            out += [B('Luồng thanh toán chuyển khoản đi qua cả frontend và backend (biểu đồ tuần tự ở mục 3.2.6):'), numbered(AN_FLOW[key]),
                    B('Backend: phương thức ghi nhận tiền khi SePay báo về.')]
        out += [fig4(f'{C}/{key}.png', title, maxw=5760000, maxh=8200000)]
        out += [L(n) for n in notes]
        if key in AN_FE:
            fkey, ftitle, fintro, fnotes = AN_FE[key]
            out += [B(fintro), fig4(f'{C}/{fkey}.png', ftitle, maxw=5760000, maxh=8200000)]
            out += [L(n) for n in fnotes]
            out += [B('Kết quả trên giao diện: màn hình chờ tiền với ảnh QR ở Hình 5.6, hóa đơn đã thu ở Hình 5.7. Các ca kiểm thử TC30 đến TC32 (webhook sai khóa API, sai mã tham chiếu, gửi lặp) ở mục 4.3 kiểm đúng các nhánh của đoạn code này.')]
    out += [H2('4.3. Kiểm thử'),
            H3('4.3.1. Kế hoạch kiểm thử'),
            tc4('Kế hoạch kiểm thử'),
            T([2300, 6772], [['Hạng mục', 'Nội dung'],
                ['Mục tiêu', 'Xác nhận mỗi chức năng làm đúng ở ca thành công, chặn đúng ở ca sai dữ liệu, sai quyền, sai trạng thái'],
                ['Phạm vi', 'Đăng nhập, phân quyền, nhân sự, thực đơn, bán hàng, bếp, thanh toán, kho, tài chính'],
                ['Loại kiểm thử', 'Kiểm thử chức năng hộp đen qua REST API (40 ca); kiểm thử tích hợp tự động JUnit + Testcontainers trên PostgreSQL thật (83 test, chạy mỗi lần đẩy code)'],
                ['Môi trường', 'Bản chạy riêng của hệ thống (Docker Compose) với dữ liệu sinh bởi seed_baocao.py; không chạy trên dữ liệu thật'],
                ['Công cụ', 'Script test/tc_runner.py (Python) gửi request, so output thực tế với output mong muốn, ghi kết quả; Google Sheet lưu bảng test case'],
                ['Tiêu chí đạt', 'Đúng mã HTTP và đúng kết quả nghiệp vụ (trạng thái, cờ replayed, kết quả webhook...)'],
                ['Thời gian', 'Chạy lại sau mỗi thay đổi nghiệp vụ; lần ghi trong báo cáo: 21/11/2026']]),
            tc4('Danh mục chức năng và trường hợp kiểm thử'),
            T([1800, 5572, 1700], [['Chức năng', 'Trường hợp kiểm', 'Số ca'],
                ['Đăng nhập', 'Thành công; sai mật khẩu; sai tên (cùng thông báo); bỏ trống; khóa sau 5 lần sai; tài khoản bị khóa', '6 (TC01–TC06)'],
                ['Phân quyền', 'Sai vai trò; không có token', '2 (TC07–TC08)'],
                ['Nhân sự', 'Thêm nhân viên; trùng tên đăng nhập; vai trò không tồn tại; phân ca; phân ca trùng giờ', '5 (TC09–TC13)'],
                ['Thực đơn', 'Thêm món; giá có phần lẻ; trùng tên nhóm món', '3 (TC14–TC16)'],
                ['Bán hàng, bếp', 'Mở bàn đang có khách; số lượng 0; món tạm dừng; gọi món; gửi trùng; chốt khi món chưa xong; bếp nhận; nhận lại; hủy món đang nấu; bưng món', '10 (TC17–TC26)'],
                ['Thanh toán', 'Thu thiếu; thu đủ; thu lần hai; webhook sai key; webhook lệch mã; webhook gửi lặp', '6 (TC27–TC32)'],
                ['Kho', 'Lập phiếu nhập (và gửi lặp); xác nhận; xác nhận lặp; sai vai trò', '4 (TC33–TC36)'],
                ['Tài chính', 'Sai nguồn tiền; hủy khoản chi; hủy lặp; đối soát ngày tương lai', '4 (TC37–TC40)']]),
            H3('4.3.2. Test case'),
            B('Bảng dưới trích 8 ca tiêu biểu. Đủ 40 ca với các cột mã, mục đích, input (giá trị cụ thể), output mong muốn, output thực tế, trạng thái, người phụ trách, ngày test, kết quả lần 1, lần 2 nằm ở Google Sheet [11]. Cả 40 ca đạt ngay lần chạy đầu nên cột kết quả lần 2 để trống.'),
            L(f'Google Sheet test case: {SHEET}'),
            L(f'Mã nguồn trên GitHub: {REPO}')]
    rows = [['Mã', 'Mục đích', 'Input', 'Output mong muốn', 'Output thực tế', 'Trạng thái'],
            ['TC01', 'Đăng nhập đúng', 'username=manager, mật khẩu đúng', '200, roles=[MANAGER]', '200, roles=[MANAGER]', 'Đạt'],
            ['TC05', 'Khóa sau 5 lần sai', 'sai mật khẩu 6 lần liên tiếp', '429 ở lần thứ 6', '429 TOO_MANY_ATTEMPTS', 'Đạt'],
            ['TC07', 'Sai vai trò', 'token WAITER, GET /api/reports/revenue-by-day', '403', '403 FORBIDDEN', 'Đạt'],
            ['TC13', 'Phân ca trùng giờ', 'cùng nhân viên, shiftId=1, ngày 11/12/2026, lần 2', '409 OVERLAP', '409 OVERLAP', 'Đạt'],
            ['TC21', 'Gửi lặp requestKey', 'gửi lại đúng body của TC20', '201, replayed=true', '201, replayed=true', 'Đạt'],
            ['TC27', 'Thu tiền mặt thiếu', 'invoiceId=74, tổng 50.000, đưa 10.000', '422', '422 INVALID_REQUEST', 'Đạt'],
            ['TC31', 'Webhook sai mã', 'API key đúng, code=RC1Q1 không tồn tại', '200, MISMATCH', '200, MISMATCH', 'Đạt'],
            ['TC35', 'Xác nhận phiếu lặp', 'receiptId=14 đã CONFIRMED', '409', '409 INVALID_STATE', 'Đạt']]
    out += [tc4('Test case tiêu biểu (đủ 40 ca ở Google Sheet)'), T([850, 1750, 2500, 1500, 1600, 872], rows, keep=True),
            fig4(f'{R}/test/ket-qua-test.png', 'Kết quả chạy 40 test case', maxw=5760000, maxh=8400000),
            fig4(f'{R}/giao-dien/anh/21-dang-nhap-sai.png', 'Kiểm thử trên giao diện: đăng nhập sai báo lỗi chung', maxw=5200000)]
    out += [H2('4.4. Triển khai lên máy chủ'),
            B('Toàn bộ quy trình triển khai do Phạm Duy An xây dựng và vận hành: chuẩn bị hai máy chủ và mạng riêng WireGuard, pipeline CI/CD trên Jenkins, triển khai GitOps bằng Argo CD lên cụm Kubernetes k3s, reverse proxy nginx và Traefik, giám sát và cảnh báo (Prometheus, Grafana, Loki, Alertmanager qua Telegram), sao lưu và khôi phục database.'),
            B('Hệ thống chạy tại https://rms.atlasplatform12.tech trên hai máy chủ Ubuntu 24.04. Mã nguồn trên GitHub; mỗi lần đẩy code, quy trình tự động thực hiện:'),
            numbered(['Lập trình viên đẩy nhánh feature lên GitHub; GitHub báo Jenkins (máy 2).',
                      'Jenkins kiểm tra migration, chạy test backend, test frontend, quét bảo mật mã nguồn; đạt thì tự gộp vào nhánh develop.',
                      'Trên develop, Jenkins dựng một bản chạy thử trên bản sao dữ liệu, chạy kiểm thử end-to-end, quét lỗ hổng image, rồi đẩy image lên GitHub Container Registry và ghi phiên bản image vào Git.',
                      'Argo CD đọc Git và triển khai lên cụm Kubernetes ở máy 1 theo kiểu blue-green: dựng bản mới bên cạnh bản cũ, kiểm tra xong mới chuyển người dùng sang; lỗi thì giữ bản cũ.',
                      'nginx ở máy 1 nhận HTTPS (chứng chỉ Let\'s Encrypt), chuyển vào Traefik; Traefik đưa / tới giao diện và /api tới backend.']),
            B('Database PostgreSQL chạy 2 bản (chính và dự phòng), sao lưu liên tục sang máy 2. Bộ giám sát Prometheus, Grafana, Loki ở máy 2 cảnh báo qua Telegram khi có sự cố. Thời gian từ lúc đẩy code tới khi lên production khoảng 6 phút, không có bước làm tay.')]
    return out


def chapter5():
    A = f'{R}/giao-dien/anh'
    out = [H1('5. Kết quả và hướng phát triển'),
           H2('5.1. Kết quả'),
           B('Các màn hình dưới chụp từ hệ thống đang chạy với dữ liệu sinh ở mục 3.1.5, đăng nhập bằng tài khoản quản lý.')]
    for key, title, text in UI:
        out += [fig4(f'{A}/{key}.png', title, ch=5, maxw=5760000), B(text)]
    out += [H3('5.1.1. Đã làm được'),
            L('Đủ 5 phân hệ với 27 nhóm chức năng, chạy thật trên domain công khai, dùng được trên máy tính và máy tính bảng.'),
            L('Nghiệp vụ đúng khi nhiều người thao tác cùng lúc: khóa dòng dữ liệu, chống gửi trùng bằng requestKey, một hóa đơn chỉ thu một lần.'),
            L('Thanh toán QR tự động qua webhook SePay, ghi sự cố khi lệch tiền.'),
            L('Kho theo lô, FEFO, trừ nguyên liệu tự động khi nấu; truy vết mọi biến động qua sổ kho.'),
            L('83 test tích hợp tự động và 40 test case chức năng đều đạt.'),
            H3('5.1.2. Chưa làm được'),
            L('Chưa có ứng dụng di động riêng và chức năng đặt bàn trước, gọi món qua mã QR tại bàn cho khách.'),
            L('Phân quyền chi tiết theo từng quyền (bảng permissions) mới thiết kế, chưa dùng; hiện phân quyền theo vai trò.'),
            L('Chưa in hóa đơn ra máy in nhiệt và chưa xuất báo cáo ra Excel.'),
            H2('5.2. Kết luận'),
            H3('5.2.1. Kiến thức đạt được'),
            L('Thiết kế cơ sở dữ liệu chuẩn hóa 3NF, dùng ràng buộc, khóa, trigger để bảo vệ dữ liệu; sinh dữ liệu mẫu bằng code qua API của hệ thống.'),
            L('Xây dựng ứng dụng Modular Monolith chia tầng Controller, Service, Repository với Spring Boot, Spring Modulith, Spring Data JPA, Spring Security; áp dụng mẫu Strategy, Repository.'),
            L('Xử lý đồng thời: giao dịch, khóa bi quan, idempotency; xác thực JWT có thu hồi token.'),
            L('Viết kiểm thử tự động và kiểm thử chức năng có ghi kết quả; làm việc nhóm qua Git với nhánh feature.'),
            H3('5.2.2. Ưu điểm và nhược điểm'),
            L('Ưu điểm:'),
            L2('Nghiệp vụ chặt chẽ, dữ liệu luôn nhất quán nhờ kiểm ở cả Service và cơ sở dữ liệu.'),
            L2('Giao diện tiếng Việt, tự làm mới, thông báo lỗi rõ nguyên nhân.'),
            L2('Triển khai tự động, có sao lưu và giám sát.'),
            L('Nhược điểm:'),
            L2('Số bảng nhiều nên người mới cần thời gian làm quen.'),
            L2('Giao diện chưa tối ưu cho điện thoại.'),
            L2('Báo cáo còn ít dạng biểu đồ.'),
            H3('5.2.3. Hướng phát triển'),
            L('Gọi món bằng mã QR tại bàn cho khách, đặt bàn trước.'),
            L('Ứng dụng di động cho phục vụ, thông báo đẩy khi món xong.'),
            L('Báo cáo nâng cao: lợi nhuận theo món (giá bán trừ giá vốn nguyên liệu), dự báo lượng nguyên liệu cần nhập.'),
            L('Phân quyền chi tiết theo quyền, nhật ký thao tác cho quản lý.')]
    return out



AN_FLOW = {'tv-1-an-qr-sepay': [
    'Thu ngân bấm "Tạo mã QR". Frontend gọi POST /api/invoices/{id}/payments với method QR; backend (QrPaymentStrategy) tạo yêu cầu WAITING_TRANSFER, mã tham chiếu dạng RC<hóa đơn>Q<n> và ảnh QR.',
    'Khách quét QR, chuyển khoản đúng số tiền với nội dung là mã tham chiếu. Trong lúc chờ, frontend cứ 3 giây tải lại hóa đơn.',
    'SePay gọi webhook POST /api/webhooks/sepay. SepayWebhookService kiểm khóa API, kiểm tiền vào đúng tài khoản nhà hàng, tách mã tham chiếu rồi gọi QrTransferProcessor.recordVerifiedTransfer.',
    'QrTransferProcessor chống ghi trùng, khóa hóa đơn, đối chiếu và ghi tiền; InvoiceSettlement đóng hóa đơn, đóng đơn, trả bàn.',
    'Lần tải lại kế tiếp frontend thấy hóa đơn PAID, ngừng tải lại và báo "Đã thu ... bằng chuyển khoản. Bàn đã được trả."']}

AN_FE = {'tv-1-an-qr-sepay': ('tv-1b-an-qr-fe', 'InvoicePage.tsx: tạo mã QR, chờ tiền về, báo đã thu',
    'Frontend: màn hình hóa đơn của thu ngân (React, TanStack Query).',
    ['Dòng 15 và 114: QR_OPEN là các trạng thái yêu cầu QR còn đang chờ; openQr tìm yêu cầu QR đang mở của hóa đơn để hiển thị.',
     'Dòng 39–46: useQuery tải hóa đơn. refetchInterval viết dạng hàm: còn yêu cầu QR đang chờ thì 3 giây tải lại một lần, hết chờ thì trả false để dừng. Nhờ vậy màn hình tự thấy kết quả webhook mà không cần WebSocket, và không tải lại thừa khi không chờ QR.',
     'Dòng 47–51: refresh đánh dấu dữ liệu hóa đơn, đơn hàng, sơ đồ bàn đã cũ (invalidateQueries) để các màn hình liên quan tải lại.',
     'Dòng 60–68: useMutation gửi yêu cầu thanh toán. Tiền mặt thì backend trả PAID ngay và hiện thông báo; QR thì lưu phản hồi (setQr) để hiện ảnh mã.',
     'Dòng 283–299: khi hóa đơn PAYMENT_PENDING và có QR đang mở thì hiện ảnh QR, số tiền, nội dung chuyển khoản, nhãn "Đang chờ tiền về" và nút hủy mã. Chưa cấu hình tài khoản nhận tiền thì báo rõ thay vì hiện ảnh hỏng.',
     'Dòng 199–204: khi hóa đơn PAID thì báo đã thu, số tiền, phương thức, giờ thu và bàn đã được trả. Đây là kết quả của InvoiceSettlement ở backend.'])}

REFS_LIST = [
    (1, 'https://docs.spring.io/spring-boot/', 'Spring, "Spring Boot Reference Documentation".'),
    (2, 'https://docs.spring.io/spring-data/jpa/reference/', 'Spring, "Spring Data JPA Reference Documentation".'),
    (3, 'https://docs.spring.io/spring-security/reference/', 'Spring, "Spring Security Reference".'),
    (4, 'https://datatracker.ietf.org/doc/html/rfc7519', 'M. Jones, J. Bradley, N. Sakimura, "JSON Web Token (JWT)", RFC 7519, IETF, 2015.'),
    (5, 'https://www.postgresql.org/docs/17/', 'PostgreSQL Global Development Group, "PostgreSQL 17 Documentation".'),
    (6, 'https://react.dev/', 'Meta, "React Documentation".'),
    (7, 'https://tanstack.com/query/latest', 'TanStack, "TanStack Query Documentation".'),
    (8, 'https://java.testcontainers.org/', 'Testcontainers, "Testcontainers for Java".'),
    (9, 'https://docs.sepay.vn/tich-hop-webhooks.html', 'SePay, "Tích hợp Webhooks".'),
    (10, 'https://github.com/Anpham120/restaurant-management-system', 'Nhóm thực hiện, mã nguồn hệ thống Atlas Restaurant (repo restaurant-management-system).'),
    (11, 'https://docs.google.com/spreadsheets/d/1VI1XD4DCVjGwWdVOkKhn3D6lPUqS4fjdb_ct4YBF5H8/edit', 'Nhóm thực hiện, bảng test case (Google Sheet).'),
    (12, 'https://www.oreilly.com/library/view/design-patterns-elements/0201633612/', 'E. Gamma, R. Helm, R. Johnson, J. Vlissides, Design Patterns: Elements of Reusable Object-Oriented Software. Addison-Wesley, 1994.'),
    (13, 'https://dl.acm.org/doi/10.1145/362384.362685', 'E. F. Codd, "A Relational Model of Data for Large Shared Data Banks", Communications of the ACM, 1970.'),
    (14, 'https://docs.spring.io/spring-modulith/reference/', 'Spring, "Spring Modulith Reference Documentation".'),
]


def references():
    for n, url, _ in REFS_LIST:
        REFS[n] = url
    return [H1('TÀI LIỆU THAM KHẢO')] + [REF(n, text) for n, _, text in REFS_LIST]
