# Default empty Maven cache (overridden via --build-context m2-cache=...)
FROM scratch AS m2-cache
# Default empty Yarn cache (overridden via --build-context yarn-cache=...)
FROM scratch AS yarn-cache

FROM node:22.16.0-alpine3.20 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/patches ./patches
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN --mount=type=bind,from=yarn-cache,target=/tmp/yarn-seed \
    CACHE_DIR=$(yarn config get cacheFolder 2>/dev/null || echo /root/.yarn/berry/cache) && \
    mkdir -p "$CACHE_DIR" && \
    cp -rn /tmp/yarn-seed/. "$CACHE_DIR/" 2>/dev/null || true
RUN yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM maven:3.9.16-eclipse-temurin-21 AS api-builder

WORKDIR /opt/openaev-build/openaev
# Pre-seed Maven cache (injected via build-context, empty by default)
RUN --mount=type=bind,from=m2-cache,target=/tmp/m2-seed \
    cp -rn /tmp/m2-seed/. /root/.m2/repository/ 2>/dev/null || mkdir -p /root/.m2/repository
# Copy only POMs first to cache dependency resolution
COPY pom.xml ./pom.xml
COPY openaev-annotation-processor/pom.xml ./openaev-annotation-processor/pom.xml
COPY openaev-model/pom.xml ./openaev-model/pom.xml
COPY openaev-framework/pom.xml ./openaev-framework/pom.xml
COPY openaev-api/pom.xml ./openaev-api/pom.xml
RUN mvn dependency:go-offline -Pdev -B -q || true
# Now copy source and build
COPY openaev-annotation-processor ./openaev-annotation-processor
COPY openaev-model ./openaev-model
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build ./openaev-front/builder/prod/build
RUN mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.11_10-jre AS app

RUN DEBIAN_FRONTEND=noninteractive apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
