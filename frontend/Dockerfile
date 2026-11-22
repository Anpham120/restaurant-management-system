# Frontend: Node + Vite build ra file tĩnh; chạy bằng nginx không cần root (cổng 8080, user nginx uid 101).
# /api do nginx máy chủ chuyển thẳng vào backend, container này chỉ phục vụ SPA.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
# Cache tải gói npm giữa các lần build (BuildKit cache mount); npm ci vẫn cài đúng theo package-lock.json
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30-alpine
# Lấy bản vá Alpine mới hơn image gốc (Trivy kiểm ở pipeline). PATCH_DATE đổi mỗi ngày (scripts/staging-test.sh)
# nên lớp này được build lại hằng ngày, không bị cache giữ bản cũ (develop #60: nginx 1.30.6 còn CVE dù đã có 1.30.7).
ARG PATCH_DATE=none
USER root
# nginx trong image gốc cài từ kho nginx.org và bị ghim đúng phiên bản trong /etc/apk/world (nginx=1.30.6-r1),
# nên "apk upgrade" thường không nâng được nginx. Bước này: bỏ ghim, tạm thêm kho nginx.org để lấy bản vá cùng
# nhánh 1.30, nâng cấp mọi gói, rồi gỡ kho ra (develop #60-#62: CVE nginx 1.30.6, đã có 1.30.7). Khóa ký nginx.org đã có
# sẵn trong image gốc (/etc/apk/keys/nginx_signing.rsa.pub), không tải lại: URL khóa trên nginx.org từng trả 404
# làm hỏng build (develop #94).
RUN echo "patch $PATCH_DATE" \
 && sed -i 's/=.*//' /etc/apk/world \
 && test -s /etc/apk/keys/nginx_signing.rsa.pub \
 && echo "https://nginx.org/packages/alpine/v$(cut -d. -f1,2 /etc/alpine-release)/main" >> /etc/apk/repositories \
 && apk upgrade --no-cache \
 && sed -i '/nginx.org/d' /etc/apk/repositories \
 && nginx -v
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
