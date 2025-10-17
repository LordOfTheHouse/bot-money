# ---------- build ----------
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package spring-boot:repackage

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /opt/app
ENV JAVA_OPTS=""
COPY --from=builder /app/target/*.jar /opt/app/app.jar
EXPOSE 8080
ENTRYPOINT ["/bin/sh","-c","java $JAVA_OPTS -jar /opt/app/app.jar"]