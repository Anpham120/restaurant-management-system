"""Thư viện dựng khối Word (document.xml) theo đúng kiểu của báo cáo: RMSBody, RMSList, bảng TableGrid, RMSCode, hình."""
import re, html, struct
NS_IMG = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"'
CODE_RPR = '<w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/><w:sz w:val="23"/></w:rPr>'
REFS = {}      # số -> URL (None = không có link)
RELS = []      # quan hệ mới: (rId, type, target, external)
_rid = [900]
_doc = [880000000]
NUMS = []      # numId mới cho danh sách đánh số

def esc(t): return html.escape(t, quote=False)

def new_rid():
    _rid[0] += 1; return f'rId{_rid[0]}'

def link_rid(url):
    for r, t, tg, ext in RELS:
        if tg == url and ext: return r
    r = new_rid(); RELS.append((r, 'hyperlink', url, True)); return r

def runs(text, base_rpr=''):
    """`mã` = font Consolas, **đậm**, [n] = link tới tài liệu tham khảo n."""
    out = []
    for part in re.split(r'(`[^`]+`|\*\*[^*]+\*\*|\[\d+\])', text):
        if not part: continue
        if part.startswith('`'):
            # Lệnh, đường dẫn, tên hàm: chữ thường như nội dung (không đổi font) cho dễ đọc
            rp = f'<w:rPr>{base_rpr}</w:rPr>' if base_rpr else ''
            out.append(f'<w:r>{rp}<w:t xml:space="preserve">{esc(part[1:-1])}</w:t></w:r>')
        elif part.startswith('**'):
            out.append(f'<w:r><w:rPr><w:b/>{base_rpr}</w:rPr><w:t xml:space="preserve">{esc(part[2:-2])}</w:t></w:r>')
        elif re.fullmatch(r'\[\d+\]', part) and REFS.get(int(part[1:-1])):
            r = link_rid(REFS[int(part[1:-1])])
            out.append(f'<w:hyperlink r:id="{r}" w:history="1"><w:r><w:rPr><w:rStyle w:val="Hyperlink"/>{base_rpr}</w:rPr><w:t>{esc(part)}</w:t></w:r></w:hyperlink>')
        else:
            rp = f'<w:rPr>{base_rpr}</w:rPr>' if base_rpr else ''
            out.append(f'<w:r>{rp}<w:t xml:space="preserve">{esc(part)}</w:t></w:r>')
    return ''.join(out)

def P(style, text, extra=''):
    return f'<w:p><w:pPr><w:pStyle w:val="{style}"/>{extra}</w:pPr>{runs(text)}</w:p>'
def H1(t): return P('Heading1', t, '<w:pageBreakBefore/>')
def H2(t): return P('Heading2', t)
def H3(t): return P('Heading3', t)
def B(t): return P('RMSBody', t)
def L(t): return P('RMSList', t, '<w:numPr><w:ilvl w:val="0"/><w:numId w:val="17"/></w:numPr>')
def L2(t): return P('RMSList', t, '<w:numPr><w:ilvl w:val="1"/><w:numId w:val="17"/></w:numPr>')
def L3(t): return P('RMSList', t, '<w:numPr><w:ilvl w:val="2"/><w:numId w:val="17"/></w:numPr>')
def numbered(items):
    n = 100 + len(NUMS); NUMS.append(n)
    return ''.join(P('RMSList', t, f'<w:numPr><w:ilvl w:val="0"/><w:numId w:val="{n}"/></w:numPr>') for t in items)
def TC(t): return P('RMSTableCaption', t)
def FC(t): return P('RMSFigureCaption', t)
GAP = '<w:p><w:pPr><w:pStyle w:val="RMSFigure"/><w:keepNext w:val="0"/></w:pPr></w:p>'

def T(widths, rows, keep=None):
    """rows[0] = tiêu đề. widths tính theo dxa, tổng 9072."""
    grid = ''.join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    if keep is None:
        keep = len(rows) <= 9          # bảng ngắn: giữ trọn trên một trang
    def cell(w, t, head, kn=False):
        sh = '<w:shd w:val="clear" w:color="auto" w:fill="DCE9F7"/>' if head else ''
        jc = '<w:jc w:val="center"/>' if head else ''
        rp = '<w:b/><w:sz w:val="26"/>' if head else '<w:sz w:val="26"/>'
        return f'<w:tc><w:tcPr><w:tcW w:type="dxa" w:w="{w}"/>{sh}</w:tcPr><w:p><w:pPr><w:pStyle w:val="RMSTableText"/>{'<w:keepNext/>' if kn else ''}{jc}</w:pPr>{runs(t, rp)}</w:p></w:tc>'
    trs = []
    for i, r in enumerate(rows):
        tp = '<w:trPr><w:tblHeader w:val="true"/><w:cantSplit/></w:trPr>' if i == 0 else '<w:trPr><w:cantSplit/></w:trPr>'
        trs.append(f'<w:tr>{tp}' + ''.join(cell(w, t, i == 0, keep and i < len(rows) - 1) for w, t in zip(widths, r)) + '</w:tr>')
    return ('<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:type="dxa" w:w="9072"/><w:jc w:val="center"/><w:tblLayout w:type="fixed"/>'
            '<w:tblLook w:firstColumn="1" w:firstRow="1" w:lastColumn="0" w:lastRow="0" w:noHBand="0" w:noVBand="1" w:val="04A0"/></w:tblPr>'
            f'<w:tblGrid>{grid}</w:tblGrid>' + ''.join(trs) + '</w:tbl>' + GAP)

def CODE(lines):
    ps = ''.join(f'<w:p><w:pPr><w:pStyle w:val="RMSCode"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/></w:rPr><w:t xml:space="preserve">{esc(l)}</w:t></w:r></w:p>' for l in lines)
    return ('<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:type="auto" w:w="0"/><w:jc w:val="center"/><w:tblBorders>'
            '<w:top w:val="single" w:sz="4" w:color="B8C7DA"/><w:left w:val="single" w:sz="4" w:color="B8C7DA"/><w:bottom w:val="single" w:sz="4" w:color="B8C7DA"/>'
            '<w:right w:val="single" w:sz="4" w:color="B8C7DA"/><w:insideH w:val="nil"/><w:insideV w:val="nil"/></w:tblBorders>'
            '<w:tblLook w:firstColumn="1" w:firstRow="1" w:lastColumn="0" w:lastRow="0" w:noHBand="0" w:noVBand="1" w:val="04A0"/></w:tblPr>'
            '<w:tblGrid><w:gridCol w:w="9071"/></w:tblGrid><w:tr><w:tc><w:tcPr><w:tcW w:type="dxa" w:w="9071"/><w:shd w:val="clear" w:color="auto" w:fill="F3F6FA"/></w:tcPr>'
            + ps + '</w:tc></w:tr></w:tbl>' + GAP)

IMAGES = []    # (rId, đường dẫn nguồn, tên trong word/media)
def FIG(path, caption, maxw=5580000, maxh=7000000):
    b = open(path, 'rb').read(24); w, h = struct.unpack('>II', b[16:24])
    s = min(maxw / w, maxh / h); cx, cy = int(w * s), int(h * s)
    r = new_rid(); name = 'ltnc-' + path.split('/')[-1]
    IMAGES.append((r, path, name)); _doc[0] += 1
    return (f'<w:p><w:pPr><w:pStyle w:val="RMSFigure"/></w:pPr><w:r><w:drawing><wp:inline {NS_IMG}><wp:extent cx="{cx}" cy="{cy}"/>'
            f'<wp:docPr id="{_doc[0]}" name="Picture {_doc[0]}"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>'
            '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr>'
            f'<pic:cNvPr id="0" name="{name}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{r}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr></pic:pic></a:graphicData></a:graphic>'
            '</wp:inline></w:drawing></w:r></w:p>' + FC(caption))

def REF(n, text):
    """Mục tài liệu tham khảo: [n] mang link, rồi tab, rồi mô tả."""
    url = REFS.get(n)
    tag = (f'<w:hyperlink r:id="{link_rid(url)}" w:history="1"><w:r><w:rPr><w:rStyle w:val="Hyperlink"/></w:rPr><w:t>[{n}]</w:t></w:r></w:hyperlink>'
           if url else f'<w:r><w:t>[{n}]</w:t></w:r>')
    return f'<w:p><w:pPr><w:pStyle w:val="RMSList"/><w:ind w:left="510" w:hanging="510"/></w:pPr>{tag}<w:r><w:tab/></w:r>{runs(text)}</w:p>'
