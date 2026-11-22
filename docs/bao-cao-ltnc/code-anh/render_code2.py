"""Chụp ảnh đoạn code (tô màu cú pháp bằng Pygments, có số dòng) từ bản repo, render bằng Chromium (Playwright)."""
import json, html
from pygments import highlight
from pygments.lexers import get_lexer_for_filename
from pygments.formatters import HtmlFormatter
from playwright.sync_api import sync_playwright
snips = json.load(open('/w/code-anh/snippets2.json'))
css = HtmlFormatter(style='friendly').get_style_defs('.highlight')
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width': 1200, 'height': 400}, device_scale_factor=2)
    for name, path, s, e, title in snips:
        allines = open('/repo/' + path, encoding='utf-8').read().rstrip('\n').split('\n')
        e = min(e, len(allines)); lines = allines[s - 1:e]
        code = highlight('\n'.join(lines), get_lexer_for_filename(path), HtmlFormatter(linenos='table', linenostart=s, style='friendly'))
        doc = f"""<html><head><meta charset='utf-8'><style>{css}
body{{margin:0;background:#fff;font-family:'DejaVu Sans Mono',monospace}}
.box{{border:1px solid #B8C7DA;margin:8px;width:1170px}}
.t{{background:#EAF2FB;padding:6px 10px;font:600 14px 'DejaVu Sans';color:#12263F;border-bottom:1px solid #B8C7DA}}
.highlight pre,.linenos pre{{font-size:13px;line-height:1.45;margin:0;padding:6px 8px}}
.linenodiv pre,.linenos{{color:#7A8CA3;background:#F3F6FA}}
table{{border-spacing:0}}</style></head><body><div class='box'><div class='t'>{html.escape(path)} (dòng {s}–{e})</div>{code}</div></body></html>"""
        pg.set_content(doc); el = pg.query_selector('.box'); el.screenshot(path=f'/w/code-anh/{name}.png'); print(name)
    b.close()
