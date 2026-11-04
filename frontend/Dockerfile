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
# Lấy bản vá Alpine mới hơn image gốc (Trivy kiểm ở pipeline). Cache build giữ lớp cũ thì: docker builder prune.
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
