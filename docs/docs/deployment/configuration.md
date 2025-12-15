# Configuration

The purpose of this section is to learn how to configure OpenAEV to have it tailored for your production and development
needs. It is possible to check all default parameters implemented in the platform in the [
`application.properties` file](https://github.com/OpenAEV-Platform/openaev/blob/master/openaev-api/src/main/resources/application.properties).

Here are the configuration keys, for both containers (environment variables) and manual deployment.

!!! note "Parameters equivalence"

    The equivalent of a config variable in environment variables is the usage of an underscore (`_`) for a level of config.

    For example:
    ```yaml
    spring.servlet.multipart.max-file-size=5GB
    ```

    will become:
    ```bash
    SPRING_SERVLET_MULTIPART_MAX-FILE-SIZE=5GB
    ```

## Platform

### API & Frontend

#### Basic parameters

| Parameter                          | Environment variable               | Default value         | Description                                                                                                              |
|:-----------------------------------|:-----------------------------------|:----------------------|:-------------------------------------------------------------------------------------------------------------------------|
| server.address                     | SERVER_ADDRESS                     | 0.0.0.0               | Listen address of the application                                                                                        |
| server.port                        | SERVER_PORT                        | 8080                  | Listen port of the application                                                                                           |
| openaev.base-url                   | OPENAEV_BASE-URL                   | http://localhost:8080 | Base URL of the application, will be used in some email links                                                            |
| server.servlet.session.timeout     | SERVER_SERVLET_SESSION_TIMEOUT     | 60m                   | Default duration of session (60 minutes)                                                                                 |
| openaev.cookie-secure              | OPENAEV_COOKIE-SECURE              | `false`               | Turn on if the access is done in HTTPS                                                                                   |
| openaev.cookie-duration            | OPENAEV_COOKIE-DURATION            | P1D                   | Cookie duration (default 1 day)                                                                                          |
| openaev.admin.email                | OPENAEV_ADMIN_EMAIL                | admin@openaev.io      | Default login email of the admin user                                                                                    |
| openaev.admin.password             | OPENAEV_ADMIN_PASSWORD             | ChangeMe              | Default password of the admin user                                                                                       |
| openaev.admin.token                | OPENAEV_ADMIN_TOKEN                | ChangeMe              | Default token (must be a valid UUIDv4)                                                                                   |
| openaev.healthcheck.key            | OPENAEV_HEALTHCHECK_KEY            | ChangeMe              | The key to use in the health check endpoint (/api/health)                                                                |
| inject.execution.threshold.minutes | INJECT_EXECUTION_THRESHOLD_MINUTES | 10                    | Inject execution threshold in minutes. If this time is exceeded, the inject will be moved to the MAYBE_PREVENTED status. |
| openaev.starterpack.enabled        | OPENAEV_STARTERPACK_ENABLED        | true                  | StarterPack feature, providing default endpoint, asset group, scenarios and dashboards                                   |

#### Network and security

| Parameter                       | Environment variable            | Default value           | Description                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------|:--------------------------------|:------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| server.ssl.enabled              | SERVER_SSL_ENABLED              | `false`                 | Turn on to enable SSL on the local server                                                                                                                                                                                                                                                                                                                                                                                   |
| server.ssl.key-store-type       | SERVER_SSL_KEY-STORE-TYPE       | PKCS12                  | Type of SSL keystore                                                                                                                                                                                                                                                                                                                                                                                                        |
| server.ssl.key-store            | SERVER_SSL_KEY-STORE            | classpath:localhost.p12 | SSL keystore path                                                                                                                                                                                                                                                                                                                                                                                                           |
| server.ssl.key-store-password   | SERVER_SSL_KEY-STORE-PASSWORD   | admin                   | SSL keystore password                                                                                                                                                                                                                                                                                                                                                                                                       |
| server.ssl.key-alias            | SERVER_SSL_KEY-ALIAS            | localhost               | SSL key alias                                                                                                                                                                                                                                                                                                                                                                                                               |
| openaev.unsecured-certificate   | OPENAEV_UNSECURED-CERTIFICATE   | `false`                 | Turn on to authorize self-signed or unsecure ssl certificate                                                                                                                                                                                                                                                                                                                                                                |
| openaev.with-proxy              | OPENAEV_WITH-PROXY              | `false`                 | Turn on to authorize environment with proxy                                                                                                                                                                                                                                                                                                                                                                                 |
| openaev.extra-trusted-certs-dir | OPENAEV_EXTRA-TRUSTED-CERTS-DIR |                         | If you want to set extra trusted self-signed TLS certificates to communicate with external applications (Crowdstrike, Tanium, SentinelOne,...),<br/>fill this attribute with you local folder containing your public .PEM certs. If you install OpenAEV with Docker,<br/>uncomment the volume and set the attribute in the [docker compose file](https://github.com/OpenAEV-Platform/docker/blob/master/docker-compose.yml) | 

⚠️ **Important**: If you are using the parameter `openaev.extra-trusted-certs-dir`, the file format needed for the
certificates in the folder are public PEM-armoured (*.pem), DER-encoded X509 certs.

#### Logging

| Parameter                                   | Environment variable                        | Default value      | Description                                   |
|:--------------------------------------------|:--------------------------------------------|:-------------------|:----------------------------------------------|
| logging.level.root                          | LOGGING_LEVEL_ROOT                          | fatal              | Root log level                                |
| logging.level.io.openaev                    | LOGGING_LEVEL_IO_OPENAEV                    | warn               | OpenAEV log level                             |
| logging.file.name                           | LOGGING_FILE_NAME                           | ./logs/openaev.log | Log file path (in addition to console output) |
| logging.logback.rollingpolicy.max-file-size | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-FILE-SIZE | 10MB               | Rolling max file size                         |
| logging.logback.rollingpolicy.max-history   | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-HISTORY   | 7                  | Rolling max days                              |

### Dependencies

#### XTM Suite: OpenCTI

| Parameter                           | Environment variable                | Default value | Description                                                                                                                           |
|:------------------------------------|:------------------------------------|:--------------|:--------------------------------------------------------------------------------------------------------------------------------------|
| openaev.xtm.opencti.enable          | OPENAEV_XTM_OPENCTI_ENABLE          | false         | Enable integration with OpenCTI                                                                                                       |
| openaev.xtm.opencti.url             | OPENAEV_XTM_OPENCTI_URL             |               | OpenCTI URL                                                                                                                           |
| openaev.xtm.opencti.api_url         | OPENAEV_XTM_OPENCTI_API_URL         |               | OpenCTI API URL, it will completly override the OpenCTI URL, otherwise the default url will be `openaev.xtm.opencti.url` + '/graphql' |
| openaev.xtm.opencti.token           | OPENAEV_XTM_OPENCTI_TOKEN           |               | OpenCTI token                                                                                                                         |
| openaev.xtm.opencti.disable-display | OPENAEV_XTM_OPENCTI_DISABLE-DISPLAY | `false`       | Disable OpenCTI in the UI                                                                                                             |

#### XTM Suite: XTM Hub

| Parameter                                             | Environment variable             | Default value | Description                                                         |
|:------------------------------------------------------|:------------------------------------------------------|:-------------------------------------|:----------------------------------------------------------------------|
| openaev.xtm.hub.enable                                | OPENAEV_XTM_HUB_ENABLE                                | false                                | Enables integration with XTM Hub                                     |
| openaev.xtm.hub.url                                   | OPENAEV_XTM_HUB_URL              |               | XTM Hub URL                                                         |
| openaev.xtm.hub.override-api-url                      | OPENAEV_XTM_HUB_OVERRIDE_API_URL                      |                                      | When specified, override `openaev.xtm.hub.url` during backend calls |
| openaev.xtm.hub.collector.enable                      | OPENAEV_XTM_HUB_COLLECTOR_ENABLE                      | false                                | Enables the XTM Hub connectivity collector                          |
| openaev.xtm.hub.collector.id                          | OPENAEV_XTM_HUB_COLLECTOR_ID                          | b402f1f5-29ba-4ee3-b366-f0467754cf4e | Identifier of the XTM Hub connectivity collector                    |
| openaev.xtm.hub.collector.connectivity-check-interval | OPENAEV_XTM_HUB_COLLECTOR_CONNECTIVITY_CHECK_INTERVAL | 1 hour in milliseconds               | Interval at which the connectivity with XTM Hub is checked          |

#### PostgreSQL

| Parameter                  | Environment variable       | Default value         | Description                                                                                |
|:---------------------------|:---------------------------|:----------------------|:-------------------------------------------------------------------------------------------|
| spring.datasource.url      | SPRING_DATASOURCE_URL      | jdbc:postgresql://... | URL of the PostgreSQL database (ex jdbc:postgresql://postgresql.mydomain.com:5432/openaev) |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME |                       | Login for the database                                                                     |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD | password              | Password for the database                                                                  |

#### Engine

| Parameter              | Environment variable   | Default value         | Description                                                                                    |
|:-----------------------|:-----------------------|:----------------------|:-----------------------------------------------------------------------------------------------|
| engine.engine-aws-mode | ENGINE_ENGINE_AWS_MODE | no                    | Whether to use AWS SigV4 authentication (yes or no)                                            |
| engine.engine-selector | ENGINE_ENGINE_SELECTOR | elk                   | Engine to use for storage and search (`elk` for ElasticSearch and `opensearch` for OpenSearch) |
| engine.url             | ENGINE_URL             | http://localhost:9200 | URL of the ElasticSearch database                                                              |
| engine.username        | ENGINE_USERNAME        |                       | This parameter is optional. Login for the database                                             |
| engine.password        | ENGINE_PASSWORD        |                       | This parameter is optional. Password for the dat                                               |

If you switch your engine selector, you’ll need to delete the `indexing_status` table in PostgreSQL to trigger a full
reindex.

#### RabbitMQ

| Parameter                             | Environment variable                  | Default value                     | Description                                                                                                                                                                       |
|:--------------------------------------|:--------------------------------------|:----------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| openaev.rabbitmq.prefix               | OPENAEV_RABBITMQ_PREFIX               | openaev                           | Prefix for the queue names                                                                                                                                                        |
| openaev.rabbitmq.hostname             | OPENAEV_RABBITMQ_HOSTNAME             | localhost                         | Hostname of the RabbitMQ server                                                                                                                                                   |
| openaev.rabbitmq.vhost                | OPENAEV_RABBITMQ_VHOST                | /                                 | Vhost of the RabbitMQ server                                                                                                                                                      |
| openaev.rabbitmq.port                 | OPENAEV_RABBITMQ_PORT                 | 5672                              | Port of the RabbitMQ Server                                                                                                                                                       |
| openaev.rabbitmq.management-port      | OPENAEV_RABBITMQ_MANAGEMENT-PORT      | 15672                             | Management port of the RabbitMQ Server                                                                                                                                            |
| openaev.rabbitmq.ssl                  | OPENAEV_RABBITMQ_SSL                  | `false`                           | Use SSL                                                                                                                                                                           |
| openaev.rabbitmq.user                 | OPENAEV_RABBITMQ_USER                 | guest                             | RabbitMQ user                                                                                                                                                                     |
| openaev.rabbitmq.pass                 | OPENAEV_RABBITMQ_PASS                 | guest                             | RabbitMQ password                                                                                                                                                                 |
| openaev.rabbitmq.queue-type           | OPENAEV_RABBITMQ_QUEUE-TYPE           | classic                           | RabbitMQ Queue Type ("classic" or "quorum")                                                                                                                                       |
| openaev.rabbitmq.management-insecure  | OPENAEV_RABBITMQ_MANAGEMENT-INSECURE  | `true`                            | Whether or not the calls to the management plugin of rabbitmq can be insecure                                                                                                     |
| openaev.rabbitmq.trust.store          | OPENAEV_RABBITMQ_TRUST_STORE          | <file:/path/to/client-store.p12\> | Path to the p12 keystore file to use if ssl is activated and insecure management is deactivated. The keystore must contain the client side certificate and key generated for ssl. |
| openaev.rabbitmq.trust-store-password | OPENAEV_RABBITMQ_TRUST-STORE-PASSWORD | <trust-store-password\>           | Password of the keystore                                                                                                                                                          |

#### S3 bucket

| Parameter               | Environment variable    | Default value | Description                                                                                                                                                                                                                                                                                                                                                                                                                   |
|:------------------------|:------------------------|:--------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| minio.endpoint          | MINIO_ENDPOINT          | localhost     | Hostname of the S3 Service. Example if you use AWS Bucket S3: __s3.us-east-1.amazonaws.com__ (if `minio:bucket_region` value is _us-east-1_). This parameter value can be omitted if you use Minio as an S3 Bucket Service.                                                                                                                                                                                                   |
| minio.port              | MINIO_PORT              | 9000          | Port of the S3 Service. For AWS Bucket S3 over HTTPS, this value can be changed (usually __443__).                                                                                                                                                                                                                                                                                                                            |
| minio.secure            | MINIO_SECURE            | `false`       | Indicates whether the S3 Service has TLS enabled. For AWS Bucket S3 over HTTPS, this value could be `true`.                                                                                                                                                                                                                                                                                                                   |
| minio.access-key        | MINIO_ACCESS-KEY        | key           | Access key for the S3 Service.                                                                                                                                                                                                                                                                                                                                                                                                |
| minio.access-secret     | MINIO_ACCESS-SECRET     | secret        | Secret key for the S3 Service.                                                                                                                                                                                                                                                                                                                                                                                                |
| minio.bucket            | MINIO_BUCKET            | openaev       | S3 bucket name. Useful to change if you use AWS.                                                                                                                                                                                                                                                                                                                                                                              |
| minio.bucket-region     | MINIO_BUCKET-REGION     | us-east-1     | Region of the S3 bucket if you are using AWS. This parameter value can be omitted if you use Minio as an S3 Bucket Service.                                                                                                                                                                                                                                                                                                   |
| openaev.s3.use-aws-role | OPENAEV_S3_USE-AWS-ROLE | `false`       | Whether or not we want to get the AWS role using AWS Security Token Service                                                                                                                                                                                                                                                                                                                                                   |
| openaev.s3.sts-endpoint | OPENAEV_S3_STS-ENDPOINT |               | `experimental` This parameter is optional. If it stays empty, it will use either the AWS legacy STS endpoint (https://sts.amazonaws.com) or the regional one using AWS_REGION if the environment variable is set (you can learn more about it [here](https://docs.aws.amazon.com/general/latest/gr/sts.html#sts_region)). Otherwise, if you want to use your own custom implementation of STS endpoints, you can set it here. |

#### Agents (executors)

To be able to use the power of the OpenAEV platform on endpoints, you need at least one **neutral executor** that will
be in charge of executing implants as detached processes. Implants will then execute payloads.

##### OpenAEV Agent

The OpenAEV agent is enabled by default and cannot be disabled. It is available for:

- Windows (`x86_64` / `arm64`)
- Linux (`x86_64` / `arm64`)
- MacOS (`x86_64` / `arm64`)

##### Other executors

To know more about other available executors, please refer to the [executors documentation](ecosystem/executors.md)

#### Mail services

For the associated mailbox, for the moment the platform only relies on IMAP / SMTP protocols, we are actively developing
integrations with APIs such as O365 and Google Apps.

##### SMTP

| Parameter                      | Environment variable           | Default value     | Description                                                                                                            |
|:-------------------------------|:-------------------------------|:------------------|:-----------------------------------------------------------------------------------------------------------------------|
| openaev.listener.smtp.enabled  | OPENAEV_LISTENER_SMTP_ENABLED  | `true`            | SMTP connection monitoring, enabled by default. Allow you to monitor service state from healthchecks and settings page |
| spring.mail.host               | SPRING_MAIL_HOST               | smtp.mail.com     | SMTP Server hostname                                                                                                   |
| spring.mail.port               | SPRING_MAIL_PORT               | 465               | SMTP Server port                                                                                                       |
| spring.mail.username           | SPRING_MAIL_USERNAME           | username@mail.com | SMTP Server username                                                                                                   |
| spring.mail.password           | SPRING_MAIL_PASSWORD           | password          | SMTP Server password                                                                                                   |

| Parameter                                        | Environment variable                             | Default value | Description                   |
|:-------------------------------------------------|:-------------------------------------------------|:--------------|:------------------------------|
| spring.mail.properties.mail.smtp.ssl.enable      | SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE      | `true`        | Turn on SMTP SSL mode         |
| spring.mail.properties.mail.smtp.ssl.trust       | SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST       | *             | Trust unverified certificates |
| spring.mail.properties.mail.smtp.auth            | SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH            | `true`        | Turn on SMTP authentication   |
| spring.mail.properties.mail.smtp.starttls.enable | SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE | `false`       | Turn on SMTP STARTTLS         |

> **Note :** Example with Gmail

| Parameter                                        | Environment variable                             | Value             | Description                  |
|:-------------------------------------------------|:-------------------------------------------------|:------------------|:-----------------------------|
| spring.mail.host                                 | SPRING_MAIL_HOST                                 | smtp.gmail.com    | Gmail SMTP server hostname   |
| spring.mail.port                                 | SPRING_MAIL_PORT                                 | 587               | Gmail SMTP server port (TLS) |
| spring.mail.username                             | SPRING_MAIL_USERNAME                             | username@mail.com | Gmail address                |
| spring.mail.password                             | SPRING_MAIL_PASSWORD                             | app password      | Gmail App-specific password  |
| spring.mail.properties.mail.smtp.auth            | SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH            | true              | Enable SMTP authentication   |
| spring.mail.properties.mail.smtp.starttls.enable | SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE | true              | Enable SMTP STARTTLS         |

⚠️ **Important**: If you are using two-factor authentication on your Gmail account, an app-specific password is
required. You can find a guide [here](https://support.google.com/accounts/answer/185833).

##### IMAP

| Parameter                  | Environment variable       | Default value     | Description                                                                         |
|:---------------------------|:---------------------------|:------------------|:------------------------------------------------------------------------------------|
| openaev.mail.imap.enabled  | OPENAEV_MAIL_IMAP_ENABLED  | false             | Turn on to enable IMAP mail synchronization. Injector email must be well configured |
| openaev.mail.imap.host     | OPENAEV_MAIL_IMAP_HOST     | imap.mail.com     | IMAP Server hostname                                                                |
| openaev.mail.imap.port     | OPENAEV_MAIL_IMAP_PORT     | 993               | IMAP Server port                                                                    |
| openaev.mail.imap.username | OPENAEV_MAIL_IMAP_USERNAME | username@mail.com | IMAP Server username                                                                |
| openaev.mail.imap.password | OPENAEV_MAIL_IMAP_PASSWORD | password          | IMAP Server password                                                                |
| openaev.mail.imap.inbox    | OPENAEV_MAIL_IMAP_INBOX    | INBOX             | IMAP inbox directory to synchronize from                                            |
| openaev.mail.imap.sent     | OPENAEV_MAIL_IMAP_SENT     | Sent              | IMAP sent directory to synchronize from                                             |

| Parameter                         | Environment variable              | Default value | Description                   |
|:----------------------------------|:----------------------------------|:--------------|:------------------------------|
| openaev.mail.imap.ssl.enable      | OPENAEV_MAIL_IMAP_SSL_ENABLE      | true          | Turn on IMAP SSL mode         |
| openaev.mail.imap.ssl.trust       | OPENAEV_MAIL_IMAP_SSL_TRUST       | *             | Trust unverified certificates |
| openaev.mail.imap.auth            | OPENAEV_MAIL_IMAP_AUTH            | true          | Turn on IMAP authentication   |
| openaev.mail.imap.starttls.enable | OPENAEV_MAIL_IMAP_STARTTLS_ENABLE | true          | Turn on IMAP STARTTLS         |

> **Note :** Example with Gmail

| Parameter                    | Environment variable         | Value             | Description                             |
|:-----------------------------|:-----------------------------|:------------------|:----------------------------------------|
| openaev.mail.imap.enabled    | OPENAEV_MAIL_IMAP_ENABLED    | true              | Enable IMAP for Gmail                   |
| openaev.mail.imap.host       | OPENAEV_MAIL_IMAP_HOST       | imap.gmail.com    | Gmail IMAP server hostname              |
| openaev.mail.imap.port       | OPENAEV_MAIL_IMAP_PORT       | 993               | Gmail IMAP port (SSL)                   |
| openaev.mail.imap.username   | OPENAEV_MAIL_IMAP_USERNAME   | username@mail.com | Gmail address                           |
| openaev.mail.imap.password   | OPENAEV_MAIL_IMAP_PASSWORD   | app password      | Gmail App-specific password             |
| openaev.mail.imap.ssl.enable | OPENAEV_MAIL_IMAP_SSL_ENABLE | true              | Enable IMAP SSL                         |
| openaev.mail.imap.ssl.trust  | OPENAEV_MAIL_IMAP_SSL_TRUST  | *                 | Trust unverified certificates           |
| openaev.mail.imap.auth       | OPENAEV_MAIL_IMAP_AUTH       | true              | Enable IMAP authentication              |
| openaev.mail.imap.sent       | OPENAEV_MAIL_IMAP_SENT       | [Gmail]/Sent Mail | IMAP sent directory to synchronize from |

⚠️ **Important**: If you are using two-factor authentication on your Gmail account, an app-specific password is
required. You can find a guide [here](https://support.google.com/accounts/answer/185833).

#### AI Service

!!! note "AI deployment and cloud services"

    There are several possibilities for [Enterprise Edition](../administration/enterprise.md) customers to use OpenAEV AI endpoints:

     - Use the Filigran AI Service leveraging our custom AI model using the token given by the support team.
     - Use OpenAI or MistralAI cloud endpoints using your own tokens.
     - Deploy or use local AI endpoints (Filigran can provide you with the custom model).

| Parameter       | Environment variable | Default value | Description                                               |
|:----------------|:---------------------|:--------------|:----------------------------------------------------------|
| ai.enabled      | AI_ENABLED           | true          | Enable AI capabilities                                    |
| ai.type         | AI_TYPE              | mistralai     | AI type (`mistralai` or `openai`)                         |
| ai.endpoint     | AI_ENDPOINT          |               | Endpoint URL (empty means default cloud service)          |
| ai.token        | AI_TOKEN             |               | Token for endpoint credentials                            |
| ai.model        | AI_MODEL             |               | Model to be used for text generation (depending on type)  |
| ai.model_images | AI_MODEL_IMAGES      |               | Model to be used for image generation (depending on type) |
