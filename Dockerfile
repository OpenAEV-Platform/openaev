FROM node:22.16.0-alpine3.20 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/patches ./patches
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN --mount=type=cache,target=/root/.yarn/berry/cache \
    yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM maven:3.9.16-eclipse-temurin-21 AS api-builder

WORKDIR /opt/openaev-build/openaev
# Copy only POMs first to cache dependency resolution
COPY pom.xml ./pom.xml
COPY openaev-annotation-processor/pom.xml ./openaev-annotation-processor/pom.xml
COPY openaev-model/pom.xml ./openaev-model/pom.xml
COPY openaev-framework/pom.xml ./openaev-framework/pom.xml
COPY openaev-api/pom.xml ./openaev-api/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -Pdev -B -q || true
# Now copy source and build
COPY openaev-annotation-processor ./openaev-annotation-processor
COPY openaev-model ./openaev-model
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build ./openaev-front/builder/prod/build
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.11_10-jre AS app

RUN DEBIAN_FRONTEND=noninteractive apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
