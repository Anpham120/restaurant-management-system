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

# ================= Giới thiệu bài toán (1/2): bối cảnh và thực trạng
s = moi('Giới thiệu bài toán: bối cảnh và thực trạng',
        'Nhà hàng phục vụ tại bàn có nhiều bộ phận phải phối hợp liên tục. Khi làm bằng giấy hoặc bảng tính rời rạc, lỗi xảy ra ở mọi khâu.')
tb(s, 0.69, 1.15, 11.95, 0.75, [('Nhà hàng phục vụ tại bàn có 5 bộ phận phối hợp trong mỗi bữa ăn. Mỗi bộ phận ghi chép riêng bằng giấy hoặc bảng tính, '
                                'nên thông tin không đi kịp giữa các khâu.', {'size': 16})])
bang(s, 0.69, 2.0, 11.95, [('Bộ phận', 1.7), ('Công việc', 4.5), ('Lỗi thường gặp khi làm thủ công', 5.75)], [
    ['Phục vụ', 'Ghi món từng đợt, chuyển xuống bếp', 'Gọi món đã hết, sai món, sót món gọi thêm'],
    ['Bếp', 'Nhận món, nấu; lấy nguyên liệu từ kho', 'Nhận món rồi mới biết hết nguyên liệu'],
    ['Thu ngân', 'Tính tiền, thu tiền mặt hoặc chuyển khoản', 'Tính sai tiền; khó đối chiếu tiền chuyển khoản'],
    ['Kho', 'Nhập nguyên liệu theo lô, xuất cho bếp', 'Không biết lô nào sắp hết hạn; tồn kho lệch sổ'],
    ['Quản lý', 'Theo dõi doanh thu, chi phí, nhân sự', 'Không biết món nào bán chạy; lệch tiền mặt cuối ngày']], size=15, hrow=0.55)
the(s, 0.69, 5.6, 11.95, 0.7, XLA_NEN)
tb(s, 0.87, 5.63, 11.6, 0.64, [('Cần một hệ thống dùng chung: thông tin đi ngay giữa các bộ phận, không còn ghi chép rời', {'size': 16, 'bold': True, 'color': XLA})], anchor=MSO_ANCHOR.MIDDLE)

# ================= Giới thiệu bài toán (2/2): lý do, yêu cầu, mục tiêu
s = moi('Giới thiệu bài toán: yêu cầu và mục tiêu',
        'Lý do chọn đề tài: bài toán thực tế, nhiều ràng buộc và nhiều người thao tác cùng lúc, phù hợp áp dụng kỹ thuật lập trình nâng cao.')
for i, (td, ds, nen, mau) in enumerate([
        ('Lý do chọn đề tài', ['Bài toán thực tế, nhà hàng nào cũng gặp', 'Nhiều bộ phận, nhiều quy tắc nghiệp vụ ràng buộc lẫn nhau',
                               'Nhiều người thao tác cùng lúc trên cùng bàn, hóa đơn, lô hàng', 'Phù hợp áp dụng kiến thức Lập trình nâng cao'], NEN, XANH),
        ('Yêu cầu đặt ra', ['4 vai trò dùng chung một hệ thống', 'Món còn nguyên liệu và còn suất mới được gọi',
                            'Một hóa đơn chỉ thu một lần, kể cả khi nhiều người bấm cùng lúc', 'Nhận tiền chuyển khoản tự động qua SePay',
                            'Xuất kho theo hạn dùng sớm nhất (FEFO)'], NEN, XANH),
        ('Mục tiêu', ['Số hóa trọn vòng nghiệp vụ: bàn, món, bếp, hóa đơn, thu tiền', 'Quản lý thực đơn, kho, nhân sự, tài chính',
                      'Dữ liệu luôn đúng, có truy vết', 'Áp dụng kiến trúc module, mẫu thiết kế, giao dịch và khóa, kiểm thử tự động'], XLA_NEN, XLA)]):
    x = 0.69 + i * 4.05
    the(s, x, 1.3, 3.85, 4.6, nen)
    tb(s, x + 0.18, 1.45, 3.5, 4.35, [(td, {'size': 19, 'bold': True, 'color': mau, 'after': 10})] +
       [(t, {'bullet': True, 'size': 16, 'after': 10}) for t in ds])

# ================= Giải pháp
s = moi('Giải pháp: hệ thống Atlas Restaurant', 'Một ứng dụng web dùng chung cho cả nhà hàng; mỗi người đăng nhập theo vai trò và chỉ thấy phần việc của mình.')
the_chu(s, 0.69, 1.3, 5.9, 2.95, 'Một ứng dụng web cho cả nhà hàng', [
    '• 4 vai trò: Quản lý, Phục vụ, Bếp, Thu ngân',
    '• Mỗi người chỉ thấy đúng phần việc của mình',
    '• Dùng trên máy tính và máy tính bảng',
    '• Màn hình tự làm mới, thông báo lỗi tiếng Việt'], size=17)
the(s, 6.85, 1.3, 5.8, 2.95, XLA_NEN)
tb(s, 7.05, 1.42, 5.4, 2.75, [('Đang chạy thật', {'size': 20, 'bold': True, 'color': XLA, 'after': 4}),
                              ('https://rms.atlasplatform12.tech', {'size': 18, 'color': NAVY, 'bold': True, 'after': 10}),
                              ('5 phân hệ nghiệp vụ', {'size': 17, 'after': 6}),
                              ('27 nhóm chức năng', {'size': 17, 'after': 6}),
                              ('Thanh toán chuyển khoản QR thật qua SePay', {'size': 17, 'after': 6})])

tb(s, 0.69, 4.45, 11.95, 0.45, [('Vòng nghiệp vụ một bữa ăn', {'size': 17, 'bold': True, 'color': NAVY})])
for i, (td, nd) in enumerate([('Mở bàn', 'Phục vụ'), ('Gọi món', 'Phục vụ'), ('Bếp nấu', 'Bếp'), ('Chốt hóa đơn', 'Thu ngân'), ('Thu tiền', 'Tiền mặt hoặc QR')]):
    x = 0.69 + i * 2.45
    the(s, x, 5.0, 2.05, 1.15, NEN)
    tb(s, x, 5.08, 2.05, 1.0, [(td, {'size': 17, 'bold': True, 'color': XANH, 'after': 2}), (nd, {'size': 13, 'color': PHU})], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
    if i < 4:
        m = s.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x + 2.1), Inches(5.42), Inches(0.32), Inches(0.32))
        m.fill.solid(); m.fill.fore_color.rgb = rgb(XANH); m.line.fill.background(); m.shadow.inherit = False

# ================= Hệ thống làm được gì
s = moi('Hệ thống làm được gì', 'Đi theo vòng một bữa ăn: mở bàn, gọi món, bếp nấu, thu tiền; phía sau là thực đơn, kho, nhân sự và tài chính.')
for i, (a_, ai, ds) in enumerate([('Bán hàng', 'Phục vụ, Bếp, Thu ngân', 'Mở bàn, gọi món nhiều đợt; bếp nhận, nấu, báo xong; chốt hóa đơn, thu tiền mặt hoặc QR'),
                                  ('Thực đơn', 'Quản lý, Bếp', 'Món, giá, công thức; thực đơn theo ngày, hạn mức suất bán, tạm dừng món khi hết'),
                                  ('Kho', 'Quản lý, Bếp', 'Nhập theo lô và hạn dùng, xuất hết hạn trước (FEFO), tự trừ nguyên liệu khi nấu, kiểm kê'),
                                  ('Nhân sự', 'Quản lý', 'Nhân viên, tài khoản, vai trò; phân ca, chấm công'),
                                  ('Tài chính', 'Quản lý, Thu ngân', 'Khoản chi, đối soát tiền mặt cuối ngày, báo cáo doanh thu')]):
    y = 1.25 + i * 1.03
    the(s, 0.69, y, 11.95, 0.9)
    tb(s, 0.87, y + 0.14, 2.3, 0.65, [(a_, {'size': 18, 'bold': True, 'color': XANH, 'after': 0}), (ai, {'size': 12, 'color': PHU})])
    tb(s, 3.3, y + 0.14, 9.2, 0.65, [(ds, {'size': 16})], anchor=MSO_ANCHOR.MIDDLE)

# ================= Giao diện
s = moi('Giao diện hệ thống', 'Ảnh chụp từ hệ thống đang chạy với dữ liệu mẫu; giao diện tự làm mới, thông báo lỗi tiếng Việt.')
for i, (f, cap) in enumerate([('03-so-do-ban', 'Sơ đồ bàn'), ('04-goi-mon', 'Gọi món'), ('05-bep', 'Màn hình bếp'),
                              ('06-hoa-don-cho-thu', 'Hóa đơn chờ thu với mã QR'), ('13-ton-kho', 'Tồn kho, cảnh báo hạn dùng'), ('19-bao-cao', 'Báo cáo doanh thu')]):
    x, y = 0.69 + (i % 3) * 4.02, 1.15 + (i // 3) * 2.65
    anh(s, f'img/{f}.png', x, y, 3.85, 2.2, khung=True)
    chu_thich(s, x, y + 2.25, 3.85, cap)

# ================= Công nghệ
s = moi('Công nghệ sử dụng', 'Backend là một ứng dụng Spring Boot chia module theo nghiệp vụ; frontend là ứng dụng một trang gọi API REST.')
for i, (ic, a_, ds) in enumerate([('spring', 'Backend', ['Java 25 · Spring Boot 4.1', 'Spring Data JPA', 'Spring Security, JWT']),
                                  ('react', 'Frontend', ['React 19 · TypeScript', 'Vite, TanStack Query']),
                                  ('postgres', 'Cơ sở dữ liệu', ['PostgreSQL 17', 'Flyway quản lý schema'])]):
    x = 0.69 + i * 4.05
    the(s, x, 1.5, 3.85, 2.6)
    s.shapes.add_picture(io.BytesIO(ICON[ic]), Inches(x + 0.18), Inches(1.68), Inches(0.62), Inches(0.62))
    tb(s, x + 0.95, 1.64, 2.8, 2.4, [(a_, {'size': 20, 'bold': True, 'color': XANH, 'after': 6})] + [(d, {'size': 15, 'after': 4}) for d in ds])
the_chu(s, 0.69, 4.4, 11.95, 1.5, 'Kiểm thử và triển khai', ['JUnit 5 + Testcontainers trên PostgreSQL thật · Chạy trên máy chủ, tự đưa bản mới lên khi đẩy code'], fill=TRANG, size=16)
s.shapes[-2].line.color.rgb = rgb('C9D6E5')

# ================= Đạt được gì
s = moi('Kết quả đạt được', 'Nhấn mạnh: hệ thống chạy thật, đúng dữ liệu khi nhiều người cùng thao tác, kiểm thử đầy đủ.')
for i, (so_, nhan, mau) in enumerate([('5', 'phân hệ, 27 nhóm chức năng', XANH), ('49', 'bảng dữ liệu, chuẩn 3NF', NAVY),
                                      ('92', 'test tự động đạt', XLA), ('40/40', 'test case chức năng đạt', XLA)]):
    stat(s, 0.69 + i * 3.05, 1.3, 2.9, so_, nhan, mau)
the_chu(s, 0.69, 3.2, 5.9, 2.9, 'Về sản phẩm', [
    '• Chạy thật trên domain công khai, có HTTPS',
    '• Thanh toán QR tự động: tiền vào là hóa đơn tự đóng',
    '• Kho theo lô, tự trừ nguyên liệu khi bếp nấu'], size=17)
the_chu(s, 6.75, 3.2, 5.9, 2.9, 'Về kỹ thuật', [
    '• Nhiều người thao tác cùng lúc vẫn đúng: một hóa đơn chỉ thu một lần',
    '• Kiến trúc chia module, mẫu thiết kế Strategy',
    '• Đăng nhập an toàn: JWT, chặn dò mật khẩu'], size=17)

# ================= Hướng phát triển
s = moi('Hướng phát triển', 'Kết thúc bằng những gì chưa làm và sẽ làm tiếp.')
the_chu(s, 0.69, 1.5, 5.9, 3.2, 'Sẽ phát triển', [
    '• Khách tự gọi món bằng mã QR tại bàn, đặt bàn trước',
    '• Ứng dụng di động cho phục vụ, báo khi món xong',
    '• Báo cáo lợi nhuận theo món, dự báo lượng nhập'], size=18)
the_chu(s, 6.75, 1.5, 5.9, 3.2, 'Chưa làm được', [
    '• Phân quyền chi tiết theo từng quyền',
    '• In hóa đơn máy in nhiệt, xuất báo cáo Excel',
    '• Giao diện chưa tối ưu cho điện thoại'], fill=TRANG, mau=DO, size=18)
s.shapes[-2].line.color.rgb = rgb('C9D6E5')

# ---- bản giới thiệu: dự án làm gì, đạt được gì (bỏ slide mục lục)
lst = prs.slides._sldIdLst
for sid in list(lst):
    sl = prs.slides.get(int(sid.get('id')))
    if sl.shapes.title is not None and sl.shapes.title.text == 'Nội dung trình bày':
        prs.part.drop_rel(sid.rId); lst.remove(sid)
# ---- trang cảm ơn xuống cuối
ids = list(lst)
cam = [sid for sid in ids if prs.slides.get(int(sid.get('id'))) is CAM_ON][0]
lst.remove(cam); lst.append(cam)
prs.save('Slide_LapTrinhNangCao_AtlasRestaurant.pptx')
print('so slide', len(prs.slides))
