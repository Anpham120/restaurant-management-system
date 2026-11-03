#!/usr/bin/env bash
# E2E ghi dữ liệu ở staging: đi hết một vòng nghiệp vụ qua REST API thật, trên bản sao dữ liệu production.
#   kho: nguyên liệu → nhập → xuất → bếp nhận
#   thực đơn: món + công thức → thêm vào thực đơn hôm nay (hạn mức 3)
#   phục vụ: mở bàn → gọi món → bếp nhận → nấu → xong → bưng → chốt hóa đơn → thu tiền mặt
# Kèm các ca lỗi (bước 6 của quy trình): số lượng âm/0, gửi trùng, vượt hạn mức, thu tiền thiếu,
# bấm thu tiền 2 lần cùng lúc. Tồn bếp được kiểm bằng psql (chỉ đọc) trên database staging.
# Gọi từ scripts/staging-test.sh: cần APP_URL, TOKEN (MANAGER staging) và biến compose của staging.
# Database staging bị xóa sau khi chạy, nên dữ liệu tạo ở đây không đi đâu cả.
set -uo pipefail

: "${APP_URL:?}" "${TOKEN:?}"
RUN=$(date +%s)
fail=0

ok()  { echo "[e2e] OK   $1"; }
bad() { echo "[e2e] FAIL $1"; fail=1; }

# call METHOD PATH [JSON]: đặt CODE (mã HTTP) và BODY
call() {
  local out
  out=$(curl -s -w '\n%{http_code}' -X "$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} "$APP_URL$2")
  CODE=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

err() { jq -c '.error // .' <<<"$BODY" 2>/dev/null | cut -c1-300; }

success() { [[ $CODE == 2?? ]]; }   # 200 hoặc 201 Created

# Bước bắt buộc của chuỗi: lỗi thì dừng kịch bản (các bước sau phụ thuộc)
step() {
  local name=$1; shift
  call "$@"
  if ! success; then
    bad "$name: HTTP $CODE $(err)"
    exit 1
  fi
}

# Ca kiểm tra: chỉ ghi nhận đạt/không đạt, chạy tiếp
expect() {
  local name=$1 want=$2; shift 2
  call "$@"
  if [ "$CODE" = "$want" ]; then ok "$name ($CODE)"; else bad "$name: mong $want, nhận $CODE $(err)"; fi
}

assert() { # assert "mô tả" <điều kiện bash>
  local name=$1; shift
  if "$@"; then ok "$name"; else bad "$name"; fi
}

d() { jq -r ".data$1" <<<"$BODY"; }
q() { docker compose exec -T db sh -c 'psql -tAq -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' _ "$1"; }
num_eq() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 == b + 0) }'; }

# ---------- thực đơn hôm nay: kiểm trước, đã đóng thì không gọi món được
call GET /api/daily-menus/today
if [ "$CODE" = 404 ]; then
  TODAY=$(date -u -d "@$(( $(date +%s) + 3 * 3600 ))" +%F)   # ngày kinh doanh: giờ VN (UTC+7), ngày mới bắt đầu 04:00
  step "tạo thực đơn hôm nay" POST /api/daily-menus "{\"businessDate\":\"$TODAY\",\"fromCatalog\":false}"
elif ! success; then
  bad "đọc thực đơn hôm nay: HTTP $CODE $(err)"; exit 1
fi
MENU=$(d .id)
MENU_STATUS=$(d .status)
if [ "$MENU_STATUS" = CLOSED ]; then
  echo "[e2e] BỎ QUA: thực đơn $(d .businessDate) đã đóng trong dữ liệu production, không gọi món được đến 04:00 ngày sau"
  exit 0
fi

# ---------- kho: 10 phần nguyên liệu vào bếp
step "tạo nguyên liệu" POST /api/ingredients "{\"name\":\"E2E $RUN\",\"baseUnit\":\"phần\",\"expiryTracked\":false}"
ING=$(d .id)
step "tạo loại bếp dùng thẳng" POST /api/kitchen-ingredient-types \
  "{\"rawIngredientId\":$ING,\"name\":\"E2E $RUN\",\"handlingType\":\"DIRECT\"}"
TYPE=$(d .id)
step "tạo phiếu nhập" POST /api/stock-receipts "{\"requestKey\":\"e2e-r-$RUN\",\"items\":[{\"ingredientId\":$ING,\
\"inputQuantity\":10,\"inputUnit\":\"phần\",\"conversionFactor\":1,\"unitPricePerInputUnit\":1000}]}"
RECEIPT=$(d .id)
step "xác nhận nhập kho" POST "/api/stock-receipts/$RECEIPT/confirm"
LOT=$(d '.lines[0].inventoryBatchId')
step "tạo phiếu xuất" POST /api/stock-issues \
  "{\"requestKey\":\"e2e-i-$RUN\",\"items\":[{\"inventoryBatchId\":$LOT,\"quantity\":10,\"fulfillmentMode\":\"DIRECT\"}]}"
ISSUE=$(d .id)
step "xuất kho" POST "/api/stock-issues/$ISSUE/issue"
step "bếp nhận hàng" POST "/api/stock-issues/$ISSUE/receive"
ok "nhập 10 phần → xuất → bếp nhận"

# ---------- món + công thức (1 phần/suất), thêm vào thực đơn với hạn mức 3 suất
step "tạo nhóm món" POST /api/menu-categories "{\"name\":\"E2E $RUN\"}"
CAT=$(d .id)
step "tạo món" POST /api/menu-items "{\"categoryId\":$CAT,\"name\":\"E2E $RUN\",\"price\":50000}"
ITEM=$(d .id)
step "tạo công thức" POST "/api/menu-items/$ITEM/recipes" \
  "{\"ingredients\":[{\"kitchenIngredientTypeId\":$TYPE,\"quantityPerServing\":1}]}"
step "thêm món vào thực đơn" POST "/api/daily-menus/$MENU/items" "{\"menuItemId\":$ITEM,\"quotaTotal\":3}"
DMI=$(jq -r ".data.items[] | select(.menuItemId == $ITEM) | .dailyMenuItemId" <<<"$BODY")
if [ "$MENU_STATUS" = DRAFT ]; then
  step "công bố thực đơn" POST "/api/daily-menus/$MENU/publish"
fi
ok "món mới có trong thực đơn (hạn mức 3)"

# ---------- mở bàn, gọi món
step "tạo bàn" POST /api/tables "{\"code\":\"E2E-$RUN\",\"capacity\":4}"
TABLE=$(d .id)
step "mở bàn" POST "/api/tables/$TABLE/sessions"
ORDER=$(d .orderId)

batch() { echo "{\"requestKey\":\"$1\",\"items\":[{\"dailyMenuItemId\":$DMI,\"quantity\":$2}]}"; }
expect "gọi món số lượng âm bị chặn" 400 POST "/api/orders/$ORDER/batches" "$(batch "e2e-neg-$RUN" -1)"
expect "gọi món số lượng 0 bị chặn" 400 POST "/api/orders/$ORDER/batches" "$(batch "e2e-zero-$RUN" 0)"

step "gọi 2 suất" POST "/api/orders/$ORDER/batches" "$(batch "e2e-b1-$RUN" 2)"
OI=$(d '.items[0].id')
call POST "/api/orders/$ORDER/batches" "$(batch "e2e-b1-$RUN" 2)"
assert "gửi lại cùng requestKey không tạo món trùng" \
  test "$(success && echo 2xx):$(d .replayed):$(d '.items[0].id')" = "2xx:true:$OI"

# ---------- bếp
step "bếp nhận món" POST "/api/order-items/$OI/accept"
assert "bếp nhận: ACCEPTED" test "$(d .result)" = ACCEPTED
assert "giữ 2 phần nguyên liệu (FEFO)" num_eq "$(q "SELECT COALESCE(SUM(reserved_quantity),0) FROM kitchen_batches WHERE kitchen_ingredient_type_id = $TYPE")" 2
step "bắt đầu nấu" POST "/api/order-items/$OI/start-cooking"
step "nấu xong" POST "/api/order-items/$OI/finish"
step "bưng món" POST "/api/order-items/$OI/serve"
assert "tồn bếp còn 8, không còn giữ chỗ" test \
  "$(q "SELECT SUM(quantity_on_hand)::int || '/' || SUM(reserved_quantity)::int FROM kitchen_batches WHERE kitchen_ingredient_type_id = $TYPE")" = "8/0"

# ---------- vượt hạn mức: đã dùng 2/3, gọi thêm 2
call POST "/api/orders/$ORDER/batches" "$(batch "e2e-b2-$RUN" 2)"
if success; then
  OI2=$(d '.items[0].id')
  step "bếp xét món vượt hạn mức" POST "/api/order-items/$OI2/accept"
  assert "vượt hạn mức bị từ chối" test "$(d .result)" = REJECTED
elif [ "$CODE" = 409 ]; then
  ok "vượt hạn mức bị chặn khi gọi (409)"
else
  bad "vượt hạn mức: HTTP $CODE $(err)"
fi

# ---------- hóa đơn, thanh toán
step "chốt hóa đơn" POST "/api/orders/$ORDER/invoice"
INV=$(d .invoiceId)
assert "hóa đơn = 2 x 50 000 (món bị từ chối không tính)" num_eq "$(d .total)" 100000
expect "thu tiền mặt thiếu bị chặn" 422 POST "/api/invoices/$INV/payments" '{"method":"CASH","cashReceived":1000}'

# Bấm thu tiền 2 lần cùng lúc: đúng 1 lần thành công
pay() {
  curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"method":"CASH","cashReceived":100000}' "$APP_URL/api/invoices/$INV/payments"
}
T=$(mktemp -d)
pay > "$T/1" & pay > "$T/2" & wait
CODES=$(cat "$T/1" "$T/2" | fold -w3 | sed 's/^20[01]$/2xx/' | sort | tr '\n' ' ')
rm -rf "$T"
assert "bấm thu tiền 2 lần cùng lúc: 1 thành công, 1 bị chặn ($CODES)" test "$CODES" = "2xx 409 "
assert "chỉ ghi 1 khoản thu" test "$(q "SELECT count(*) FROM payments WHERE invoice_id = $INV")" = 1

step "đọc hóa đơn" GET "/api/invoices/$INV"
assert "hóa đơn PAID" test "$(d .status)" = PAID
step "đọc bàn" GET "/api/tables/$TABLE"
assert "bàn trống lại sau thanh toán" test "$(d .status):$(d .currentSessionId)" = "AVAILABLE:null"

if [ "$fail" -ne 0 ]; then
  echo "[e2e] THẤT BẠI"
  exit 1
fi
echo "[e2e] đạt: luồng gọi món → bếp → thanh toán chạy đúng trên dữ liệu thật"
