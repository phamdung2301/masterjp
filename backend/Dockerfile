# Multi-stage build cho backend Master JP (Spring Boot 4.1.1, Java 17).
# Render chay Dockerfile nay voi build context = thu muc backend/.
#
# Luu y:
# - Audio MP3 (backend/src/main/resources/static/audio/, ~31MB) duoc dong goi
#   TRONG jar nen container co san file phat am, khong can sinh lai.
# - Moi bien cau hinh nhay cam (MONGO URI, JWT_SECRET, Gmail, ADMIN_*)
#   truyen qua Environment Variables cua Render — KHONG hard-code o day.

# ---- Stage 1: build jar bang Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Tai dependencies truoc de tan dung Docker layer cache
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Stage 2: chay bang JRE gon nhe ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Render cap cong qua bien PORT (thuong la 10000) — app PHAI nghe dung cong nay.
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
