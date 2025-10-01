FROM node:22.16.0-alpine3.20 AS front-builder

WORKDIR /opt/openaev-build/openaev-front
COPY openaev-front/packages ./packages
COPY openaev-front/.yarn ./.yarn
COPY openaev-front/package.json openaev-front/yarn.lock openaev-front/.yarnrc.yml ./
RUN yarn install
COPY openaev-front /opt/openaev-build/openaev-front
RUN yarn build

FROM maven:3.9.11-eclipse-temurin-21 AS api-builder

WORKDIR /opt/openaev-build/openaev
COPY openaev-model ./openaev-model
COPY openaev-framework ./openaev-framework
COPY openaev-api ./openaev-api
COPY pom.xml ./pom.xml
COPY --from=front-builder /opt/openaev-build/openaev-front/builder/prod/build ./openaev-front/builder/prod/build
RUN mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.7_6-jre AS app

RUN DEBIAN_FRONTEND=noninteractive apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/openaev-build/openaev/openaev-api/target/openaev-api.jar ./

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "openaev-api.jar"]
