"""Dựng báo cáo Lập trình nâng cao (docx) từ mẫu báo cáo triển khai: giữ bìa, header, footer, style; thay toàn bộ nội dung.
  python3 build_ltnc.py <mẫu.docx> <ra.docx>
Định dạng theo yêu cầu: Times New Roman 13 cho mọi chữ, giãn dòng 1,3, gạch đầu dòng 3 cấp "-", "+", "✓",
không in nghiêng, chỉ in đậm tiêu đề mục và dòng tiêu đề bảng."""
import html, json, os, re, shutil, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
import ltnc_lib
from ltnc_lib import *
import ch45
for n, url, _ in ch45.REFS_LIST:        # link [n] phải có trước khi sinh nội dung
    REFS[n] = url
from ch12 import chapter1, chapter2
from ch3 import chapter3
from ch45 import chapter4, chapter5, references

SRC, OUT = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
TRANG = json.load(open(f'{HERE}/trang.json', encoding='utf-8')) if os.path.exists(f'{HERE}/trang.json') else {}
D = OUT + '.d'
shutil.rmtree(D, ignore_errors=True)
z = zipfile.ZipFile(SRC); names = z.namelist(); z.extractall(D)
x = open(f'{D}/word/document.xml', encoding='utf-8').read()
b0 = x.index('<w:body>') + len('<w:body>'); sect = x.rindex('<w:sectPr')
blocks = [m.group(0) for m in re.finditer(r'<w:(p|tbl)[ >].*?</w:\1>', x[b0:sect], re.S)]

# ---------- Bìa (khối 1–19 của mẫu, có section riêng không header)
def retext(blk, old, new):
    t = ''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', blk))
    assert html.unescape(t) == old, (t, old)
    first = [True]
    def sub(m):
        if first[0]:
            first[0] = False
            return f'<w:t xml:space="preserve">{esc(new)}</w:t>'
        return '<w:t></w:t>'
    return re.sub(r'<w:t(?: [^>]*)?>[^<]*</w:t>', sub, blk)

cover = blocks[:19]
cover[8] = retext(cover[8], 'MÔN: TRIỂN KHAI PHẦN MỀM', 'MÔN: LẬP TRÌNH NÂNG CAO')
cover[9] = re.sub(r'<w:br/>', '<w:t xml:space="preserve"> </w:t>', re.sub(r'Phát triển và triển khai', 'Xây dựng', cover[9], count=1))
cover[10] = cover[10].replace('Đặng Quốc Hữu', 'Nguyễn Đức Giang')
cover[18] = retext(cover[18], 'Hà Nội, ngày 4 tháng 11 năm 2026', 'Hà Nội, tháng 11 năm 2026')
FRONT = lambda t, br=True: (f'<w:p><w:pPr><w:pStyle w:val="RMSFrontTitle"/>{"<w:pageBreakBefore/>" if br else ""}</w:pPr>'
                            f'<w:r><w:t>{esc(t)}</w:t></w:r></w:p>')

ABBR = [['Ký hiệu', 'Nội dung'], ['API', 'Application Programming Interface: giao diện lập trình ứng dụng'],
        ['CSDL', 'Cơ sở dữ liệu'], ['ERD', 'Entity Relationship Diagram: sơ đồ thực thể liên kết'],
        ['FEFO', 'First Expired, First Out: hết hạn trước xuất trước'], ['FK', 'Foreign Key: khóa ngoại'],
        ['JPA', 'Jakarta Persistence API: chuẩn ánh xạ đối tượng - quan hệ của Java'], ['JWT', 'JSON Web Token: token xác thực'],
        ['PK', 'Primary Key: khóa chính'], ['QR', 'Quick Response code: mã thanh toán chuyển khoản'],
        ['REST', 'Representational State Transfer: kiểu thiết kế API qua HTTP'], ['SPA', 'Single Page Application: ứng dụng web một trang'],
        ['UC', 'Use case'], ['UML', 'Unified Modeling Language: ngôn ngữ mô hình hóa thống nhất'],
        ['VND', 'Việt Nam đồng'], ['3NF', 'Dạng chuẩn 3 (Third Normal Form)']]

body = cover + [FRONT('MỤC LỤC'), '<!--MUCLUC-->', FRONT('DANH SÁCH TỪ, KÝ HIỆU VIẾT TẮT'), T([1701, 7371], ABBR),
                FRONT('DANH SÁCH HÌNH VẼ'), '<!--DM-HINH-->', FRONT('DANH SÁCH BẢNG'), '<!--DM-BANG-->']
body += chapter1() + chapter2() + chapter3() + chapter4() + chapter5() + references()
newbody = ''.join(body)

# Cùng cách xử lý với báo cáo Triển khai phần mềm (bc_build.py)
def _ngat_chuoi_dai(body):
    """Đoạn căn đều 2 bên có URL/đường dẫn dài: Word không ngắt được giữa chuỗi nên kéo giãn khoảng trắng của dòng
    (chữ cách nhau quá xa). Chèn zero-width space sau / . - _ trong chuỗi từ 25 ký tự để Word được xuống dòng ở đó;
    ký tự này không hiện ra. Không đụng bảng, khối code, URL của hyperlink (nằm ở rels)."""
    def tach(m):
        w = m.group(0)
        return re.sub(r'([/._-])(?=[^\s])', '\\1\u200b', w) if len(w) >= 25 or (len(w) >= 16 and '/' in w) else w
    def para(m):
        p = m.group(0)
        if not re.search(r'<w:pStyle w:val="(RMSBody|RMSList)"/>', p):
            return p
        chu = ''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', p))
        p = re.sub(r'(<w:t(?: [^>]*)?>)([^<]*)(</w:t>)', lambda t: t.group(1) + re.sub(r'\S{16,}', tach, t.group(2)) + t.group(3), p)
        if not re.search(r'\S{25,}', chu):
            return p
        # Đoạn có chuỗi dài: căn trái để dòng không bị giãn (jc đứng trước rPr trong pPr theo thứ tự của OOXML)
        p = re.sub(r'<w:jc w:val="\w+"/>', '', p, count=1)
        ppr = re.search(r'<w:pPr>.*?</w:pPr>', p, re.S).group(0)
        moi = ppr.replace('<w:rPr>', '<w:jc w:val="left"/><w:rPr>', 1) if '<w:rPr>' in ppr else ppr.replace('</w:pPr>', '<w:jc w:val="left"/></w:pPr>')
        return p.replace(ppr, moi, 1)
    parts = re.split(r'(<w:tbl>.*?</w:tbl>)', body, flags=re.S)
    return ''.join(x if x.startswith('<w:tbl>') else re.sub(r'<w:p[ >].*?</w:p>', para, x, flags=re.S) for x in parts)
newbody = _ngat_chuoi_dai(newbody)

# ---------- Bookmark cho chú thích; danh sách hình, bảng dạng bảng có cột trang (PAGEREF)
dm = {'RMSFigureCaption': [], 'RMSTableCaption': []}
def _bm(m):
    ppr, style, rest = m.group(1), m.group(2), m.group(3)
    text = html.unescape(''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', rest)))
    mm = re.match(r'((?:Hình|Bảng) \d+\.\d+)\.\s*(.*)', text)
    if not mm:
        return m.group(0)
    k = sum(len(v) for v in dm.values()) + 1
    dm[style].append((mm.group(1), mm.group(2), f'_Ref_dm_{k}'))
    return f'<w:p>{ppr}<w:bookmarkStart w:id="{9000 + k}" w:name="_Ref_dm_{k}"/>{rest}<w:bookmarkEnd w:id="{9000 + k}"/></w:p>'
newbody = re.sub(r'<w:p>(<w:pPr><w:pStyle w:val="(RMSFigureCaption|RMSTableCaption)"/>(?:(?!</w:pPr>).)*</w:pPr>)((?:(?!</w:p>).)*)</w:p>', _bm, newbody, flags=re.S)

def _cell(w, inner, head=False, center=False):
    sh = '<w:shd w:val="clear" w:color="auto" w:fill="DCE9F7"/>' if head else ''
    jc = '<w:jc w:val="center"/>' if (head or center) else ''
    return f'<w:tc><w:tcPr><w:tcW w:type="dxa" w:w="{w}"/>{sh}<w:vAlign w:val="center"/></w:tcPr><w:p><w:pPr><w:pStyle w:val="RMSTableText"/>{jc}</w:pPr>{inner}</w:p></w:tc>'
def _txt(t, b=False):
    return f'<w:r><w:rPr>{"<w:b/>" if b else ""}<w:sz w:val="26"/></w:rPr><w:t xml:space="preserve">{esc(t)}</w:t></w:r>'
def bang_dm(items, col):
    W = [1400, 6572, 1100]
    rows = ['<w:tr><w:trPr><w:tblHeader w:val="true"/><w:cantSplit/></w:trPr>' + _cell(W[0], _txt('Số hiệu', True), True)
            + _cell(W[1], _txt(col, True), True) + _cell(W[2], _txt('Trang', True), True) + '</w:tr>']
    for so, ten, bm in items:
        fld = ('<w:r><w:fldChar w:fldCharType="begin"/></w:r>'
               f'<w:r><w:instrText xml:space="preserve"> PAGEREF {bm} \\h </w:instrText></w:r>'
               '<w:r><w:fldChar w:fldCharType="separate"/></w:r>' + _txt(str(TRANG.get(so, ''))) + '<w:r><w:fldChar w:fldCharType="end"/></w:r>')
        rows.append('<w:tr><w:trPr><w:cantSplit/></w:trPr>' + _cell(W[0], _txt(so), center=True) + _cell(W[1], _txt(ten)) + _cell(W[2], fld, center=True) + '</w:tr>')
    return ('<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:type="dxa" w:w="9072"/><w:jc w:val="center"/><w:tblLayout w:type="fixed"/>'
            '<w:tblLook w:firstColumn="1" w:firstRow="1" w:lastColumn="0" w:lastRow="0" w:noHBand="0" w:noVBand="1" w:val="04A0"/></w:tblPr>'
            f'<w:tblGrid><w:gridCol w:w="{W[0]}"/><w:gridCol w:w="{W[1]}"/><w:gridCol w:w="{W[2]}"/></w:tblGrid>' + ''.join(rows) + '</w:tbl>' + GAP)

heads = []
for m in re.finditer(r'<w:p><w:pPr><w:pStyle w:val="Heading([123])"/>.*?</w:p>', newbody, re.S):
    t = html.unescape(''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', m.group(0))))
    if t.strip():
        heads.append((int(m.group(1)), t))
def _toc_p(lv, t, first, last):
    beg = ('<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve">TOC \\o "1-3" \\h \\z \\u</w:instrText></w:r>'
           '<w:r><w:fldChar w:fldCharType="separate"/></w:r>') if first else ''
    end = '<w:r><w:fldChar w:fldCharType="end"/></w:r>' if last else ''
    return (f'<w:p><w:pPr><w:pStyle w:val="TOC{lv}"/><w:tabs><w:tab w:pos="9071" w:val="right" w:leader="dot"/></w:tabs></w:pPr>'
            f'{beg}<w:r><w:t xml:space="preserve">{esc(t)}</w:t></w:r><w:r><w:tab/><w:t>{TRANG.get("H:" + t, "")}</w:t></w:r>{end}</w:p>')
newbody = newbody.replace('<!--MUCLUC-->', ''.join(_toc_p(lv, t, k == 0, k == len(heads) - 1) for k, (lv, t) in enumerate(heads)))
newbody = newbody.replace('<!--DM-HINH-->', bang_dm(dm['RMSFigureCaption'], 'Tên hình'))
newbody = newbody.replace('<!--DM-BANG-->', bang_dm(dm['RMSTableCaption'], 'Tên bảng'))
# Đoạn đệm sau bảng (GAP) mà đứng ngay trước một đoạn tự sang trang mới (pageBreakBefore) thì bỏ: khi bảng vừa đầy trang,
# đoạn đệm bị đẩy sang trang sau và đoạn kế lại ngắt trang lần nữa, sinh ra một trang trắng (trước đây là trang sau danh sách bảng).
newbody = re.sub(re.escape(GAP) + r'(?=<w:p><w:pPr><w:pStyle w:val="[^"]+"/><w:pageBreakBefore/>)', '', newbody)
json.dump({'hinh': [d[0] for d in dm['RMSFigureCaption']], 'bang': [d[0] for d in dm['RMSTableCaption']], 'head': [h[1] for h in heads]},
          open(f'{HERE}/dm_ds.json', 'w'), ensure_ascii=False)
assert 'Consolas' not in newbody
x = x[:b0] + newbody + x[sect:]
open(f'{D}/word/document.xml', 'w', encoding='utf-8').write(x)

# ---------- Quan hệ: link, ảnh
rp = f'{D}/word/_rels/document.xml.rels'; r = open(rp, encoding='utf-8').read(); add = ''
for rid, typ, tg, ext in ltnc_lib.RELS:
    add += f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="{esc(tg)}" TargetMode="External"/>'
for rid, path, name in ltnc_lib.IMAGES:
    shutil.copy(path, f'{D}/word/media/{name}')
    add += f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{name}"/>'
    names.append(f'word/media/{name}')
open(rp, 'w', encoding='utf-8').write(r.replace('</Relationships>', add + '</Relationships>'))

# ---------- Style: Times New Roman 13 mọi chữ, giãn dòng 1,3, không nghiêng, chú thích không đậm
sp = f'{D}/word/styles.xml'; st = open(sp, encoding='utf-8').read()
st = re.sub(r'<w:i/>|<w:iCs/>|<w:i w:val="(?:1|true)"/>', '', st)
st = re.sub(r'<w:sz w:val="\d+"/>', '<w:sz w:val="26"/>', st)
st = re.sub(r'<w:szCs w:val="\d+"/>', '<w:szCs w:val="26"/>', st)
st = re.sub(r'w:line="360"', 'w:line="312"', st)
def _gian(m):
    return re.sub(r'w:line="\d+"', 'w:line="312"', m.group(0))
st = re.sub(r'<w:style [^>]*w:styleId="(?:TOC[123]|RMSFigureCaption|RMSTableCaption|RMSTOF)".*?</w:style>', _gian, st, flags=re.S)
def _nobold(m):
    return re.sub(r'<w:b/>|<w:bCs/>', '', m.group(0))
st = re.sub(r'<w:style [^>]*w:styleId="(?:RMSFigureCaption|RMSTableCaption)".*?</w:style>', _nobold, st, flags=re.S)
st = re.sub(r'(<w:style [^>]*w:styleId="RMSBody".*?)<w:ind w:firstLine="\d+"/>', r'\1<w:ind w:firstLine="567"/>', st, count=1, flags=re.S)
st = re.sub(r'w:ascii="Consolas" w:hAnsi="Consolas" w:eastAsia="Consolas" w:cs="Consolas"', 'w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="Times New Roman" w:cs="Times New Roman"', st)
open(sp, 'w', encoding='utf-8').write(st)
# Header: tên môn
for h in [n for n in os.listdir(f'{D}/word') if n.startswith('header')]:
    p = f'{D}/word/{h}'; s = open(p, encoding='utf-8').read().replace('Triển khai phần mềm', 'Lập trình nâng cao')
    open(p, 'w', encoding='utf-8').write(s)
# Gạch đầu dòng 3 cấp: "-", "+", "✓" (Wingdings ü); danh sách số bắt đầu lại từ 1 mỗi lần
npth = f'{D}/word/numbering.xml'; n = open(npth, encoding='utf-8').read()
def _bullets(m):
    a = m.group(0)
    if '<w:lvl w:ilvl="2"' not in a:     # mẫu chỉ có 2 cấp: thêm cấp 3
        lv1 = re.search(r'<w:lvl w:ilvl="1".*?</w:lvl>', a, re.S).group(0)
        a = a.replace(lv1, lv1 + lv1.replace('w:ilvl="1"', 'w:ilvl="2"'), 1)
    for lv, ch, font, left in [(0, '-', 'Times New Roman', 567), (1, '+', 'Times New Roman', 851), (2, '\uf0fc', 'Wingdings', 1134)]:
        a = re.sub(rf'(<w:lvl w:ilvl="{lv}".*?<w:lvlText w:val=")[^"]*(")', rf'\g<1>{ch}\2', a, count=1, flags=re.S)
        a = re.sub(rf'(<w:lvl w:ilvl="{lv}".*?)<w:ind w:left="\d+" w:hanging="\d+"/>', rf'\1<w:ind w:left="{left}" w:hanging="284"/>', a, count=1, flags=re.S)
        a = re.sub(rf'(<w:lvl w:ilvl="{lv}".*?<w:rPr>)<w:rFonts [^>]*/>', rf'\1<w:rFonts w:ascii="{font}" w:hAnsi="{font}" w:hint="default"/>', a, count=1, flags=re.S)
    return a
n = re.sub(r'<w:abstractNum [^>]*w:abstractNumId="15".*?</w:abstractNum>', _bullets, n, count=1, flags=re.S)
absid = re.search(r'<w:num w:numId="18"[^>]*>\s*<w:abstractNumId w:val="(\d+)"/>', n).group(1)
n = n.replace('</w:numbering>', ''.join(f'<w:num w:numId="{k}"><w:abstractNumId w:val="{absid}"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/></w:lvlOverride></w:num>' for k in ltnc_lib.NUMS) + '</w:numbering>')
open(npth, 'w', encoding='utf-8').write(n)
sp = f'{D}/word/settings.xml'; s = open(sp, encoding='utf-8').read()
if 'updateFields' not in s:
    s = re.sub(r'(<w:(?:hdrShapeDefaults|footnotePr|endnotePr|compat)\b)', r'<w:updateFields w:val="true"/>\1', s, count=1)
open(sp, 'w', encoding='utf-8').write(s)
# Bỏ ảnh của mẫu không còn dùng
used = set(re.findall(r'r:embed="(rId\d+)"', open(f'{D}/word/document.xml', encoding='utf-8').read()))
if os.path.exists(OUT): os.remove(OUT)
with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zo:
    for nm in names:
        zo.write(f'{D}/{nm}', nm)
shutil.rmtree(D)
print('hình', len(dm['RMSFigureCaption']), '| bảng', len(dm['RMSTableCaption']), '| heading', len(heads), '| ảnh', len(ltnc_lib.IMAGES), '| link', len(ltnc_lib.RELS))
