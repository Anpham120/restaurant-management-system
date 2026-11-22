"""Chụp giao diện thật của hệ thống (môi trường báo cáo) bằng Playwright: đăng nhập qua form như người dùng rồi mở
từng màn hình. Chạy trong container mcr.microsoft.com/playwright/python (mạng host)."""
import os, sys
from playwright.sync_api import sync_playwright
BASE = 'http://127.0.0.1:28080'
PW = open('/w/.manager_pw').read().strip()
OPEN_ORDER, PAID_ORDER, PENDING_ORDER = sys.argv[1:4]
SHOTS = [('01-dang-nhap', '/login', None), ('02-tong-quan', '/dashboard', None), ('03-so-do-ban', '/tables', None),
         ('04-goi-mon', f'/orders/{OPEN_ORDER}', None), ('05-bep', '/kitchen', None),
         ('06-hoa-don-cho-thu', f'/orders/{PENDING_ORDER}/invoice', None), ('07-hoa-don-da-thu', f'/orders/{PAID_ORDER}/invoice', None),
         ('08-mon-an', '/menu/items', None), ('09-thuc-don-ngay', '/menu/daily', None), ('10-nguyen-lieu', '/inventory/ingredients', None),
         ('11-phieu-nhap', '/inventory/receipts', None), ('12-phieu-xuat', '/inventory/issues', None), ('13-ton-kho', '/inventory/stock', None),
         ('14-nhan-vien', '/staff', None), ('15-ca-lam', '/shifts', None), ('16-cham-cong', '/attendance', None),
         ('17-khoan-chi', '/finance/expenses', None), ('18-doi-soat', '/finance/cash', None), ('19-bao-cao', '/finance/reports', None),
         ('20-su-co-thanh-toan', '/finance/incidents', None)]
os.makedirs('/w/anh', exist_ok=True)
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 1440, 'height': 900}, locale='vi-VN')
    pg.goto(BASE + '/login'); pg.wait_for_load_state('networkidle')
    pg.screenshot(path='/w/anh/01-dang-nhap.png')
    pg.fill('#username', 'manager'); pg.fill('#password', PW); pg.click('button.btn-primary'); pg.wait_for_load_state('networkidle')
    pg.wait_for_timeout(1500)
    for name, path, _ in SHOTS[1:]:
        pg.goto(BASE + path); pg.wait_for_load_state('networkidle'); pg.wait_for_timeout(1200)
        pg.screenshot(path=f'/w/anh/{name}.png')
        print(name, pg.url)
    # Lỗi nghiệp vụ hiển thị trên giao diện: đăng nhập sai
    pg2 = b.new_page(viewport={'width': 1440, 'height': 900})
    pg2.goto(BASE + '/login'); pg2.fill('#username', 'nv02'); pg2.fill('#password', 'sai-mat-khau'); pg2.click('button.btn-primary')
    pg2.wait_for_timeout(1500); pg2.screenshot(path='/w/anh/21-dang-nhap-sai.png')
    b.close()
