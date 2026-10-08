# Build stage on glibc (Debian): npm does not install lightningcss' musl binary on Alpine.
# Node 24, the version the tests run on (Jenkinsfile): a new major moves all of them together.
FROM node:24-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Runtime stage: Nginx serves the app and forwards /api and /ws to the backend. The label lets deploy.sh and the
# Jenkinsfile clear the images of old commits.
FROM nginx:1.31-alpine
LABEL khoibep.build=true
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
