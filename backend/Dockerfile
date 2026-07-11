# syntax=docker/dockerfile:1
# 그늘 백엔드 이미지 (AWS ECS Fargate 등 컨테이너 배포용). 멀티스테이지 = 빌드/런타임 분리로 이미지 경량화.

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system geuneul && useradd --system --gid geuneul --home-dir /app --shell /usr/sbin/nologin geuneul
# bootJar만 복사. `-plain.jar`(라이브러리 jar)는 `*-SNAPSHOT.jar` 글롭에 미매치되어 제외됨.
COPY --from=build --chown=geuneul:geuneul /app/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
USER geuneul
# 컨테이너 메모리에 맞춘 힙 자동조정 + $PORT는 application.yml의 server.port가 처리.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
