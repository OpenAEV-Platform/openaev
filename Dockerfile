# Set by BuildKit to the target platform's architecture (amd64, arm64); selects the tini stage.
ARG TARGETARCH

# tini comes from its release as a static binary, checksum-pinned, so the app stage takes it
# without apt. BuildKit builds only the stage of the target architecture.
FROM scratch AS tini-amd64
ADD --checksum=sha256:c5b0666b4cb676901f90dfcb37106783c5fe2077b04590973b885950611b30ee --chmod=755 \
    https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-amd64 /tini

FROM scratch AS tini-arm64
ADD --checksum=sha256:eae1d3aa50c48fb23b8cbdf4e369d0910dfc538566bfd09df89a774aa84a48b9 --chmod=755 \
    https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-arm64 /tini

FROM tini-${TARGETARCH} AS tini

FROM node:24.22.2-alpine3.24 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/patches ./patches
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM maven:3.10.0-eclipse-temurin-21-noble AS api-builder

WORKDIR /opt/openaev-build/openaev
COPY openaev-build-services ./openaev-build-services
COPY openaev-maven-plugin ./openaev-maven-plugin
COPY openaev-model ./openaev-model
COPY openaev-es8-client ./openaev-es8-client
COPY openaev-es9-client ./openaev-es9-client
COPY openaev-opensearch-client ./openaev-opensearch-client
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY openaev-maven-plugin ./openaev-maven-plugin
COPY openaev-ocsf ./openaev-ocsf
COPY pom.xml ./pom.xml
COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build ./openaev-front/builder/prod/build
RUN mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.13_9-jre-noble AS app

# Fixed world-readable browser path so any runtime UID finds the Chromium bundle (reporting)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# apt retries transient mirror failures ("Temporary failure resolving", exit 100) instead of
# failing the build on the first one; every apt call below, Playwright's included, reads it.
RUN printf 'Acquire::Retries "5";\nAcquire::http::Timeout "30";\nAcquire::https::Timeout "30";\n' \
    > /etc/apt/apt.conf.d/80-retries

# Upgrade the base image's packages: it lags behind Ubuntu updates between two rebuilds.
# A mirror outage that outlasts apt's own retries gets two more attempts, 60 s apart.
RUN set -eu; \
    for attempt in 1 2 3; do \
      DEBIAN_FRONTEND=noninteractive apt-get update -q \
        && DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y \
        && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "apt-get failed (attempt $attempt of 3)"; sleep 60; \
    done; \
    rm -rf /var/lib/apt/lists/*
COPY --from=tini /tini /usr/bin/tini
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./
# Install Chromium and its system libraries for server-side report rendering. The boot jar uses
# the ZIP layout, so PropertiesLauncher can run the embedded Playwright CLI (Spring Boot 3.x
# loader). Dev machines need nothing: Playwright auto-downloads the browser on first use.
# The runtime image lacks Chromium's libraries, so apt is needed here: a mirror outage that
# outlasts apt's own retries gets two more attempts, 60 s apart, before the build fails.
RUN set -eu; \
    for attempt in 1 2 3; do \
      DEBIAN_FRONTEND=noninteractive java -Dloader.main=com.microsoft.playwright.CLI -jar openaev-api.jar install --with-deps chromium && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "playwright install --with-deps failed (attempt $attempt of 3)"; sleep 60; \
    done; \
    rm -rf /var/lib/apt/lists/*; \
    chmod -R a+rX /ms-playwright
RUN set -eux; \
    sed -i -E 's|^(mozilla/emSign_.*\.crt)$|!\1|' /etc/ca-certificates.conf; \
    update-ca-certificates --fresh; \
    keytool -list -v -cacerts -storepass changeit > /tmp/cacerts.txt; \
    sed -n 's/^Alias name: //p' /tmp/cacerts.txt | grep -i emsign > /tmp/emsign.txt || true; \
    while IFS= read -r alias; do keytool -delete -noprompt -cacerts -storepass changeit -alias "$alias"; done < /tmp/emsign.txt; \
    keytool -list -v -cacerts -storepass changeit > /tmp/cacerts.txt; \
    grep -q '^Alias name: ' /tmp/cacerts.txt; \
    if ls /etc/ssl/certs | grep -i emsign || grep -iE 'emsign|emudhra' /tmp/cacerts.txt; then exit 1; fi; \
    rm /tmp/cacerts.txt /tmp/emsign.txt

# Set by CI to the built commit, exposed in the platform settings
ARG OPENAEV_COMMIT
ENV OPENAEV_COMMIT=${OPENAEV_COMMIT}

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
