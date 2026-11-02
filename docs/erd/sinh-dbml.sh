#!/usr/bin/env bash
# Sinh lại sơ đồ DBML cho https://dbdiagram.io/d từ docs/04-schema-v4.1.sql: nạp schema vào PostgreSQL tạm (không mạng,
# tự xóa), đọc catalog (sinh-dbml.sql), viết rms-schema.dbml (48 bảng) và rms-schema-p1..p5.dbml (từng phân hệ).
# Chạy lại mỗi khi đổi schema. Cần docker và python3:  bash docs/erd/sinh-dbml.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ERD=$ROOT/docs/erd
NAME=rms-dbml-$$
docker run -d --rm --name "$NAME" --network none -e POSTGRES_PASSWORD=x \
  -v "$ROOT/docs/04-schema-v4.1.sql:/s.sql:ro" postgres:17 >/dev/null
trap 'docker stop "$NAME" >/dev/null' EXIT
until docker exec "$NAME" pg_isready -U postgres -q 2>/dev/null; do sleep 1; done
sleep 2   # image postgres báo sẵn sàng một lần trong lúc khởi tạo rồi mới khởi động lại thật
docker exec "$NAME" psql -q -U postgres -v ON_ERROR_STOP=1 -f /s.sql >/dev/null
CATALOG=$(mktemp)
docker exec -i "$NAME" psql -U postgres -At < "$ERD/sinh-dbml.sql" > "$CATALOG"
python3 -I "$ERD/sinh-dbml.py" "$CATALOG" "$ROOT/docs/04-schema-v4.1.sql" "$ROOT/docs/02-danh-gia-database.md" "$ERD/rms-schema.dbml"
rm -f "$CATALOG"
