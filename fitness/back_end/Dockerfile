# syntax=docker/dockerfile:1
# Build 2 giai đoạn: image cuối chỉ có JRE + file jar, không có Maven và mã nguồn.

# ---------- 1. Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Tải dependency trước (layer này được cache, chỉ build lại khi pom.xml đổi)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Test cần MySQL + Redis thật nên chạy riêng (mvn test / CI), không chạy lúc build image
RUN mvn -B -q package -DskipTests

# ---------- 2. Run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Không chạy bằng root
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /workspace/target/fitness-*.jar app.jar
USER spring

EXPOSE 8081
# Giờ server = giờ Việt Nam (LocalDateTime, job 00:05, gom doanh thu theo ngày đều dựa vào giờ server)
ENV TZ=Asia/Ho_Chi_Minh
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
