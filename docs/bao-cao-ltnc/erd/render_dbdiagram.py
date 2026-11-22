"""Render database diagram trên https://dbdiagram.io từ các file DBML (gen_dbml.py sinh ra): mở editor, dán mã DBML,
chọn thuật toán tự sắp xếp của dbdiagram, thu sơ đồ vừa khung rồi chụp ảnh. Không đăng nhập, không lưu diagram.
Chạy trong container mcr.microsoft.com/playwright/python, thư mục erd/ mount vào /w:
  python /w/render_dbdiagram.py dbml-p1-nhan-su.dbml ...
"""
import sys
from playwright.sync_api import sync_playwright

# Thuật toán sắp xếp của dbdiagram cho từng phân hệ (chọn cái cho ít đường cắt nhau nhất)
LAYOUT = {'p1a-tai-khoan': 'Pipeline', 'p1b-ca-lam': 'Pipeline', 'p2-thuc-don': 'Snowflake', 'p3a-goi-mon': 'Snowflake', 'p3b-thanh-toan': 'Snowflake', 'p4a-nhap-xuat': 'Pipeline', 'p4b-bep': 'Snowflake', 'p4c-kiem-ke': 'Pipeline', 'p5-tai-chinh': 'Pipeline'}   # thuật toán cho ít đường cắt nhất, chọn sau khi so 4 thuật toán
HIDE = """header, .controller-btn, [class*=controller], .minimap, .right-sidebar-toggle-btn, .panel-close-button,
          [class*=ad-], [class*=promo], [class*=Promo], [class*=banner], .monaco-editor, button { visibility: hidden !important; }"""


def render(pg, path):
    key = path.split('/')[-1].removeprefix('dbml-').removesuffix('.dbml')
    code = open(path, encoding='utf-8').read()
    pg.goto('https://dbdiagram.io/d', wait_until='networkidle', timeout=90000)
    pg.wait_for_timeout(3000)
    pg.keyboard.press('Escape')
    pg.click('.monaco-editor .view-lines')
    pg.keyboard.press('Control+A')
    pg.keyboard.press('Delete')
    pg.keyboard.insert_text(code)
    pg.wait_for_timeout(4000)
    loi = pg.inner_text('button:has(.fa-times-circle)').strip()
    if not loi.startswith('0'):
        raise SystemExit(f'{key}: DBML lỗi ({loi})')
    pg.click('button.dropbtn')
    pg.click(f"span.dropup-item-label:text-is('{ALG or LAYOUT.get(key, 'Pipeline')}')")
    pg.wait_for_timeout(800)
    pg.click('button:has-text("Confirm")')                                  # xác nhận sắp xếp lại
    pg.wait_for_timeout(2500)
    pg.click('button.ci-focus-btn', force=True)                             # vừa khung
    pg.wait_for_timeout(2000)
    pg.add_style_tag(content=HIDE)
    pg.wait_for_timeout(500)
    top = pg.evaluate("() => document.querySelector('header').getBoundingClientRect().bottom")
    ed = pg.evaluate("() => { const r = document.querySelector('.monaco-editor').getBoundingClientRect(); return r.width > 50 ? r.right : 0; }")
    left = ed + 2 if ed else 0
    vw, vh = pg.viewport_size['width'], pg.viewport_size['height']
    out = f'/w/dbml-{key}{SUFFIX}.png'
    pg.screenshot(path=out, clip={'x': left, 'y': top, 'width': vw - left, 'height': vh - top})
    cat_le(out)
    print(key, 'ok')


def cat_le(path, nen=(250, 250, 250), le=30):
    """Cắt khoảng trống quanh sơ đồ (bỏ qua dòng chữ chân trang của dbdiagram ở góc dưới)."""
    from PIL import Image, ImageChops
    im = Image.open(path).convert('RGB')
    w, h = im.size
    vung = im.crop((40, 40, w, h - 160))
    bbox = ImageChops.difference(vung, Image.new('RGB', vung.size, nen)).convert('L').point(lambda v: 255 if v > 12 else 0).getbbox()
    if bbox:
        x0, y0, x1, y1 = bbox
        im.crop((max(0, x0 + 40 - le), max(0, y0 + 40 - le), min(w, x1 + 40 + le), min(h, y1 + 40 + le))).save(path)


import os
ALG = os.environ.get('ALG')            # thử thuật toán khác: ALG=Snowflake SUFFIX=-snow
SUFFIX = os.environ.get('SUFFIX', '')
VW, VH = int(os.environ.get('VW', 1800)), int(os.environ.get('VH', 1200))

with sync_playwright() as p:
    b = p.chromium.launch()
    for f in sys.argv[1:]:
        pg = b.new_page(viewport={'width': VW, 'height': VH}, device_scale_factor=2)
        render(pg, '/w/' + f)
        pg.close()
    b.close()
