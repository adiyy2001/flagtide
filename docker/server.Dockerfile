FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/server/pom.xml apps/server/pom.xml
COPY apps/server/config apps/server/config
COPY apps/server/domain apps/server/domain
COPY apps/server/application apps/server/application
COPY apps/server/adapter-out-memory apps/server/adapter-out-memory
COPY apps/server/adapter-out-postgres apps/server/adapter-out-postgres
COPY apps/server/adapter-in-rest apps/server/adapter-in-rest
COPY apps/server/adapter-in-websocket apps/server/adapter-in-websocket
COPY apps/server/bootstrap apps/server/bootstrap
COPY apps/server/architecture/pom.xml apps/server/architecture/pom.xml
WORKDIR /build/apps/server
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl bootstrap -am -DskipTests -Djacoco.skip=true package

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system flagwire && useradd --system --gid flagwire --no-create-home flagwire
WORKDIR /app
COPY --from=build --chown=flagwire:flagwire /build/apps/server/bootstrap/target/quarkus-app/ /app/
USER flagwire
ENV QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080 \
    JAVA_TOOL_OPTIONS="-Xmx512m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /q/health/ready HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n 1 <&3 | grep -q " 200 "'
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
