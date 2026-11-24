#!/usr/bin/env bash
# Dọn sau buổi demo: xóa nhánh feature/demo-loi-* (commit lỗi cố ý, không bao giờ được merge) trên GitHub và ở máy.
# Nhánh demo-doi-chu-* đã tự merge và Jenkins tự xóa sau khi merge, nên chỉ xóa bản ở máy nếu còn.
#
# Dùng (trong thư mục repo): bash scripts/demo/don-dep.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git fetch -q --prune origin
git switch -q main 2>/dev/null || true

for b in $(git branch -r --format='%(refname:short)' | sed -n 's#^origin/\(feature/demo-loi-.*\)#\1#p'); do
  echo "Xóa nhánh trên GitHub: $b"
  git push -q origin --delete "$b"
done
for b in $(git branch --format='%(refname:short)' | grep -E '^feature/demo-(loi|doi-chu|tra-lai)-' || true); do
  echo "Xóa nhánh ở máy: $b"
  git branch -q -D "$b"
done
echo "Xong."
