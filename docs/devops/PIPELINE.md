# Quy trình CI/CD (SCM + Jenkins + Docker + Nginx + Prometheus + Grafana)

Không có bước duyệt tay. Mọi thứ đi từ commit đến production đều do Jenkins chạy.

## 1. Mô hình nhánh

```
feature/xxx ──(CI pass, tự merge --no-ff)──► develop ──(CI pass, tự promote ff-only)──► main ──► DEPLOY
```

| Nhánh | Tạo từ | Pipeline chạy | Kết quả khi CI pass |
|---|---|---|---|
| `feature/*` | `develop` | Test backend + frontend | Tự merge vào `develop` |
| `develop` | — | Test backend + frontend | Tự promote lên `main` (fast-forward) |
| `main` | — | Test backend + frontend, deploy | Deploy production, tự quay lui nếu lỗi |

Quy tắc phát triển:
1. `git switch develop && git switch -c feature/ten-tinh-nang`
2. Làm việc, commit, `git push -u origin feature/ten-tinh-nang`
3. Jenkins test. Nếu pass, nhánh tự merge vào `develop`. Nếu fail, nhánh không được merge.
4. `develop` test lại. Nếu pass, tự đẩy lên `main` và `main` tự deploy.

Lưu ý: không commit trực tiếp lên `main` hoặc `develop`. Nếu làm vậy, lần promote sau sẽ không fast-forward được và pipeline báo đỏ.

## 2. Pipeline (`Jenkinsfile`)

| Stage | Chạy ở | Làm gì |
|---|---|---|
| Test backend | mọi nhánh | `mvn verify` (unit + `*IT` dùng Testcontainers) trong container `maven:3.9-eclipse-temurin-21` |
| Test frontend | mọi nhánh | `npm ci`, `typecheck`, `build` trong container `node:22-alpine` |
| Merge feature vào develop | `feature/*` | `scripts/auto-merge.sh develop` |
| Promote develop lên main | `develop` | `scripts/auto-merge.sh main` |
| Deploy production | `main` | `scripts/deploy.sh` trong thư mục `/home/coder/rms-deploy` |

Deploy (`scripts/deploy.sh`):
- Lấy mã `main` mới nhất, build image `app` và `web`, chạy `docker compose up --no-deps --wait`.
- Kiểm tra `/actuator/health` và trang web. Lỗi thì tự tag lại image cũ và chạy lại.
- Không đụng container `db` và volume `restaurant-management-system_pgdata`.

## 3. Thiết lập một lần

### 3.1 Token GitHub
Tạo token (fine-grained, quyền Contents: read/write trên repo này). Lưu vào Jenkins:
**Manage Jenkins → Credentials → Secret text**, ID: `github-token`.

### 3.2 Job Jenkins
**New Item → Multibranch Pipeline**, tên `rms`:
- Branch Sources → GitHub → repo `Anpham120/restaurant-management-system`, credential là token ở trên.
- Build Configuration: Jenkinsfile tại `Jenkinsfile`.
- Scan Multibranch Pipeline Triggers: định kỳ 1 phút (đã có `pollSCM` trong Jenkinsfile).

Tùy chọn: thêm webhook GitHub tới `https://jenkins.atlasplatform12.tech/github-webhook/` để chạy ngay khi push, thay cho polling.

### 3.3 Thư mục triển khai
```bash
git clone --branch main https://github.com/Anpham120/restaurant-management-system.git /home/coder/rms-deploy
cp /home/coder/projects/RSM/restaurant-management-system/.env /home/coder/rms-deploy/.env
```
`.env` chứa bí mật, chỉ nằm trên máy chủ, không commit. Kiểm tra uid của user `coder` bằng `id -u coder`. Nếu khác 1000 thì sửa `chown -R 1000:1000` trong `Jenkinsfile`.

### 3.4 Giám sát
```bash
cd /home/coder/projects/RSM/restaurant-management-system/monitoring
echo "GRAFANA_ADMIN_PASSWORD=<mật khẩu mạnh>" > .env
docker compose up -d
```
Truy cập Grafana bằng SSH tunnel: `ssh -L 3000:127.0.0.1:3000 <user>@<ip>`, rồi mở `http://127.0.0.1:3000`.
Import dashboard có sẵn từ grafana.com: **4701** (JVM Micrometer) và **1860** (Node Exporter Full). Datasource Prometheus đã được cấu hình sẵn.

Prometheus chỉ thấy app sau khi bản có `micrometer-registry-prometheus` được deploy. Trước đó target `rms-app` sẽ báo DOWN.

## 4. Quay lui và giới hạn

- Quay lui tự động chỉ áp dụng cho image `app` và `web`. Flyway là migration tiến một chiều: nếu bản mới đã migrate schema thì quay lui image không hoàn tác được DB. Vì vậy thay đổi schema phải tương thích ngược.
- Không có cổng duyệt tay nghĩa là lỗi logic không bị test bắt được sẽ lên production sau khi pass CI. Đây là đánh đổi của yêu cầu tự động hóa hoàn toàn.
- `/actuator/prometheus` được permitAll để Prometheus scrape. Nginx máy chủ chỉ proxy `/api/` và `/actuator/health`, nên đường dẫn này không lộ ra internet. App chỉ lắng nghe `127.0.0.1:8081`.

## 5. Kiểm tra nhanh

```bash
# Cú pháp script
bash -n scripts/auto-merge.sh && bash -n scripts/deploy.sh
# Cấu hình monitoring
cd monitoring && GRAFANA_ADMIN_PASSWORD=x docker compose config >/dev/null && echo OK
# Trạng thái deploy
curl -s http://127.0.0.1:8081/actuator/health
```
