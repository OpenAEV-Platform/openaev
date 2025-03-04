# Configuration

The purpose of this section is to learn how to configure OpenBAS to have it tailored for your production and development
needs. It is possible to check all default parameters implemented in the platform in the [
`application.properties` file](https://github.com/OpenBAS-Platform/openbas/blob/master/openbas-api/src/main/resources/application.properties).

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

| Parameter                     | Environment variable          | Default value           | Description                                                  |
|:------------------------------|:------------------------------|:------------------------|:-------------------------------------------------------------|
| server.ssl.enabled            | SERVER_SSL_ENABLED            | `false`                 | Turn on to enable SSL on the local server                    |
| server.ssl.key-store-type     | SERVER_SSL_KEY-STORE-TYPE     | PKCS12                  | Type of SSL keystore                                         |
| server.ssl.key-store          | SERVER_SSL_KEY-STORE          | classpath:localhost.p12 | SSL keystore path                                            |
| server.ssl.key-store-password | SERVER_SSL_KEY-STORE-PASSWORD | admin                   | SSL keystore password                                        |
| server.ssl.key-alias          | SERVER_SSL_KEY-ALIAS          | localhost               | SSL key alias                                                |
| openbas.unsecured-certificate | OPENBAS_UNSECURED-CERTIFICATE | `false`                 | Turn on to authorize self-signed or unsecure ssl certificate |
| openbas.with-proxy            | OPENBAS_WITH-PROXY            | `false`                 | Turn on to authorize environment with proxy                  |

#### Logging

| Parameter                                   | Environment variable                        | Default value      | Description                                   |
|:--------------------------------------------|:--------------------------------------------|:-------------------|:----------------------------------------------|
| logging.level.root                          | LOGGING_LEVEL_ROOT                          | fatal              | Root log level                                |
| logging.level.io.openbas                    | LOGGING_LEVEL_IO_OPENBAS                    | warn               | OpenBAS log level                             |
| logging.file.name                           | LOGGING_FILE_NAME                           | ./logs/openbas.log | Log file path (in addition to console output) |
| logging.logback.rollingpolicy.max-file-size | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-FILE-SIZE | 10MB               | Rolling max file size                         |
| logging.logback.rollingpolicy.max-history   | LOGGING_LOGBACK_ROLLINGPOLICY_MAX-HISTORY   | 7                  | Rolling max days                              |

### Dependencies

#### XTM Suite

| Parameter                           | Environment variable                | Default value | Description                                                                                         |
|:------------------------------------|:------------------------------------|:--------------|:----------------------------------------------------------------------------------------------------|
| openbas.xtm.opencti.enable          | OPENBAS_XTM_OPENCTI_ENABLE          | false         | Enable integration with OpenCTI                                                                     |
| openbas.xtm.opencti.url             | OPENBAS_XTM_OPENCTI_URL             |               | OpenCTI URL                                                                                         |
| openbas.xtm.opencti.api_url         | OPENBAS_XTM_OPENCTI_API_URL         |               | OpenCTI API URL, it will completly override the OpenCTI URL, otherwise the default url will be `openbas.xtm.opencti.url` + '/graphql'|
| openbas.xtm.opencti.token           | OPENBAS_XTM_OPENCTI_TOKEN           |               | OpenCTI token                                                                                       |
| openbas.xtm.opencti.disable-display | OPENBAS_XTM_OPENCTI_DISABLE-DISPLAY | `false`       | Disable OpenCTI in the UI                                                                           |

#### PostgreSQL

| Parameter                  | Environment variable       | Default value         | Description                                                                     |
|:---------------------------|:---------------------------|:----------------------|:--------------------------------------------------------------------------------|
| spring.datasource.url      | SPRING_DATASOURCE_URL      | jdbc:postgresql://... | URL of the database (ex jdbc:postgresql://postgresql.mydomain.com:5432/openbas) |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME |                       | Login for the database                                                          |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD | password              | Password for the database                                                       |

#### RabbitMQ

| Parameter                        | Environment variable             | Default value | Description                                 |
|:--------------------------------------|:--------------------------------------|:----------------------------------|:--------------------------------------------|
| openbas.rabbitmq.prefix               | OPENBAS_RABBITMQ_PREFIX               | openbas                           | Prefix for the queue names                  |
| openbas.rabbitmq.hostname             | OPENBAS_RABBITMQ_HOSTNAME             | localhost                         | Hostname of the RabbitMQ server             |
| openbas.rabbitmq.vhost                | OPENBAS_RABBITMQ_VHOST                | /                                 | Vhost of the RabbitMQ server                |
| openbas.rabbitmq.port                 | OPENBAS_RABBITMQ_PORT                 | 5672                              | Port of the RabbitMQ Server                 |
| openbas.rabbitmq.management-port      | OPENBAS_RABBITMQ_MANAGEMENT-PORT      | 15672                             | Management port of the RabbitMQ Server      |
| openbas.rabbitmq.ssl                  | OPENBAS_RABBITMQ_SSL                  | `false`                           | Use SSL                                     |
| openbas.rabbitmq.user                 | OPENBAS_RABBITMQ_USER                 | guest                             | RabbitMQ user                               |
| openbas.rabbitmq.pass                 | OPENBAS_RABBITMQ_PASS                 | guest                             | RabbitMQ password                           |
| openbas.rabbitmq.queue-type           | OPENBAS_RABBITMQ_QUEUE-TYPE           | classic                           | RabbitMQ Queue Type ("classic" or "quorum") |
| openbas.rabbitmq.management-insecure  | OPENBAS_RABBITMQ_MANAGEMENT-INSECURE  | `true`                            | Whether or not the calls to the management plugin of rabbitmq can be insecure |
| openbas.rabbitmq.trust.store          | OPENBAS_RABBITMQ_TRUST_STORE          | <file:/path/to/client-store.p12\> | Path to the p12 keystore file to use if ssl is activated and insecure management is deactivated. The keystore must contain the client side certificate and key generated for ssl. |
| openbas.rabbitmq.trust-store-password | OPENBAS_RABBITMQ_TRUST-STORE-PASSWORD | <trust-store-password\>           | Password of the keystore |

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
| openbas.s3.use-aws-role | OPENBAS_S3_USE-AWS-ROLE  | `false` | Whether or not we want to get the AWS role using AWS Security Token Service  |
| openbas.s3.sts-endpoint | OPENBAS_S3_STS-ENDPOINT  |         | `experimental` This parameter is optional. If it stays empty, it will use either the AWS legacy STS endpoint (https://sts.amazonaws.com) or the regional one using AWS_REGION if the environment variable is set (you can learn more about it [here](https://docs.aws.amazon.com/general/latest/gr/sts.html#sts_region)). Otherwise, if you want to use your own custom implementation of STS endpoints, you can set it here. |

#### Agents (executors)

To be able to use the power of the OpenBAS platform on endpoints, you need at least one **neutral executor** that will
be in charge of executing implants as detached processes. Implants will then execute payloads.

##### OpenBAS Agent

The OpenBAS agent is enabled by default and cannot be disabled. It is available for:

- Windows (`x86_64` / `arm64`)
- Linux (`x86_64` / `arm64`)
- MacOS (`x86_64` / `arm64`)

##### Other executors

To know more about other available executors, please refer to the [executors documentation](ecosystem/executors.md)

#### Mail services

For the associated mailbox, for the moment the platform only relies on IMAP / SMTP protocols, we are actively developing
integrations with APIs such as O365 and Google Apps.

##### SMTP

| Parameter            | Environment variable | Default value     | Description          |
|:---------------------|:---------------------|:------------------|:---------------------|
| spring.mail.host     | SPRING_MAIL_HOST     | smtp.mail.com     | SMTP Server hostname |
| spring.mail.port     | SPRING_MAIL_PORT     | 465               | SMTP Server port     |
| spring.mail.username | SPRING_MAIL_USERNAME | username@mail.com | SMTP Server username |
| spring.mail.password | SPRING_MAIL_PASSWORD | password          | SMTP Server password |

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
| openbas.mail.imap.enabled  | OPENBAS_MAIL_IMAP_ENABLED  | false             | Turn on to enable IMAP mail synchronization. Injector email must be well configured |
| openbas.mail.imap.host     | OPENBAS_MAIL_IMAP_HOST     | imap.mail.com     | IMAP Server hostname                                                                |
| openbas.mail.imap.port     | OPENBAS_MAIL_IMAP_PORT     | 993               | IMAP Server port                                                                    |
| openbas.mail.imap.username | OPENBAS_MAIL_IMAP_USERNAME | username@mail.com | IMAP Server username                                                                |
| openbas.mail.imap.password | OPENBAS_MAIL_IMAP_PASSWORD | password          | IMAP Server password                                                                |
| openbas.mail.imap.inbox    | OPENBAS_MAIL_IMAP_INBOX    | INBOX             | IMAP inbox directory to synchronize from                                            |
| openbas.mail.imap.sent     | OPENBAS_MAIL_IMAP_SENT     | Sent              | IMAP sent directory to synchronize from                                             |

| Parameter                         | Environment variable              | Default value | Description                   |
|:----------------------------------|:----------------------------------|:--------------|:------------------------------|
| openbas.mail.imap.ssl.enable      | OPENBAS_MAIL_IMAP_SSL_ENABLE      | true          | Turn on IMAP SSL mode         |
| openbas.mail.imap.ssl.trust       | OPENBAS_MAIL_IMAP_SSL_TRUST       | *             | Trust unverified certificates |
| openbas.mail.imap.auth            | OPENBAS_MAIL_IMAP_AUTH            | true          | Turn on IMAP authentication   |
| openbas.mail.imap.starttls.enable | OPENBAS_MAIL_IMAP_STARTTLS_ENABLE | true          | Turn on IMAP STARTTLS         |

> **Note :** Example with Gmail

| Parameter                    | Environment variable         | Value              | Description                             |
|:-----------------------------|:-----------------------------|:-------------------|:----------------------------------------|
| openbas.mail.imap.enabled    | OPENBAS_MAIL_IMAP_ENABLED    | true               | Enable IMAP for Gmail                   |
| openbas.mail.imap.host       | OPENBAS_MAIL_IMAP_HOST       | imap.gmail.com     | Gmail IMAP server hostname              |
| openbas.mail.imap.port       | OPENBAS_MAIL_IMAP_PORT       | 993                | Gmail IMAP port (SSL)                   |
| openbas.mail.imap.username   | OPENBAS_MAIL_IMAP_USERNAME   | username@mail.com  | Gmail address                           |
| openbas.mail.imap.password   | OPENBAS_MAIL_IMAP_PASSWORD   | app password       | Gmail App-specific password             |
| openbas.mail.imap.ssl.enable | OPENBAS_MAIL_IMAP_SSL_ENABLE | true               | Enable IMAP SSL                         |
| openbas.mail.imap.ssl.trust  | OPENBAS_MAIL_IMAP_SSL_TRUST  | *                  | Trust unverified certificates           |
| openbas.mail.imap.auth       | OPENBAS_MAIL_IMAP_AUTH       | true               | Enable IMAP authentication              |
| openbas.mail.imap.sent       | OPENBAS_MAIL_IMAP_SENT       | [Gmail]/Sent Mail  | IMAP sent directory to synchronize from |

⚠️ **Important**: If you are using two-factor authentication on your Gmail account, an app-specific password is
required. You can find a guide [here](https://support.google.com/accounts/answer/185833).

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
