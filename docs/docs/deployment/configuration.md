# Configuration

The purpose of this section is to learn how to configure OpenBAS to have it tailored for your production and development needs. It is possible to check all default parameters implemented in the platform in the [`application.properties` file](https://github.com/OpenBAS-Platform/openbas/blob/master/openbas-api/src/main/resources/application.properties).

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

| Parameter                      | Environment variable           | Default value         | Description                                                   |
|:-------------------------------|:-------------------------------|:----------------------|:--------------------------------------------------------------|
| server.address                 | SERVER_ADDRESS                 | 0.0.0.0               | Listen address of the application                             |
| server.port                    | SERVER_PORT                    | 8080                  | Listen port of the application                                |
| openbas.base-url               | OPENBAS_BASE-URL               | http://localhost:8080 | Base URL of the application, will be used in some email links |
| server.servlet.session.timeout | SERVER_SERVLET_SESSION_TIMEOUT | 60m                   | Default duration of session (60 minutes)                      |
| openbas.cookie-secure          | OPENBAS_COOKIE-SECURE          | `false`               | Turn on if the access is done in HTTPS                        |
| openbas.cookie-duration        | OPENBAS_COOKIE-DURATION        | P1D                   | Cookie duration (default 1 day)                               |
| openbas.admin.email            | OPENBAS_ADMIN_EMAIL            | admin@openbas.io      | Default login email of the admin user                         |
| openbas.admin.password         | OPENBAS_ADMIN_PASSWORD         | ChangeMe              | Default password of the admin user                            |
| openbas.admin.token            | OPENBAS_ADMIN_TOKEN            | ChangeMe              | Default token (must be a valid UUIDv4)                        |

#### Network and security

| Parameter                     | Environment variable          | Default value           | Description                               |
|:------------------------------|:------------------------------|:------------------------|:------------------------------------------|
| server.ssl.enabled            | SERVER_SSL_ENABLED            | `false`                 | Turn on to enable SSL on the local server |
| server.ssl.key-store-type     | SERVER_SSL_KEY-STORE-TYPE     | PKCS12                  | Type of SSL keystore                      |
| server.ssl.key-store          | SERVER_SSL_KEY-STORE          | classpath:localhost.p12 | SSL keystore path                         |
| server.ssl.key-store-password | SERVER_SSL_KEY-STORE-PASSWORD | admin                   | SSL keystore password                     |
| server.ssl.key-alias          | SERVER_SSL_KEY-ALIAS          | localhost               | SSL key alias                             |

#### Logging

| Parameter                                       | Environment variable                            | Default value      | Description                                   |
|:------------------------------------------------|:------------------------------------------------|:-------------------|:----------------------------------------------|
| logging.level.root                              | LOGGING_LEVEL_ROOT                              | fatal              | Root log level                                |
| logging.level.io.openbas                        | LOGGING_LEVEL_IO_OPENBAS                        | warn               | OpenBAS log level                             |
| logging.file.name                               | LOGGING_FILE_NAME                               | ./logs/openbas.log | Log file path (in addition to console output) |
| logging.logback.rollingpolicy.max-file-size     | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-FILE-SIZE     | 10MB               | Rolling max file size                         |
| logging.logback.rollingpolicy.max-history       | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-HISTORY       | 7                  | Rolling max days                              |


### Dependencies

#### XTM Suite

| Parameter                           | Environment variable                | Default value | Description                     |
|:------------------------------------|:------------------------------------|:--------------|:--------------------------------|
| openbas.xtm.opencti.enable          | OPENBAS_XTM_OPENCTI_ENABLE          | false         | Enable integration with OpenCTI |
| openbas.xtm.opencti.url             | OPENBAS_XTM_OPENCTI_URL             |               | OpenCTI URL                     |
| openbas.xtm.opencti.token           | OPENBAS_XTM_OPENCTI_TOKEN           |               | OpenCTI token                   |
| openbas.xtm.opencti.disable-display | OPENBAS_XTM_OPENCTI_DISABLE-DISPLAY | `false`       | Disable OpenCTI in the UI       |

#### PostgreSQL

| Parameter                  | Environment variable       | Default value         | Description                                                                     |
|:---------------------------|:---------------------------|:----------------------|:--------------------------------------------------------------------------------|
| spring.datasource.url      | SPRING_DATASOURCE_URL      | jdbc:postgresql://... | URL of the database (ex jdbc:postgresql://postgresql.mydomain.com:5432/openbas) |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME |                       | Login for the database                                                          |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD | password              | Password for the database                                                       |

#### RabbitMQ

| Parameter                   | Environment variable        | Default value | Description                                 |
|:----------------------------|:----------------------------|:--------------|:--------------------------------------------|
| openbas.rabbitmq.prefix     | OPENBAS_RABBITMQ_PREFIX     | openbas       | Prefix for the queue names                  |
| openbas.rabbitmq.hostname   | OPENBAS_RABBITMQ_HOSTNAME   | localhost     | Hostname of the RabbitMQ server             |
| openbas.rabbitmq.vhost      | OPENBAS_RABBITMQ_VHOST      | /             | Vhost of the RabbitMQ server                |
| openbas.rabbitmq.port       | OPENBAS_RABBITMQ_PORT       | 5672          | Port of the RabbitMQ Server                 |
| openbas.rabbitmq.ssl        | OPENBAS_RABBITMQ_SSL        | `false`       | Use SSL                                     |
| openbas.rabbitmq.user       | OPENBAS_RABBITMQ_USER       | guest         | RabbitMQ user                               |
| openbas.rabbitmq.pass       | OPENBAS_RABBITMQ_PASS       | guest         | RabbitMQ password                           |
| openbas.rabbitmq.queue-type | OPENBAS_RABBITMQ_QUEUE-TYPE | classic       | RabbitMQ Queue Type ("classic" or "quorum") |

#### S3 bucket

| Parameter           | Environment variable | Default value | Description                                                                                                                                                                                                                 |
|:--------------------|:---------------------|:--------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| minio.endpoint      | MINIO_ENDPOINT       | localhost     | Hostname of the S3 Service. Example if you use AWS Bucket S3: __s3.us-east-1.amazonaws.com__ (if `minio:bucket_region` value is _us-east-1_). This parameter value can be omitted if you use Minio as an S3 Bucket Service. |
| minio.port          | MINIO_PORT           | 9000          | Port of the S3 Service. For AWS Bucket S3 over HTTPS, this value can be changed (usually __443__).                                                                                                                          |
| minio.secure        | MINIO_SECURE         | `false`       | Indicates whether the S3 Service has TLS enabled. For AWS Bucket S3 over HTTPS, this value could be `true`.                                                                                                                 |
| minio.access-key    | MINIO_ACCESS-KEY     | key           | Access key for the S3 Service.                                                                                                                                                                                              |
| minio.access-secret | MINIO_ACCESS-SECRET  | secret        | Secret key for the S3 Service.                                                                                                                                                                                              |
| minio.bucket        | MINIO_BUCKET         | openbas       | S3 bucket name. Useful to change if you use AWS.                                                                                                                                                                            |
| minio.bucket-region | MINIO_BUCKET-REGION  | us-east-1     | Region of the S3 bucket if you are using AWS. This parameter value can be omitted if you use Minio as an S3 Bucket Service.                                                                                                 |

#### Executors (neutral agents)

To be able to use the power of the OpenBAS platform on endpoints, you need at least one executor that will be in charge of spawing temporary processes (endpoint injectors) which will execute payloads.

!!! note "Supported executors"

    We currently support only Caldera and Tanium but are actively working on our own agent as well as supporting more third-party.

##### Caldera

| Parameter                   | Environment variable        | Default value | Description                           |
|:----------------------------|:----------------------------|:--------------|:--------------------------------------|
| executor.caldera.enable     | EXECUTOR_CALDERA_ENABLE     | `true`        | Enable the Caldera executor           |
| executor.caldera.url        | EXECUTOR_CALDERA_URL        |               | Caldera URL                           |
| executor.caldera.public-url | EXECUTOR_CALDERA_PUBLIC-URL |               | Caldera URL accessible from endpoints |
| executor.caldera.api-key    | EXECUTOR_CALDERA_API-KEY    |               | Caldera API key                       |

##### Tanium

!!! note "Tanium Packages"

    To use the Tanium executor, please install the [OpenBAS packages](https://github.com/OpenBAS-Platform/openbas/blob/master/openbas-framework/src/main/java/io/openbas/executors/tanium/openbas-tanium-packages.json) on the Tanium platform and fill their IDs in the configuration below.

| Parameter                          | Environment variable               | Default value | Description                                     |
|:-----------------------------------|:-----------------------------------|:--------------|:------------------------------------------------|
| executor.tanium.enable             | EXECUTOR_TANIUM_ENABLE             | `false`       | Enable the Tanium executor                      |
| executor.tanium.url                | EXECUTOR_TANIUM_URL                |               | Tanium URL                                      |
| executor.tanium.api-key            | EXECUTOR_TANIUM_API-KEY            |               | Tanium API key                                  |
| executor.tanium.computer-group-id  | EXECUTOR_TANIUM_COMPUTER_GROUP_ID  |               | Tanium Computer Group to be used in simulations |
| executor.tanium.windows-package-id | EXECUTOR_TANIUM_WINDOWS_PACKAGE_ID |               | ID of the OpenBAS Tanium Windows package        |
| executor.tanium.unix-package-id    | EXECUTOR_TANIUM_UNIX_PACKAGE_ID    |               | ID of the OpenBAS Tanium Unix package           |

#### Mail services

For the associated mailbox, for the moment the platform only relies on IMAP / SMTP protocols, we are actively developing integrations with APIs such as O365 and Google Apps.

##### SMTP

| Parameter             | Environment variable | Default value     | Description          |
|:----------------------|:---------------------|:------------------|:---------------------|
| spring.mail.host      | SPRING_MAIL_HOST     | smtp.mail.com     | SMTP Server hostname |
| spring.mail.port      | SPRING_MAIL_PORT     | 465               | SMTP Server port     |
| spring.mail.username  | SPRING_MAIL_USERNAME | username@mail.com | SMTP Server username |
| spring.mail.password  | SPRING_MAIL_PASSWORD | password          | SMTP Server password |

| Parameter                                        | Environment variable                             | Default value | Description                   |
|:-------------------------------------------------|:-------------------------------------------------|:--------------|:------------------------------|
| spring.mail.properties.mail.smtp.ssl.enable      | SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE      | `true`        | Turn on SMTP SSL mode         |
| spring.mail.properties.mail.smtp.ssl.trust       | SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST       | *             | Trust unverified certificates |
| spring.mail.properties.mail.smtp.auth            | SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH            | `true`        | Turn on SMTP authentication   |
| spring.mail.properties.mail.smtp.starttls.enable | SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE | `false`       | Turn on SMTP STARTTLS         |

##### IMAP

| Parameter                 | Environment variable      | Default value     | Description                                                                         |
|:--------------------------|:--------------------------|:------------------|:------------------------------------------------------------------------------------|
| openbas.mail.imap.enabled  | OPENBAS_MAIL_IMAP_ENABLED  | false             | Turn on to enable IMAP mail synchronization. Injector email must be well configured |
| openbas.mail.imap.host     | OPENBAS_MAIL_IMAP_HOST     | imap.mail.com     | IMAP Server hostname                                                                |
| openbas.mail.imap.port     | OPENBAS_MAIL_IMAP_PORT     | 993               | IMAP Server port                                                                    |
| openbas.mail.imap.username | OPENBAS_MAIL_IMAP_USERNAME | username@mail.com | IMAP Server username                                                                |
| openbas.mail.imap.password | OPENBAS_MAIL_IMAP_PASSWORD | password          | IMAP Server password                                                                |
| openbas.mail.imap.inbox    | OPENBAS_MAIL_IMAP_INBOX    | INBOX             | IMAP inbox directory to synchronize from                                            |
| openbas.mail.imap.sent     | OPENBAS_MAIL_IMAP_SENT     | Sent              | IMAP sent directory to synchronize from                                             |

| Parameter                        | Environment variable             | Default value | Description                   |
|:---------------------------------|:---------------------------------|:--------------|:------------------------------|
| openbas.mail.imap.ssl.enable      | OPENBAS_MAIL_IMAP_SSL_ENABLE      | true          | Turn on IMAP SSL mode         |
| openbas.mail.imap.ssl.trust       | OPENBAS_MAIL_IMAP_SSL_TRUST       | *             | Trust unverified certificates |
| openbas.mail.imap.auth            | OPENBAS_MAIL_IMAP_AUTH            | true          | Turn on IMAP authentication   |
| openbas.mail.imap.starttls.enable | OPENBAS_MAIL_IMAP_STARTTLS_ENABLE | true          | Turn on IMAP STARTTLS         |

#### AI Service

!!! note "AI deployment and cloud services"

    There are several possibilities for [Enterprise Edition](../administration/enterprise.md) customers to use OpenBAS AI endpoints:

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