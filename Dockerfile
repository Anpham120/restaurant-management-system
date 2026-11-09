# Backend: build bằng JDK, chạy bằng JRE với user thường. Jar được tách lớp (thư viện / ứng dụng) để mỗi bản phát hành
# chỉ khác lớp ứng dụng vài trăm KB: đẩy GHCR, lưu 5 image phát hành trên máy và kéo về đều nhẹ.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY --chmod=755 mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY src/main src/main
# Cache thư viện Maven giữa các lần build (BuildKit cache mount): không nằm trong image, không tải lại khi pom.xml đổi.
# Bỏ qua cả biên dịch test: test đã chạy ở stage "Test backend" của pipeline, image không chứa test.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -Dmaven.test.skip=true package \
 && java -Djarmode=tools -jar target/restaurant-core-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre
# Cài bản vá bảo mật Ubuntu đã phát hành mà image nền chưa kịp build lại (ví dụ CVE-2026-109518 của apt: image nền
# 29/10 còn apt 3.2.0, bản sửa 3.2.1ubuntu0.1). Không có bước này Trivy chặn pipeline đến khi image nền được build lại.
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get -y --no-install-recommends upgrade \
 && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 app
WORKDIR /app
# Thứ tự từ ít đổi đến hay đổi: lớp thư viện được Docker cache và dùng chung giữa các bản phát hành
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8081
# Hết bộ nhớ thì thoát hẳn để Docker khởi động lại (restart: unless-stopped), không treo ở trạng thái hỏng
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
