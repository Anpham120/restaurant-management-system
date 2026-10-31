# Build: Node + Vite ra file tĩnh; chạy: nginx nhỏ phục vụ dist/ (SPA). /api do nginx máy chủ chuyển thẳng vào backend.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Nhánh stable của nginx; apk upgrade lấy bản vá Alpine mới hơn image gốc (Trivy kiểm tra ở pipeline)
FROM nginx:1.30-alpine
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
