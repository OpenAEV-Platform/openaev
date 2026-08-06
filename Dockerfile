FROM node:22.16.0-alpine3.20 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/patches ./patches
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM maven:3.9.16-eclipse-temurin-21 AS api-builder

WORKDIR /opt/openaev-build/openaev
COPY openaev-annotation-processor ./openaev-annotation-processor
COPY openaev-maven-plugin ./openaev-maven-plugin
COPY openaev-model ./openaev-model
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY openaev-maven-plugin ./openaev-maven-plugin
COPY pom.xml ./pom.xml
COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build ./openaev-front/builder/prod/build
RUN mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.11_10-jre AS app

# Fixed world-readable browser path so any runtime UID finds the Chromium bundle (reporting)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN DEBIAN_FRONTEND=noninteractive apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./
# Install Chromium and its system libraries for server-side report rendering. The boot jar uses
# the ZIP layout, so PropertiesLauncher can run the embedded Playwright CLI (Spring Boot 3.x
# loader). Dev machines need nothing: Playwright auto-downloads the browser on first use.
RUN DEBIAN_FRONTEND=noninteractive java -Dloader.main=com.microsoft.playwright.CLI -jar openaev-api.jar install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/* \
    && chmod -R a+rX /ms-playwright

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
