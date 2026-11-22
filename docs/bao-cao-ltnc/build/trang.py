import sys,json,glob,re
S=sys.argv[1]; ds=json.load(open(f'{S}/dm_ds.json'))
pages={int(re.search(r't-(\d+)',f).group(1)):open(f,encoding='utf-8').read() for f in glob.glob(f'{S}/render/t-*.txt')}
def footer(t):
    l=[x.strip() for x in t.strip().split('\n') if x.strip()]
    return l[-1] if l and re.fullmatch(r'\d+',l[-1]) else None
res={}
for so in ds['hinh']+ds['bang']:
    for i in sorted(pages):
        if re.search(rf'^\s*{re.escape(so)}\.\s', pages[i], re.M) and 'Số hiệu' not in pages[i]:
            res[so]=footer(pages[i]); break
bat_dau = max(i for i in pages if 'DANH SÁCH BẢNG' in pages[i])
for h in ds.get('head', []):
    key = h[:40]
    for i in sorted(pages):
        t = pages[i]
        if i <= bat_dau: continue   # bỏ trang bìa, mục lục, danh mục
        if any(l.strip().startswith(key) for l in t.split('\n')):
            res['H:' + h] = footer(t); break
old=json.load(open(f'{S}/trang.json')); json.dump(res, open(f'{S}/trang.json','w'), ensure_ascii=False)
print('đổi:', {k:(old.get(k),v) for k,v in res.items() if old.get(k)!=v} or 'không')
