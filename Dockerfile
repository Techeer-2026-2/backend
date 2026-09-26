# ---------- build stage ----------
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

# 의존성 레이어 캐싱: 빌드 스크립트만 먼저 복사
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 루트로 실행하지 않음
RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown spring:spring /app/app.jar
USER spring

EXPOSE 8080

# actuator 헬스체크 (docker compose / EC2 배포 검증용)
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
