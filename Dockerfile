FROM node:24.21.0-alpine3.24 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/patches ./patches
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM scratch AS front-assets

COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build /build

FROM maven:3.9.16-eclipse-temurin-21-noble AS api-dependencies

WORKDIR /opt/openaev-build/openaev
# POMs only, so the dependency layer is reused until one of them changes.
# Keep this module list in sync with the api-builder stage below.
COPY pom.xml ./pom.xml
COPY openaev-build-services/pom.xml ./openaev-build-services/pom.xml
COPY openaev-model/pom.xml ./openaev-model/pom.xml
COPY openaev-framework/pom.xml ./openaev-framework/pom.xml
COPY openaev-api/pom.xml ./openaev-api/pom.xml
COPY openaev-maven-plugin/pom.xml ./openaev-maven-plugin/pom.xml
COPY openaev-es8-client/pom.xml ./openaev-es8-client/pom.xml
COPY openaev-es9-client/pom.xml ./openaev-es9-client/pom.xml
COPY openaev-opensearch-client/pom.xml ./openaev-opensearch-client/pom.xml
COPY openaev-ocsf/pom.xml ./openaev-ocsf/pom.xml
RUN mvn -B -ntp -Pdev -Dmaven.test.skip=true -DexcludeReactor=true dependency:go-offline

FROM api-dependencies AS api-builder

COPY openaev-build-services ./openaev-build-services
COPY openaev-maven-plugin ./openaev-maven-plugin
COPY openaev-model ./openaev-model
COPY openaev-es8-client ./openaev-es8-client
COPY openaev-es9-client ./openaev-es9-client
COPY openaev-opensearch-client ./openaev-opensearch-client
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY openaev-ocsf ./openaev-ocsf
COPY --from=front-assets /build ./openaev-front/builder/prod/build
RUN mvn -B -ntp install -Dmaven.test.skip=true -Pdev

FROM eclipse-temurin:21.0.12_8-jre-noble AS app

# Fixed world-readable browser path so any runtime UID finds the Chromium bundle (reporting)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Upgrade the base image's packages: it lags behind Ubuntu updates between two rebuilds.
RUN DEBIAN_FRONTEND=noninteractive apt-get update -q \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y \
    && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini \
    && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./
# Install Chromium and its system libraries for server-side report rendering. The boot jar uses
# the ZIP layout, so PropertiesLauncher can run the embedded Playwright CLI (Spring Boot 3.x
# loader). Dev machines need nothing: Playwright auto-downloads the browser on first use.
# Keep this after the jar COPY: the layer is then rebuilt on every build instead of being
# restored from the layer cache, so its system libraries pick up Ubuntu security updates.
RUN DEBIAN_FRONTEND=noninteractive java -Dloader.main=com.microsoft.playwright.CLI -jar openaev-api.jar install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/* \
    && chmod -R a+rX /ms-playwright

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
