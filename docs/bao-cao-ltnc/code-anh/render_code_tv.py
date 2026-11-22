"""Chụp ảnh đoạn code của từng thành viên (nhiều khoảng dòng trong một ảnh), tô màu bằng Pygments, render bằng Chromium."""
import json, html, sys
from pygments import highlight
from pygments.lexers import get_lexer_for_filename
from pygments.formatters import HtmlFormatter
from playwright.sync_api import sync_playwright
snips = json.load(open('/w/code-anh/snippets-tv.json'))
css = HtmlFormatter(style='friendly').get_style_defs('.highlight')
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width': 1200, 'height': 400}, device_scale_factor=2)
    for name, path, ranges, title in snips:
        allines = open('/repo/' + path, encoding='utf-8').read().rstrip('\n').split('\n')
        parts = []
        for s, e in ranges:
            src = '\n'.join(allines[s - 1:e])
            code = highlight(src, get_lexer_for_filename(path), HtmlFormatter(linenos='table', linenostart=s, style='friendly'))
            parts.append(code)
        body = "<div class='sep'>…</div>".join(parts)
        dai = max(len(allines[i]) for s, e in ranges for i in range(s - 1, e))
        co = 13 if dai <= 140 else max(9, int(13 * 140 / dai))   # dòng dài: thu nhỏ chữ để không bị cắt
        dong = ', '.join(str(s) if s == e else f'{s}–{e}' for s, e in ranges)
        doc = f"""<html><head><meta charset='utf-8'><style>{css}
body{{margin:0;background:#fff;font-family:'DejaVu Sans Mono',monospace}}
.box{{border:1px solid #B8C7DA;margin:8px;width:1170px}}
.t{{background:#EAF2FB;padding:6px 10px;font:600 14px 'DejaVu Sans';color:#12263F;border-bottom:1px solid #B8C7DA}}
.sep{{background:#F3F6FA;color:#7A8CA3;padding:2px 10px;font-size:13px;border-top:1px dashed #B8C7DA;border-bottom:1px dashed #B8C7DA}}
.highlight pre,.linenos pre{{font-size:{co}px;line-height:1.45;margin:0;padding:6px 8px}}
.linenodiv pre,.linenos{{color:#7A8CA3;background:#F3F6FA}}
table{{border-spacing:0}}</style></head><body><div class='box'><div class='t'>{html.escape(path)} (dòng {dong})</div>{body}</div></body></html>"""
        pg.set_content(doc); pg.query_selector('.box').screenshot(path=f'/w/code-anh/{name}.png'); print(name)
    b.close()
