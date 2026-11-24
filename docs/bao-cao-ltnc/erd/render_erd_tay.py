"""Vẽ ERD trên dbdiagram.io với vị trí bảng và đường quan hệ do mình đặt (không dùng thuật toán tự xếp của dbdiagram).

Vì sao: thuật toán tự xếp để đường chạy sau bảng và nhiều quan hệ dùng chung một đoạn dọc, người đọc không biết đường nào
nối cột nào. Ở đây:
  - bảng đặt theo cột (COT bên dưới), bảng được tham chiếu nằm cạnh bảng tham chiếu tới nó;
  - mỗi quan hệ đi trong một "làn" riêng của khe giữa 2 cột (không 2 đường nào chung đoạn dọc);
  - quan hệ giữa 2 cột không kề nhau đi vòng qua hành lang trên hoặc dưới sơ đồ, cũng mỗi đường một làn;
  - đường đi đặt bằng checkPoints của dbdiagram (các điểm uốn), tọa độ cùng hệ với vị trí bảng.
Bề rộng bảng đo trên chính dbdiagram (ảnh chụp), chiều cao tính theo số cột: tiêu đề 32, mỗi dòng 32 đơn vị.

Chạy trong container Playwright, mount erd/ vào /w:
  docker run --rm -v $PWD:/w -w /w mcr.microsoft.com/playwright/python:v1.49.1-noble \
    sh -c 'pip install -q playwright==1.49.1 pillow==11.0.0 && python render_erd_tay.py [cụm ...]'
"""
import json, re, sys
from playwright.sync_api import sync_playwright
# Chép từ render_dbdiagram.py (file đó chạy ngay khi import)
HIDE = """header, .controller-btn, [class*=controller], .minimap, .right-sidebar-toggle-btn, .panel-close-button,
          [class*=ad-], [class*=promo], [class*=Promo], [class*=banner], .monaco-editor, button { visibility: hidden !important; }"""


def cat_le(path, nen=(250, 250, 250), le=30):
    """Cắt khoảng trống quanh sơ đồ (bỏ qua dòng chữ chân trang của dbdiagram ở góc dưới)."""
    from PIL import Image, ImageChops
    im = Image.open(path).convert('RGB')
    w, h = im.size
    vung = im.crop((40, 40, w, h - 160))
    bbox = ImageChops.difference(vung, Image.new('RGB', vung.size, nen)).convert('L').point(lambda v: 255 if v > 12 else 0).getbbox()
    if bbox:
        x0, y0, x1, y1 = bbox
        im = im.crop((max(0, x0 + 40 - le), max(0, y0 + 40 - le), min(w, x1 + 40 + le), min(h, y1 + 40 + le)))
    dam_net(im).save(path)


def dam_net(im):
    """dbdiagram vẽ chữ xám nhạt: kéo các điểm không phải nền về đậm hơn để in giấy vẫn rõ."""
    from PIL import ImageEnhance
    return ImageEnhance.Contrast(im.convert('RGB').point(lambda v: v if v > 225 else int(v * 0.6))).enhance(1.15)



# Cột từ trái sang phải; mỗi cột là danh sách bảng từ trên xuống. Bảng được tham chiếu đặt cạnh bảng tham chiếu tới nó.
COT = {
    'p1a-tai-khoan': [['departments', 'permissions'], ['employees', 'role_permissions'], ['accounts', 'roles'], ['account_roles', 'login_throttles']],
    'p1b-ca-lam': [['shifts', 'employees'], ['shift_assignments'], ['attendances'], ['attendance_adjustments']],
    'p2-thuc-don': [['menu_categories'], ['menu_items'], ['recipes'], ['recipe_ingredients', 'daily_menu_items'], ['kitchen_ingredient_types', 'daily_menus']],
    'p3a-goi-mon': [['dining_tables', 'employees'], ['table_sessions', 'order_batches'], ['orders', 'order_items'], ['daily_menu_items', 'order_item_events']],
    'p3b-thanh-toan': [['orders'], ['invoices', 'order_items', 'employees'], ['invoice_items', 'payment_requests'], ['payments'], ['payment_webhook_events']],
    'p4a-nhap-xuat': [['ingredients', 'stock_receipts'], ['kitchen_ingredient_types', 'stock_receipt_items'], ['inventory_batches', 'stock_issues'], ['stock_issue_items']],
    'p4b-bep': [['stock_issue_items', 'kitchen_ingredient_types'], ['kitchen_preparations'], ['kitchen_preparation_outputs'], ['kitchen_batches'],
                ['stock_reservations', 'stock_return_items'], ['order_items', 'stock_returns']],
    'p4c-kiem-ke': [['stock_adjustments', 'stock_counts'], ['stock_adjustment_items', 'stock_count_items'], ['inventory_batches', 'stock_issue_items', 'kitchen_batches'],
                    ['stock_movements'], ['kitchen_preparations', 'order_items', 'stock_return_items']],
    'p5-tai-chinh': [['stock_receipts'], ['operating_expenses'], ['daily_cash_reconciliations']],
}
HEAD, ROW = 32, 32          # chiều cao tiêu đề và mỗi dòng (đơn vị của dbdiagram)
LE, LAN = 44, 22            # lề trong khe giữa 2 cột, khoảng cách giữa 2 làn
GAP_DOC = 80                # khoảng cách dọc giữa 2 bảng cùng cột
S = "(()=>[...document.querySelectorAll('*')].find(e=>e.__vue_app__).__vue_app__.config.globalProperties.$store)()"


def doc_dbml(path):
    s = open(path, encoding='utf-8').read()
    bang = {}
    for m in re.finditer(r'^Table (\w+) \{(.*?)^\}', s, re.S | re.M):
        bang[m.group(1)] = [l.split()[0] for l in m.group(2).strip().split('\n') if l.strip() and not l.strip().startswith('Note')]
    return s, bang


def mo(pg, code):
    for lan_thu in range(3):   # dbdiagram đôi khi tải chậm
        try:
            pg.goto('https://dbdiagram.io/d', wait_until='domcontentloaded', timeout=90000)
            pg.wait_for_selector('.monaco-editor .view-lines', timeout=90000)
            break
        except Exception:
            if lan_thu == 2:
                raise
    pg.wait_for_timeout(3000)
    pg.keyboard.press('Escape')
    pg.click('.monaco-editor .view-lines')
    pg.keyboard.press('Control+A')
    pg.keyboard.press('Delete')
    pg.keyboard.insert_text(code.rstrip() + '\n\n')   # con trỏ đứng ở dòng trống: dbdiagram tô xanh Ref dưới con trỏ
    pg.wait_for_timeout(4000)
    loi = pg.inner_text('button:has(.fa-times-circle)').strip()
    if not loi.startswith('0'):
        raise SystemExit(f'DBML lỗi ({loi})')


def dat_vi_tri(pg, pos):
    pg.evaluate(f"(pos) => {{ {S}.commit('chart/setTablePositions', pos) }}",
                [{'name': n, 'schemaName': 'public', 'x': x, 'y': y} for n, (x, y) in pos.items()])
    pg.wait_for_timeout(800)


def chup(pg, out, thu_nho=False):
    # dbdiagram cập nhật vị trí bảng không đồng bộ: chờ rồi bấm "vừa khung" 2 lần, nếu không khung nhìn dùng vị trí cũ
    pg.wait_for_timeout(2500)
    for _ in range(2):
        pg.click('button.ci-focus-btn', force=True)
        pg.wait_for_timeout(1500)
    if thu_nho:   # "vừa khung" chỉ tính bảng, không tính đường đi qua hành lang trên/dưới: thu nhỏ một nấc cho đủ lề
        for _ in range(2):
            pg.click('button.zoom-btn', force=True)
            pg.wait_for_timeout(1000)
    # bấm vào vùng trống sát mép dưới để bỏ chọn (đường đang chọn hoặc đang trỏ chuột bị tô màu), rồi đưa chuột ra ngoài
    vw0, vh0 = pg.viewport_size['width'], pg.viewport_size['height']
    pg.mouse.click(vw0 * 0.62, vh0 - 6)
    pg.mouse.move(3, 3)
    pg.wait_for_timeout(500)
    pg.add_style_tag(content=HIDE)
    pg.wait_for_timeout(400)
    top = 0   # thanh tiêu đề đã ẩn; phần sơ đồ sát mép trên (hành lang trên) nằm dưới vị trí của nó
    ed = pg.evaluate("() => { const r = document.querySelector('.monaco-editor').getBoundingClientRect(); return r.width > 50 ? r.right : 0; }")
    left = ed + 2 if ed else 0
    vw, vh = pg.viewport_size['width'], pg.viewport_size['height']
    pg.screenshot(path=out, clip={'x': left, 'y': top, 'width': vw - left, 'height': vh - top})


def bo_to_sang(path):
    """dbdiagram đôi khi tô xanh lơ một đường (đường đang chọn): đưa các điểm xanh lơ sáng về xám như mọi đường khác.
    Thanh tiêu đề xanh đậm không bị đổi (kênh xanh lá thấp)."""
    from PIL import Image
    im = Image.open(path).convert('RGB')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b = px[x, y]
            if b - r > 60 and g > 90:
                v = int((0.3 * r + 0.59 * g + 0.11 * b) * 0.55)
                px[x, y] = (v, v, v)
    im.save(path)


def do_be_rong(pg, bang):
    """Đặt các bảng thành 1 cột dọc tại x = 0, chụp, tìm thanh tiêu đề (màu xanh) của từng bảng để đo bề rộng."""
    from PIL import Image
    ten = list(bang)
    pos, y = {}, 0
    for t in ten:
        pos[t] = (0, y)
        y += HEAD + ROW * len(bang[t]) + 120
    dat_vi_tri(pg, pos)
    chup(pg, '/tmp/do.png')
    im = Image.open('/tmp/do.png').convert('RGB')
    W, H = im.size
    px = im.load()
    xanh = lambda c: c[2] > 120 and c[2] - c[0] > 45 and c[0] < 140
    # các dải tiêu đề: hàng có nhiều điểm xanh liên tiếp
    dai = []
    yy = 0
    while yy < H:
        run = [x for x in range(W) if xanh(px[x, yy])]
        if len(run) > 30:
            y0 = yy
            while yy < H and sum(1 for x in range(0, W, 2) if xanh(px[x, yy])) > 15:
                yy += 1
            dai.append((y0, yy, min(run), max(run)))
        yy += 1
    if len(dai) != len(ten):
        raise SystemExit(f'đo được {len(dai)} tiêu đề, cần {len(ten)}')
    # tỉ lệ: khoảng cách dọc giữa tiêu đề đầu và cuối so với tọa độ đã đặt
    k = (dai[-1][0] - dai[0][0]) / (pos[ten[-1]][1] - pos[ten[0]][1]) if len(ten) > 1 else 0.5
    return {t: round((d[3] - d[2] + 1) / k) + 2 for t, d in zip(ten, dai)}


def bo_tri(cum, bang, refs, rong):
    cot = COT[cum]
    o_cot = {t: c for c, ds in enumerate(cot) for t in ds}
    thieu = set(bang) - set(o_cot)
    if thieu:
        raise SystemExit(f'{cum}: chưa xếp cột cho {thieu}')
    cao = {t: HEAD + ROW * len(bang[t]) for t in bang}
    # y trong cột; mỗi cột lệch một chút (không bội 32) để dòng của 2 cột khác nhau không trùng độ cao
    y_bang = {}
    for c, ds in enumerate(cot):
        y = (c * 11) % 32
        for t in ds:
            y_bang[t] = y
            y += cao[t] + GAP_DOC
    y_dong = lambda t, f: y_bang[t] + HEAD + ROW * bang[t].index(f) + ROW / 2
    day = max(y_bang[t] + cao[t] for t in bang)
    nc = len(cot)

    # Gán làn: khe g nằm giữa cột g và g+1
    lan = {g: [] for g in range(nc - 1)}
    dd = []
    for i, (ta, fa, tb, fb) in enumerate(refs):
        ca, cb = o_cot[ta], o_cot[tb]
        ya, yb = y_dong(ta, fa), y_dong(tb, fb)
        if abs(ca - cb) == 1 or ca == cb:
            g = min(ca, cb) if ca != cb else (ca if ca < nc - 1 else ca - 1)
            lan[g].append((min(ya, yb), i))
            dd.append({'kieu': 'ke', 'g': g, 'ya': ya, 'yb': yb})
        else:
            ga = ca if cb > ca else ca - 1
            gb = cb - 1 if cb > ca else cb
            lan[ga].append((ya, i)); lan[gb].append((yb, i))
            dd.append({'kieu': 'xa', 'ga': ga, 'gb': gb, 'ya': ya, 'yb': yb})
    so_lan = {g: len(v) for g, v in lan.items()}
    rong_cot = [max(rong[t] for t in ds) for ds in cot]
    x_cot, x = [], 0
    for c in range(nc):
        x_cot.append(x)
        if c < nc - 1:
            x += rong_cot[c] + 2 * LE + max(so_lan[c] - 1, 0) * LAN
    pos = {t: (x_cot[o_cot[t]], y_bang[t]) for t in bang}
    # x của từng làn: sắp theo độ cao để ít đường cắt nhau
    x_lan = {}
    for g, v in lan.items():
        x0 = x_cot[g] + rong_cot[g] + LE
        for k, (_, i) in enumerate(sorted(v)):
            x_lan.setdefault(i, []).append((g, x0 + k * LAN))
    def lx(i, g):
        return [x for gg, x in x_lan[i] if gg == g][0] if len([1 for gg, _ in x_lan[i] if gg == g]) == 1 else x_lan[i][0][1]
    # hành lang trên và dưới cho đường nối 2 cột không kề nhau
    duoi, tren = day + 60, -60
    cp = {}
    for i, d in enumerate(dd):
        if d['kieu'] == 'ke':
            x1 = lx(i, d['g'])
            cp[i] = [(x1, d['ya']), (x1, d['yb'])]
        else:
            xa = [x for g, x in x_lan[i] if g == d['ga']][0]
            xb = [x for g, x in x_lan[i] if g == d['gb']][0]
            # luôn đi hành lang dưới: sau khi "vừa khung" dbdiagram đặt sơ đồ sát mép trên, hành lang trên bị cắt mất
            yc = duoi; duoi += LAN
            cp[i] = [(xa, d['ya']), (xa, yc), (xb, yc), (xb, d['yb'])]
    return pos, cp


def ve(pg, path, cum):
    code, bang = doc_dbml(path)
    mo(pg, code)
    rp = pg.evaluate(f"() => JSON.parse(JSON.stringify({S}.state.chart.referencePaths))")
    refs = [(r['firstTableName'], r['firstFieldNames'][0], r['secondTableName'], r['secondFieldNames'][0]) for r in rp]
    rong = do_be_rong(pg, bang)
    pos, cp = bo_tri(cum, bang, refs, rong)
    dat_vi_tri(pg, pos)
    for i, r in enumerate(rp):
        r['checkPoints'] = [{'x': round(x, 1), 'y': round(y, 1)} for x, y in cp[i]]
    pg.evaluate(f"(rp) => {{ {S}.commit('chart/setReferencePaths', rp) }}", rp)
    pg.wait_for_timeout(1500)
    out = f'/w/dbml-{cum}.png'
    chup(pg, out, thu_nho=True)
    cat_le(out)
    bo_to_sang(out)
    print(cum, 'ok', {t: rong[t] for t in bang})


if __name__ == '__main__':
    cums = sys.argv[1:] or list(COT)
    with sync_playwright() as p:
        b = p.chromium.launch()
        for cum in cums:
            pg = b.new_page(viewport={'width': int(__import__('os').environ.get('VW', 3600)), 'height': int(__import__('os').environ.get('VH', 2200))}, device_scale_factor=2)
            ve(pg, f'/w/dbml-{cum}.dbml', cum)
            pg.close()
        b.close()
