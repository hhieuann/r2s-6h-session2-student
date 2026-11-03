# ==============================================================================
# R2S MASTERCLASS - SESSION 2: DOCKERIZE PRODUCTION LAB TASK
# Multi-stage build: Builder (Maven + JDK) -> Runtime (chỉ JRE, non-root)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Builder - biên dịch, đóng gói jar và tách Spring Boot layers
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy pom.xml trước để cache layer dependencies: chỉ tải lại khi pom.xml thay đổi
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy mã nguồn sau cùng: sửa code chỉ invalidate từ bước này trở đi
COPY src ./src
RUN mvn clean package -DskipTests -B

# Tách jar thành 4 layer theo tần suất thay đổi (dependencies ít đổi nhất -> application đổi nhiều nhất)
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ------------------------------------------------------------------------------
# Stage 2: Runtime - chỉ chứa JRE, không có Maven/JDK/mã nguồn
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# DevSecOps: chạy ứng dụng bằng user thường, không dùng root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy layers theo thứ tự ít thay đổi -> thay đổi nhiều để tận dụng Docker cache
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/application/ ./

USER appuser

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080

# Thăm dò sức khỏe container qua Spring Boot Actuator (wget có sẵn trong Alpine/BusyBox)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
