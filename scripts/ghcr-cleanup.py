"""Dọn image phát hành cũ trên GHCR: mỗi package (rms-app, rms-web) giữ KEEP bản có tag mới nhất, cộng mọi tag trong
PROTECT (tag đang chạy ở staging/production, đọc từ overlay Kubernetes). Bản không có tag (manifest con của image
index, attestation) không bao giờ bị xóa ở đây: xóa chúng có thể làm hỏng image còn giữ.

Chạy trong container tạm (Jenkins không có python):
  docker run --rm -i -e GHCR_TOKEN -e KEEP=20 -e PROTECT="<sha> <sha>" python:3.12-alpine python - < scripts/ghcr-cleanup.py
Cần GHCR_TOKEN: token GitHub classic có read:packages + delete:packages (credential Jenkins "ghcr-token").
DRY_RUN=1: chỉ in sẽ xóa gì. Lỗi dọn không làm hỏng phát hành (Jenkinsfile gọi với || true).
"""
import json
import os
import sys
import urllib.error
import urllib.request

API = 'https://api.github.com/user/packages/container'
PACKAGES = ['rms-app', 'rms-web']
TOKEN = os.environ.get('GHCR_TOKEN', '')
KEEP = int(os.environ.get('KEEP', '20'))
PROTECT = set(os.environ.get('PROTECT', '').split())
DRY_RUN = os.environ.get('DRY_RUN') == '1'


def call(method, url):
    req = urllib.request.Request(url, method=method, headers={
        'Authorization': f'Bearer {TOKEN}', 'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28'})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read()
        return json.loads(body) if body else None, r.headers


def versions(pkg):
    out, page = [], 1
    while True:
        data, _ = call('GET', f'{API}/{pkg}/versions?per_page=100&page={page}')
        if not data:
            return out
        out += data
        page += 1


def main():
    if not TOKEN:
        print('[ghcr-cleanup] thiếu GHCR_TOKEN: bỏ qua'); return 0
    _, headers = call('GET', 'https://api.github.com/user')
    scopes = {s.strip() for s in (headers.get('X-OAuth-Scopes') or '').split(',')}
    if 'delete:packages' not in scopes:
        print('[ghcr-cleanup] token thiếu scope delete:packages: bỏ qua (cấp thêm cho token của credential ghcr-token)')
        return 0
    for pkg in PACKAGES:
        tagged = [v for v in versions(pkg) if v['metadata']['container']['tags']]
        tagged.sort(key=lambda v: v['created_at'], reverse=True)
        keep_ids = {v['id'] for v in tagged[:KEEP]}
        keep_ids |= {v['id'] for v in tagged if PROTECT & set(v['metadata']['container']['tags'])}
        old = [v for v in tagged if v['id'] not in keep_ids]
        print(f'[ghcr-cleanup] {pkg}: {len(tagged)} bản có tag, giữ {len(keep_ids)}, xóa {len(old)}'
              + (' (DRY_RUN)' if DRY_RUN else ''))
        for v in old:
            tags = ','.join(t[:7] for t in v['metadata']['container']['tags'])
            if not DRY_RUN:
                call('DELETE', f"{API}/{pkg}/versions/{v['id']}")
            print(f'  - {tags} ({v["created_at"][:10]})')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except urllib.error.HTTPError as e:
        print(f'[ghcr-cleanup] GitHub API lỗi HTTP {e.code}: bỏ qua lần này'); sys.exit(0)
