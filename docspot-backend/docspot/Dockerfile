## ---- build stage ----
#FROM maven:3.9-eclipse-temurin-17 AS build
#WORKDIR /build
#COPY pom.xml .
#RUN mvn -B -q dependency:go-offline
#COPY src ./src
#RUN mvn -B -q clean package -DskipTests
#
## ---- run stage ----
#FROM eclipse-temurin:17-jre
#WORKDIR /app
#RUN useradd -r -u 1001 appuser && mkdir -p /app/uploads/docs && chown -R appuser /app
#COPY --from=build /build/target/*.jar app.jar
#USER appuser
#
## Tuned for Render free tier (512 MB RAM, ~0.1 CPU).
## Override from the Render dashboard by setting a JAVA_OPTS environment variable.
##  - MaxRAMPercentage=55   heap ~280 MB, leaving room for Metaspace, thread stacks, code cache, OS
##  - UseSerialGC           lowest memory/CPU overhead of the collectors
##  - TieredStopAtLevel=1   C1 JIT only: less CPU and memory spent compiling
##  - ExitOnOutOfMemoryError  crash fast so Render restarts it instead of hanging
#ENV JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
#
#EXPOSE 8080
#ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m -XX:+UseSerialGC"
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser && mkdir -p /app/uploads/docs && chown -R appuser /app
COPY --from=build /build/target/*.jar app.jar
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]