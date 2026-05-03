FROM eclipse-temurin:25-jdk AS build

ARG PROJECT_ID
ARG MODULE_DIR

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && curl -fsSL https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt -o /usr/local/bin/sbt \
  && chmod 0755 /usr/local/bin/sbt \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY project ./project
COPY build.sbt ./
COPY modules ./modules

RUN sbt "${PROJECT_ID}/assembly" \
  && cp modules/${MODULE_DIR}/target/scala-3.3.7/*-assembly-*.jar /tmp/app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar

ENTRYPOINT ["java", "--add-modules=jdk.httpserver", "-jar", "/app/app.jar"]
