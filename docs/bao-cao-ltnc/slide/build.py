"""Slide Lập trình nâng cao, dựng trên template Slide Triển khai phần mềm (giữ bìa, nền, logo, footer, màu, font)."""
import copy, io
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from PIL import Image

XANH, NAVY, CHU, PHU, NEN, TRANG = '0070C0', '0B2E59', '1F2D3D', '5A6B80', 'EAF3FC', 'FFFFFF'
XLA, XLA_NEN, DO = '2E7D32', 'E6F4EA', 'C62828'
rgb = lambda h: RGBColor.from_string(h)

prs = Presentation('mau.pptx')
SL = list(prs.slides)
BIA, CAM_ON = SL[0], SL[23]
ICON = {n: sh.image.blob for n, sh in zip(['spring', 'react', 'postgres'],
        [s for s in SL[3].shapes if s.shape_type == 13])}
# Dùng đúng layout mà slide mẫu đang dùng (template có nhiều master trùng tên layout)
LAYOUT = {'Title and Content': SL[1].slide_layout, 'Title Only': SL[2].slide_layout}

# ---- bỏ mọi slide nội dung cũ, giữ bìa và trang cảm ơn
lst = prs.slides._sldIdLst
for sid in list(lst):
    sl = prs.slides.get(int(sid.get('id')))
    if sl not in (BIA, CAM_ON):
        prs.part.drop_rel(sid.rId); lst.remove(sid)


def tb(s, x, y, w, h, paras, size=16, color=CHU, bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, name=None):
    """paras: list chuỗi hoặc (chuỗi, {size,color,bold,italic,bullet}). Mỗi phần tử một đoạn."""
    t = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    if name: t.name = name
    tf = t.text_frame; tf.word_wrap = True; tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = Inches(0.05); tf.margin_top = tf.margin_bottom = Inches(0.03)
    for i, p in enumerate(paras):
        txt, o = (p, {}) if isinstance(p, str) else p
        para = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        para.alignment = align
        if o.get('after') is not None: para.space_after = Pt(o['after'])
        else: para.space_after = Pt(4)
        if o.get('bullet'):
            pPr = para._p.get_or_add_pPr(); pPr.set('marL', '228600'); pPr.set('indent', '-228600')
            bu = pPr.makeelement('{http://schemas.openxmlformats.org/drawingml/2006/main}buChar', {'char': '•'})
            pPr.append(bu)
        r = para.add_run(); r.text = txt
        f = r.font; f.size = Pt(o.get('size', size)); f.bold = o.get('bold', bold); f.italic = o.get('italic', False)
        f.color.rgb = rgb(o.get('color', color))
    return t


def the(s, x, y, w, h, fill=NEN, line=None):
    r = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    r.adjustments[0] = 0.08
    r.fill.solid(); r.fill.fore_color.rgb = rgb(fill)
    if line: r.line.color.rgb = rgb(line); r.line.width = Pt(1)
    else: r.line.fill.background()
    r.shadow.inherit = False
    return r


def the_chu(s, x, y, w, h, tieu_de, dong, fill=NEN, mau=XANH, size=14):
    the(s, x, y, w, h, fill)
    return tb(s, x + 0.15, y + 0.1, w - 0.3, h - 0.2, [(tieu_de, {'size': 17, 'bold': True, 'color': mau, 'after': 4})] +
              [(d, {'size': size, 'color': CHU, 'after': 2}) for d in dong])


def so(s, x, y, n, d=0.55):
    o = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(d), Inches(d))
    o.fill.solid(); o.fill.fore_color.rgb = rgb(XANH); o.line.fill.background(); o.shadow.inherit = False
    tf = o.text_frame; tf.margin_left = tf.margin_right = 0
    p = tf.paragraphs[0]; p.alignment = PP_ALIGN.CENTER; r = p.add_run(); r.text = str(n)
    r.font.size = Pt(17); r.font.bold = True; r.font.color.rgb = rgb(TRANG)


def buoc(s, x, y, w, ds, gap=0.95, size=14):
    for i, (td, nd) in enumerate(ds, 1):
        so(s, x, y + (i - 1) * gap + 0.05, i)
        tb(s, x + 0.75, y + (i - 1) * gap, w - 0.75, gap, [(td, {'size': 17, 'bold': True, 'color': NAVY, 'after': 1}),
                                                         (nd, {'size': size, 'color': CHU})])


def anh(s, path, x, y, w, h, khung=False):
    """Đặt ảnh vừa khung (x, y, w, h), giữ tỉ lệ, căn giữa."""
    iw, ih = Image.open(path).size
    k = min(w / iw, h / ih); ww, hh = iw * k, ih * k
    px, py = x + (w - ww) / 2, y + (h - hh) / 2
    if khung: the(s, px - 0.08, py - 0.08, ww + 0.16, hh + 0.16, TRANG, line='C9D6E5')
    return s.shapes.add_picture(path, Inches(px), Inches(py), Inches(ww), Inches(hh))


def chu_thich(s, x, y, w, txt):
    tb(s, x, y, w, 0.35, [(txt, {'size': 12, 'color': PHU, 'italic': True})], align=PP_ALIGN.CENTER)


def bang(s, x, y, w, cols, rows, size=14, hrow=0.42):
    sh = s.shapes.add_table(len(rows) + 1, len(cols), Inches(x), Inches(y), Inches(w), Inches(hrow * (len(rows) + 1)))
    t = sh.table
    tong = sum(c[1] for c in cols)
    for j, (ten, rong) in enumerate(cols):
        t.columns[j].width = Emu(int(Inches(w) * rong / tong))
    for i, row in enumerate([[c[0] for c in cols]] + rows):
        t.rows[i].height = Inches(hrow)
        for j, v in enumerate(row):
            c = t.cell(i, j); c.fill.solid(); c.fill.fore_color.rgb = rgb(XANH if i == 0 else TRANG)
            c.margin_left = c.margin_right = Inches(0.08); c.margin_top = c.margin_bottom = Inches(0.04)
            c.vertical_anchor = MSO_ANCHOR.MIDDLE
            tf = c.text_frame; tf.word_wrap = True
            p = tf.paragraphs[0]; r = p.add_run(); r.text = v
            r.font.size = Pt(size + (1 if i == 0 else 0)); r.font.bold = (i == 0 or j == 0)
            r.font.color.rgb = rgb(TRANG if i == 0 else (NAVY if j == 0 else CHU))
    return sh


def stat(s, x, y, w, so_lieu, nhan, mau=XANH):
    tb(s, x, y, w, 0.8, [(so_lieu, {'size': 40, 'bold': True, 'color': mau, 'after': 0})])
    tb(s, x, y + 0.78, w, 0.6, [(nhan, {'size': 13, 'color': PHU})])


def moi(tieu_de, ghi_chu=None, layout='Title Only'):
    s = prs.slides.add_slide(LAYOUT[layout])
    s.shapes.title.text = tieu_de
    if ghi_chu: s.notes_slide.notes_text_frame.text = ghi_chu
    return s


# ================= 1. Bìa (sửa chữ trên slide gốc)
def doi_doan(shape, dong):
    """Thay chữ, giữ định dạng: tiêu đề bìa là 1 đoạn nhiều run ngăn bởi <a:br/>, phần dưới là nhiều đoạn 1 run."""
    ps = shape.text_frame.paragraphs
    runs = [r for p in ps for r in p.runs]
    assert len(runs) == len(dong), (len(runs), len(dong))
    for r, txt in zip(runs, dong): r.text = txt

tieu, phu = [sh for sh in BIA.shapes if sh.has_text_frame]
doi_doan(tieu, ['Học phần: Lập trình nâng cao', 'Đề tài: Xây dựng hệ thống', 'quản lý nhà hàng Atlas Restaurant'])
doi_doan(phu, ['Giảng viên hướng dẫn: Nguyễn Đức Giang',
               'Thành viên: BIT240002 – Phạm Duy An (nhóm trưởng) · BIT240025 – Bùi Đào Đức Anh',
               'BIT240015 – Đỗ Tuấn Anh · BIT240017 – Lê Anh · BIT240091 – Nguyễn Quang Hiếu'])

# ================= 2. Nội dung trình bày
s = moi('Nội dung trình bày', 'Đi theo đúng thứ tự báo cáo: bài toán, phân tích thiết kế, cài đặt, kiểm thử, kết quả.', 'Title and Content')
body = [p for p in s.placeholders if p.placeholder_format.idx != 0][0]
muc = [('Giới thiệu và phát triển bài toán', 'Mục tiêu, công nghệ, người dùng, ràng buộc nghiệp vụ'),
       ('Phân tích và thiết kế', 'Cơ sở dữ liệu 3NF, ERD, use case, hoạt động, tuần tự'),
       ('Kiến trúc và kỹ thuật lập trình nâng cao', 'Modular Monolith, chia tầng, Strategy, xử lý đồng thời'),
       ('Triển khai', 'Phân công, code tiêu biểu, kiểm thử, đưa lên máy chủ'),
       ('Kết quả và hướng phát triển', 'Giao diện đang chạy, kết luận, hướng phát triển')]
tf = body.text_frame; tf.clear()
for i, (a, b) in enumerate(muc):
    p = tf.paragraphs[0] if i == 0 else tf.add_paragraph(); r = p.add_run(); r.text = a
    r.font.color.rgb = rgb(XANH)
    p2 = tf.add_paragraph(); p2.level = 1
    pPr = p2._p.get_or_add_pPr(); pPr.set('marL', '457200'); pPr.set('indent', '0')
    pPr.append(pPr.makeelement('{http://schemas.openxmlformats.org/drawingml/2006/main}buNone', {}))
    r2 = p2.add_run(); r2.text = b; r2.font.italic = True
    r2.font.color.rgb = rgb(XANH)

# ================= 3. Bài toán và mục tiêu
s = moi('Atlas Restaurant: bài toán và mục tiêu',
        'Nhà hàng phục vụ tại bàn có nhiều bộ phận phối hợp liên tục; làm bằng giấy dễ sai: hết nguyên liệu, tính sai tiền, không kiểm soát hạn dùng và tiền mặt.')
tb(s, 0.69, 1.25, 6.2, 0.5, [('Một ứng dụng web cho cả nhà hàng', {'size': 20, 'bold': True, 'color': NAVY})])
for i, (a, b) in enumerate([('Bán hàng', 'Mở bàn, gọi món theo đợt, bếp, hóa đơn, tiền mặt và QR'),
                            ('Thực đơn', 'Món, công thức theo phiên bản, thực đơn ngày, hạn mức suất'),
                            ('Kho', 'Nhập theo lô, xuất FEFO, trừ nguyên liệu khi nấu, kiểm kê'),
                            ('Nhân sự', 'Nhân viên, tài khoản, vai trò, ca làm, chấm công'),
                            ('Tài chính', 'Khoản chi, đối soát tiền mặt, báo cáo doanh thu')]):
    y = 1.9 + i * 0.98
    the(s, 0.69, y, 6.2, 0.86)
    tb(s, 0.87, y + 0.1, 5.9, 0.7, [(a, {'size': 16, 'bold': True, 'color': XANH, 'after': 1}), (b, {'size': 14})])
tb(s, 7.35, 1.25, 5.3, 0.5, [('Mục tiêu', {'size': 20, 'bold': True, 'color': NAVY})])
tb(s, 7.35, 1.9, 5.3, 2.8, [(t, {'bullet': True, 'size': 15, 'after': 6}) for t in [
    'Số hóa trọn vòng nghiệp vụ, mỗi người chỉ thấy phần việc của vai trò mình',
    'Dữ liệu luôn đúng khi nhiều người thao tác cùng lúc',
    'Áp dụng kỹ thuật lập trình nâng cao: Modular Monolith, mẫu thiết kế, đảo phụ thuộc, giao dịch và khóa',
    'Kiểm thử tự động, chạy mỗi lần đẩy code']])
the(s, 7.35, 4.85, 5.3, 1.45, XLA_NEN)
tb(s, 7.53, 4.97, 5.0, 1.25, [('Đang chạy thật', {'size': 17, 'bold': True, 'color': XLA, 'after': 3}),
                               ('https://rms.atlasplatform12.tech', {'size': 15, 'color': CHU, 'after': 2}),
                               ('4 vai trò · 5 phân hệ · 27 nhóm chức năng', {'size': 14, 'color': CHU})])

# ================= 4. Công nghệ
s = moi('Công nghệ sử dụng', 'Ba thành phần tách biệt giao tiếp qua REST/JSON. Backend là một ứng dụng Spring Boot chia module theo nghiệp vụ.')
for i, (ic, a, ds) in enumerate([('spring', 'Backend', ['Java 25 · Spring Boot 4.1', 'Spring Data JPA (Hibernate)', 'Spring Security, JWT, bcrypt', 'Spring Modulith: ranh giới module']),
                                 ('react', 'Frontend', ['React 19 · TypeScript', 'Vite build ra file tĩnh', 'TanStack Query: tải, làm mới dữ liệu', 'SPA, gọi API qua /api']),
                                 ('postgres', 'Cơ sở dữ liệu', ['PostgreSQL 17', '49 bảng, chuẩn 3NF', 'CHECK, UNIQUE, trigger', 'Flyway quản lý phiên bản schema'])]):
    x = 0.69 + i * 4.05
    the(s, x, 1.35, 3.85, 3.2)
    s.shapes.add_picture(io.BytesIO(ICON[ic]), Inches(x + 0.18), Inches(1.53), Inches(0.62), Inches(0.62))
    tb(s, x + 0.95, 1.49, 2.8, 3.0, [(a, {'size': 20, 'bold': True, 'color': XANH, 'after': 6})] + [(d, {'size': 14, 'after': 4}) for d in ds])
the(s, 0.69, 4.8, 11.95, 1.3, TRANG, line='C9D6E5')
tb(s, 0.87, 4.92, 11.65, 1.1, [('Kiểm thử và tích hợp', {'size': 17, 'bold': True, 'color': XANH, 'after': 3}),
                                ('JUnit 5 + Testcontainers chạy trên PostgreSQL thật; script Python chạy 40 test case chức năng qua REST API; SePay gửi webhook khi khách chuyển khoản QR.', {'size': 14})])

# ================= 5. Người dùng
s = moi('Người dùng và chức năng', 'Một nhân viên có thể mang nhiều vai trò. Phân quyền kiểm ở cả backend (@PreAuthorize) và frontend.')
bang(s, 0.69, 1.3, 11.95, [('Người dùng', 2.2), ('Chức năng chính', 7.2), ('Phân hệ', 2.5)], [
    ['Quản lý', 'Nhân sự, ca làm, thực đơn, kho, tài chính, báo cáo; xử lý ngoại lệ (miễn thu, hủy món đã nấu, sự cố chuyển khoản)', 'Tất cả'],
    ['Phục vụ', 'Mở bàn, gọi món theo nhiều đợt, bưng món, hủy món chưa nấu', 'Bán hàng'],
    ['Bếp', 'Hàng đợi món: nhận hoặc từ chối, nấu, báo xong; nhận nguyên liệu, sơ chế, hoàn trả, báo hủy', 'Bán hàng, Kho'],
    ['Thu ngân', 'Chốt hóa đơn, thu tiền mặt, tạo mã QR, mở lại hóa đơn khi khách gọi thêm', 'Thanh toán'],
    ['SePay', 'Hệ thống ngoài: gọi webhook báo tiền chuyển khoản đã vào tài khoản nhà hàng', 'Thanh toán']], size=14, hrow=0.62)
the(s, 0.69, 5.35, 11.95, 0.9, NEN)
tb(s, 0.87, 5.45, 11.65, 0.75, [('Đăng nhập theo vai trò: ', {'size': 15, 'bold': True, 'color': XANH, 'after': 0}),
                                 ('mỗi vai trò chỉ thấy đúng các trang của mình; gọi API sai vai trò nhận 403.', {'size': 14})])

# ================= 6. Ràng buộc nghiệp vụ
s = moi('Ràng buộc nghiệp vụ và yêu cầu phi chức năng',
        'Ràng buộc kiểm ở tầng Service (báo lỗi rõ cho người dùng) và được database kiểm lại bằng CHECK, UNIQUE, trigger (lớp bảo vệ cuối).')
bang(s, 0.69, 1.3, 8.2, [('Chức năng', 2.0), ('Ràng buộc', 6.2)], [
    ['Đăng nhập', 'Sai 5 lần trong 15 phút: khóa tạm 15 phút; sai tên hay sai mật khẩu cùng một thông báo'],
    ['Gọi món', 'Món thuộc thực đơn hôm nay, không tạm dừng, đủ nguyên liệu; gửi lặp không tạo đợt mới'],
    ['Chế biến', 'PENDING → ACCEPTED → COOKING → READY → SERVED; nhận món thì giữ nguyên liệu FEFO'],
    ['Thanh toán', 'Một hóa đơn chỉ thu một lần, kể cả khi nhiều thu ngân bấm cùng lúc'],
    ['Kho', 'Lô nhập còn hạn; xuất theo FEFO, không xuất quá tồn; phiếu đã xác nhận không xóa']], size=13, hrow=0.72)
stat(s, 9.4, 1.3, 3.3, '2 lớp', 'kiểm ràng buộc: Service và database')
stat(s, 9.4, 2.85, 3.3, 'Khóa dòng', 'mọi thao tác đổi trạng thái (SELECT ... FOR UPDATE)', NAVY)
stat(s, 9.4, 4.4, 3.3, 'Truy vết', 'người làm, thời điểm, lý do của mọi lần đổi trạng thái', XLA)

# ================= 7. CSDL
s = moi('Thiết kế cơ sở dữ liệu', 'ERD vẽ trên dbdiagram với vị trí bảng và đường nối đặt thủ công: mỗi quan hệ một đường riêng từ cột khóa ngoại tới khóa chính.')
stat(s, 0.69, 1.3, 4.6, '49 bảng', 'chia 9 cụm nghiệp vụ, mỗi cụm 2 đến 8 bảng')
stat(s, 0.69, 2.75, 4.6, '3NF', 'mọi thuộc tính phụ thuộc trực tiếp vào khóa chính', NAVY)
the_chu(s, 0.69, 4.25, 4.6, 2.05, 'Bảo vệ dữ liệu ở database', [
    'Khóa ngoại, CHECK, UNIQUE', 'Trigger chặn sửa dữ liệu đã khóa sổ', 'Giá chụp lúc gọi món (unit_price) để hóa đơn không đổi khi đổi giá'], size=13)
anh(s, 'img/dbml-p3a-goi-mon.png', 5.6, 1.15, 7.05, 5.2, khung=True)
chu_thich(s, 5.6, 6.4, 7.05, 'ERD cụm gọi món: bàn → phiên → đơn → đợt gọi → món → nhật ký trạng thái')

# ================= 8. Use case
s = moi('Biểu đồ use case tổng quát', '4 tác nhân người và SePay; use case chi tiết từng phân hệ và đặc tả use case chính nằm trong báo cáo mục 3.2.')
anh(s, 'img/uc-tong-quat.png', 0.69, 1.15, 6.4, 5.25, khung=True)
the_chu(s, 7.5, 1.3, 5.15, 2.3, 'Tác nhân', ['Quản lý, Phục vụ, Bếp, Thu ngân: đăng nhập theo vai trò', 'SePay: hệ thống ngoài gọi webhook'], size=14)
the_chu(s, 7.5, 3.8, 5.15, 2.5, 'Phân hệ', ['Nhân sự và ca làm · Thực đơn', 'Bán hàng (phục vụ, bếp) · Thanh toán', 'Kho · Tài chính, báo cáo'], size=14)

# ================= 9. Hoạt động và trạng thái
s = moi('Biểu đồ hoạt động và trạng thái', 'Hoạt động gọi món theo làn từng vai trò; trạng thái món là máy trạng thái mà OrderItemStatusChanger bảo đảm.')
anh(s, 'img/act-goi-mon.png', 0.69, 1.15, 5.8, 4.95, khung=True)
chu_thich(s, 0.69, 6.15, 5.8, 'Hoạt động gọi món: kiểm bàn, gọi nhiều đợt, gửi bếp')
anh(s, 'img/state-mon.png', 6.85, 1.15, 5.8, 4.95, khung=True)
chu_thich(s, 6.85, 6.15, 5.8, 'Trạng thái món: PENDING → ACCEPTED → COOKING → READY → SERVED')

# ================= 10. Tuần tự
s = moi('Biểu đồ tuần tự: thanh toán chuyển khoản QR', 'Hình 1/2 (thu ngân tạo QR, màn hình chờ) có trong báo cáo; slide này trình bày phía SePay, phần quyết định ghi tiền.')
anh(s, 'img/seq-thanh-toan-qr-2.png', 0.69, 1.15, 7.6, 5.3, khung=True)
buoc(s, 8.6, 1.3, 4.05, [('Kiểm webhook', 'Đúng khóa API, đúng tài khoản nhận, tách mã tham chiếu'),
                         ('Chống ghi trùng', 'Khóa theo mã giao dịch; gửi lặp trả kết quả cũ'),
                         ('Đối chiếu', 'Đúng mã, đúng số tiền, còn chờ thu thì PROCESSED'),
                         ('Đóng hóa đơn', 'InvoiceSettlement: PAID, đóng đơn, trả bàn')], gap=1.25, size=13)

# ================= 11. Kiến trúc module
s = moi('Kiến trúc Modular Monolith', 'Một ứng dụng Spring Boot chia module theo nghiệp vụ. Module khác chỉ gọi interface ở package gốc; ModularityTest kiểm luật mỗi lần build.')
anh(s, 'img/class-kien-truc.png', 0.69, 1.15, 6.0, 5.3, khung=True)
the_chu(s, 7.05, 1.25, 5.6, 1.6, 'Phụ thuộc một chiều', ['finance → order → inventory → menu, không có vòng'], size=14)
the_chu(s, 7.05, 3.0, 5.6, 1.85, 'Đảo phụ thuộc', ['Module dưới khai báo interface, module trên cài đặt', 'Ví dụ: order khai báo OrderInvoiceAccess, finance cài'], size=14)
the_chu(s, 7.05, 5.0, 5.6, 1.35, 'Kiểm tự động', ['ModularityTest (Spring Modulith): vi phạm thì build đỏ'], fill=XLA_NEN, mau=XLA, size=14)

# ================= 12. Chia tầng
s = moi('Chia tầng trong mỗi module', 'Ví dụ module order: controller nhận request, service chứa nghiệp vụ và giao dịch, repository đọc ghi, entity ánh xạ bảng.')
anh(s, 'img/class-ban-hang.png', 0.69, 1.15, 6.4, 5.3, khung=True)
buoc(s, 7.45, 1.25, 5.2, [('Controller', '@RestController: @Valid, @PreAuthorize, lấy người dùng từ token; không gọi database'),
                          ('Service', 'Interface + Impl; mỗi thao tác ghi là một @Transactional; nghiệp vụ chỉ ở đây'),
                          ('Repository', 'Spring Data JPA; truy vấn khóa dòng có hậu tố ForUpdate'),
                          ('Entity, DTO', 'Entity ánh xạ bảng; DTO dạng record tách dữ liệu vào ra API')], gap=1.28, size=13)

# ================= 13. Strategy
s = moi('Mẫu Strategy cho thanh toán', 'PaymentServiceImpl không biết chi tiết từng phương thức; Spring tiêm mọi lớp cài PaymentStrategy vào Map.')
anh(s, 'img/class-thanh-toan.png', 0.69, 1.15, 7.0, 5.3, khung=True)
the_chu(s, 8.0, 1.25, 4.65, 2.0, 'Ý tưởng', ['Map<PaymentMethod, PaymentStrategy>', 'Chọn strategy theo phương thức: CASH hoặc QR'], size=14)
the_chu(s, 8.0, 3.4, 4.65, 1.6, 'Mở rộng', ['Thêm phương thức mới = thêm 1 lớp, không sửa PaymentServiceImpl'], fill=XLA_NEN, mau=XLA, size=14)
the_chu(s, 8.0, 5.15, 4.65, 1.2, 'Một điểm kết thúc', ['Cả 2 đường thu tiền đều qua InvoiceSettlement'], size=14)

# ================= 14. Đồng thời và bảo mật
s = moi('Xử lý đồng thời và bảo mật', 'Đây là phần khó nhất của bài toán: nhiều người cùng thao tác trên cùng bàn, cùng hóa đơn, cùng lô nguyên liệu.')
for i, (a, ds) in enumerate([('Khóa dòng (khóa bi quan)', ['SELECT ... FOR UPDATE trước khi đổi trạng thái', 'Hai thu ngân bấm cùng lúc: chỉ một người thu được']),
                             ('Chống gửi trùng', ['Mỗi thao tác ghi mang requestKey', 'Gửi lặp trả lại kết quả cũ (replayed), không tạo bản mới']),
                             ('Khóa theo khóa nghiệp vụ', ['pg_advisory_xact_lock theo mã giao dịch, theo ngày', 'Webhook gửi lặp, đối soát cùng ngày chạy lần lượt']),
                             ('Bảo mật đăng nhập', ['bcrypt; JWT có phiên bản: đổi mật khẩu thì token cũ mất hiệu lực ngay', 'Sai 5 lần khóa 15 phút; không lộ tài khoản có tồn tại'])]):
    x, y = 0.69 + (i % 2) * 6.05, 1.3 + (i // 2) * 2.55
    the_chu(s, x, y, 5.9, 2.35, a, ds, size=14)

# ================= 15. Phân công
s = moi('Kế hoạch và phân công', 'Phân công theo vòng đời nghiệp vụ: mỗi người phụ trách một phân hệ gồm backend, giao diện và kiểm thử.')
bang(s, 0.69, 1.3, 11.95, [('Thành viên', 2.6), ('Phân hệ phụ trách', 5.6), ('Đoạn code tiêu biểu', 3.75)], [
    ['Phạm Duy An (NT)', 'Bán hàng: bàn, gọi món, bếp, hóa đơn, thanh toán tiền mặt, QR SePay; điều phối kiểm thử; triển khai máy chủ', 'Ghi nhận tiền chuyển khoản QR'],
    ['Nguyễn Quang Hiếu', 'Nhân sự và bảo mật: nhân viên, tài khoản, Spring Security, JWT, ca làm, chấm công', 'Đăng nhập'],
    ['Lê Anh', 'Thực đơn: món, công thức theo phiên bản, thực đơn ngày, hạn mức, tạm dừng món', 'Đổi hạn mức suất bán'],
    ['Đỗ Tuấn Anh', 'Kho: nhập theo lô, xuất FEFO, bếp nhận, sơ chế, hoàn trả, kiểm kê', 'Giữ nguyên liệu theo FEFO'],
    ['Bùi Đào Đức Anh', 'Tài chính: khoản chi, đối soát tiền mặt, báo cáo doanh thu', 'Đối soát tiền mặt cuối ngày']], size=13, hrow=0.78)

# ================= 16. Code An
s = moi('Code tiêu biểu: ghi nhận tiền chuyển khoản QR', 'Phạm Duy An. Sai ở đây là mất tiền hoặc thu hai lần, nên mọi trường hợp bất thường đều phải xử lý.')
anh(s, 'img/tv-1-an-qr-sepay.png', 0.69, 1.15, 7.4, 5.3, khung=True)
tb(s, 8.4, 1.25, 4.25, 0.5, [('QrTransferProcessor', {'size': 18, 'bold': True, 'color': NAVY})])
tb(s, 8.4, 1.85, 4.25, 4.5, [(t, {'bullet': True, 'size': 14, 'after': 8}) for t in [
    'Khóa theo mã giao dịch: SePay gửi lặp thì trả kết quả cũ, không thu lần hai',
    'Khóa hóa đơn và yêu cầu thanh toán rồi mới đối chiếu',
    'Lệch số tiền, sai mã, đến sau khi hủy: lưu sự cố cho quản lý xử lý',
    'PROCESSED: ghi Payment, InvoiceSettlement đóng hóa đơn và trả bàn',
    'Frontend tự tải lại hóa đơn 3 giây/lần khi còn QR đang chờ']])

# ================= 17. Code 4 thành viên
s = moi('Code tiêu biểu của các thành viên', 'Mỗi thành viên chọn một đoạn code tiêu biểu nhất trong phân hệ của mình; ảnh chụp từ mã nguồn, có số dòng (báo cáo mục 4.2).')
for i, (f, ten, y_nghia) in enumerate([('tv-2-hieu-dang-nhap', 'Nguyễn Quang Hiếu · Đăng nhập', 'So mật khẩu với mã băm giả khi không có tài khoản: thời gian phản hồi như nhau'),
                                       ('tv-3-le-anh-han-muc', 'Lê Anh · Đổi hạn mức suất bán', 'Khóa mọi món trước khi đọc; không hạ dưới số suất đã nhận'),
                                       ('tv-4-tuan-anh-fefo', 'Đỗ Tuấn Anh · Giữ nguyên liệu FEFO', 'Lập kế hoạch đủ mọi nguyên liệu rồi mới ghi; hạn sớm lấy trước'),
                                       ('tv-5-duc-anh-doi-soat', 'Bùi Đào Đức Anh · Đối soát tiền mặt', 'Khóa theo ngày; mỗi lần lập lại là một phiên bản mới, có lý do')]):
    x, y = 0.69 + (i % 2) * 6.05, 1.2 + (i // 2) * 2.6
    the(s, x, y, 5.9, 2.45, TRANG, line='C9D6E5')
    anh(s, f'img/{f}.png', x + 0.1, y + 0.1, 2.6, 2.25)
    tb(s, x + 2.85, y + 0.15, 2.95, 2.2, [(ten, {'size': 15, 'bold': True, 'color': XANH, 'after': 6}), (y_nghia, {'size': 13})])

# ================= 18. Kiểm thử
s = moi('Kiểm thử', '40 test case chức năng có input cụ thể, output mong muốn và thực tế, lưu ở Google Sheet; 92 test tự động chạy mỗi lần đẩy code.')
stat(s, 0.69, 1.3, 3.6, '92', 'test tự động (JUnit 5 + Testcontainers trên PostgreSQL thật)')
stat(s, 0.69, 2.85, 3.6, '40/40', 'test case chức năng hộp đen qua REST API đều đạt', XLA)
stat(s, 0.69, 4.4, 3.6, '9 nhóm', 'đăng nhập, phân quyền, nhân sự, thực đơn, bán hàng, thanh toán, kho, tài chính', NAVY)
anh(s, 'img/ket-qua-test.png', 4.6, 1.15, 8.05, 5.25, khung=True)
chu_thich(s, 4.6, 6.4, 8.05, 'Kết quả chạy 40 test case (script test/tc_runner.py)')

# ================= 19. Giao diện
s = moi('Giao diện hệ thống đang chạy', 'Ảnh chụp từ https://rms.atlasplatform12.tech với dữ liệu mẫu; giao diện tự làm mới, thông báo lỗi tiếng Việt.')
for i, (f, cap) in enumerate([('03-so-do-ban', 'Sơ đồ bàn'), ('04-goi-mon', 'Gọi món'), ('05-bep', 'Màn hình bếp'),
                              ('06-hoa-don-cho-thu', 'Hóa đơn chờ thu với mã QR'), ('13-ton-kho', 'Tồn kho, cảnh báo hạn dùng'), ('19-bao-cao', 'Báo cáo doanh thu')]):
    x, y = 0.69 + (i % 3) * 4.02, 1.15 + (i // 3) * 2.65
    anh(s, f'img/{f}.png', x, y, 3.85, 2.2, khung=True)
    chu_thich(s, x, y + 2.25, 3.85, cap)

# ================= 20. Triển khai
s = moi('Triển khai lên máy chủ', 'Mỗi lần đẩy code, quy trình tự động kiểm tra và đưa lên production khoảng 6 đến 8 phút, không có bước làm tay. Chi tiết ở học phần Triển khai phần mềm.')
anh(s, 'img/deploy.png', 0.69, 1.15, 5.6, 5.3, khung=True)
buoc(s, 6.65, 1.25, 6.0, [('Đẩy code lên GitHub', 'Jenkins (máy 2) chạy test, quét bảo mật; đạt thì tự gộp nhánh'),
                          ('Chạy thử trên bản sao dữ liệu', 'Kiểm thử end-to-end, đóng gói image'),
                          ('Argo CD triển khai blue-green', 'Lên Kubernetes máy 1; kiểm tra xong mới chuyển người dùng'),
                          ('HTTPS qua nginx', 'Traefik đưa / tới giao diện, /api tới backend'),
                          ('Giám sát, sao lưu', 'Prometheus, Grafana, Loki; cảnh báo Telegram; sao lưu liên tục')], gap=1.02, size=13)

# ================= 21. Kết luận
s = moi('Kết luận và hướng phát triển', 'Nhấn mạnh kiến thức đạt được: thiết kế CSDL, kiến trúc module, xử lý đồng thời, kiểm thử tự động, làm việc nhóm qua Git.')
the(s, 0.69, 1.25, 5.9, 5.05, XLA_NEN)
tb(s, 0.87, 1.38, 5.55, 4.85, [('Đã làm được', {'size': 18, 'bold': True, 'color': XLA, 'after': 8})] +
   [(t, {'bullet': True, 'size': 14, 'after': 6}) for t in [
       '5 phân hệ, 27 nhóm chức năng, chạy thật trên domain công khai',
       'Đúng dữ liệu khi nhiều người thao tác: khóa dòng, requestKey, thu một lần',
       'Thanh toán QR tự động qua webhook SePay, ghi sự cố khi lệch',
       'Kho theo lô, FEFO, trừ nguyên liệu khi nấu, sổ kho truy vết',
       '92 test tự động và 40 test case chức năng đều đạt']])
the(s, 6.75, 1.25, 5.9, 2.75, NEN)
tb(s, 6.93, 1.38, 5.55, 2.55, [('Hướng phát triển', {'size': 18, 'bold': True, 'color': XANH, 'after': 8})] +
   [(t, {'bullet': True, 'size': 14, 'after': 5}) for t in [
       'Khách gọi món bằng mã QR tại bàn, đặt bàn trước',
       'Ứng dụng di động cho phục vụ, thông báo khi món xong',
       'Báo cáo lợi nhuận theo món, dự báo lượng nhập']])
the(s, 6.75, 4.15, 5.9, 2.15, TRANG, line='C9D6E5')
tb(s, 6.93, 4.27, 5.55, 1.95, [('Chưa làm được', {'size': 18, 'bold': True, 'color': DO, 'after': 8})] +
   [(t, {'bullet': True, 'size': 14, 'after': 5}) for t in [
       'Phân quyền chi tiết theo từng quyền (mới thiết kế bảng)',
       'In hóa đơn máy in nhiệt, xuất báo cáo Excel']])

# ---- trang cảm ơn xuống cuối
ids = list(lst)
cam = [sid for sid in ids if prs.slides.get(int(sid.get('id'))) is CAM_ON][0]
lst.remove(cam); lst.append(cam)
prs.save('Slide_LapTrinhNangCao_AtlasRestaurant.pptx')
print('so slide', len(prs.slides))
