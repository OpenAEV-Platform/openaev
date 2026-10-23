# Injector development

This page explains what OpenAEV expects from an Injector: a process that adds new types of Injects to the platform.

To set up a new Injector project (template, `pyoaev` dependency, local development), follow the [Injectors repository README](https://github.com/OpenAEV-Platform/injectors#development). For a working example, read the [HTTP Injector](https://github.com/OpenAEV-Platform/injectors/tree/main/http-query). For a functional overview, see [Injectors](../integrations/injectors/injectors.md).

## How an Injector works with OpenAEV

An Injector talks to OpenAEV through the REST API and a RabbitMQ queue, so you can write it in any language.

1. **Register.** The Injector sends `POST /api/injectors` as a multipart request: an `input` part with its `injector_id`, `injector_name`, `injector_type` and `injector_contracts`, and an optional PNG `icon` part. The response gives the RabbitMQ `connection` details and the queue to `listen` to: `<prefix>_injector_<injector_id>`, where the prefix is `openaev.rabbitmq.prefix` (`openaev` by default). The Injector then appears under **Integrations > Deployed**.
2. **Consume.** OpenAEV pushes each Inject to execute to this queue.
3. **Report.** The Injector sends the result with `POST /api/injects/execution/callback/{injectId}`, with at least `execution_message` and `execution_status`.

## Contracts

A contract lists the fields of an Inject. OpenAEV uses it to build the Inject form in the UI, and the Injector uses it to read the values the user entered. An Injector can declare several contracts.

## Credentials

An Injector that must authenticate against its target (a cloud account, a remote host) reads the secret from a
[Credential](../usage/build/credentials.md) instead of a plain contract field. The secret never travels in the Inject
itself: the Injector fetches it from OpenAEV while the Inject runs.

!!! note "Preview feature"

    Credentials are only available when `CREDENTIAL_ASSET` is listed in `openaev.enabled-dev-features`.

### Declare a Credential field

Add a field of type `credential-reference` to the contract. The UI then shows a Credential selector in the Inject form.

| Property                    | Description                                                                                                    |
|:----------------------------|:---------------------------------------------------------------------------------------------------------------|
| `type`                      | `credential-reference`                                                                                         |
| `mandatory`                 | `true` to flag the Inject as not ready while no Credential is selected                                         |
| `cardinality`               | `1` to accept a single Credential, `n` to accept several                                                       |
| `credential_reference_type` | Optional. Restricts the selector to one type: `IDENTITY`, `CLOUD_AWS`, `CLOUD_AZURE` or `CLOUD_GCP`             |

```json
{
  "key": "credential_reference",
  "label": "AWS account",
  "type": "credential-reference",
  "mandatory": true,
  "cardinality": "1",
  "credential_reference_type": "CLOUD_AWS"
}
```

### Resolve the secret at execution

When an Inject carries Credentials, the message pushed to the Injector queue holds an `attachments` object:

```json
{
  "attachments": {
    "credential_references": ["<credential_id>"],
    "authorisation_code": "<execution-scoped code>"
  }
}
```

For each Credential, the Injector exchanges the code for the secret. The same code is valid for every Credential of
the Inject, for the whole execution:

```http
POST /api/injects/{injectId}/attachment/secret
Content-Type: application/json

{
  "attachment_id": "<credential_id>",
  "authorisation": "<authorisation_code>"
}
```

The account of the Injector needs the `Resolve inject secret` capability. The response gives the secret kind in `type`
and its fields in `value`:

```json
{
  "type": "AWS_ACCESS_KEY",
  "value": {
    "aws_default_region": "us-east-1",
    "aws_access_key_id": "AKIA...",
    "aws_secret_access_key": "..."
  }
}
```

| `type`                    | `value` fields                                                                                                                         |
|:--------------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `USERNAME_PASSWORD`       | `username`, `password`                                                                                                                 |
| `HASH`                    | `hash_algorithm`, `hash`                                                                                                               |
| `AWS_ACCESS_KEY`          | `aws_default_region`, `aws_access_key_id`, `aws_secret_access_key`, `aws_session_token`                                                |
| `AWS_ASSUME_ROLE`         | `aws_default_region`, `aws_role_arn`, `aws_source_identity_type`, `aws_external_id`, `aws_source_profile_access_key_id`, `aws_source_profile_secret_access_key` |
| `AZURE_SERVICE_PRINCIPAL` | `azure_environment`, `azure_client_id`, `azure_client_secret`, `azure_tenant_id`, `azure_subscription_id`                              |
| `AZURE_MANAGED_IDENTITY`  | `azure_environment`, `azure_client_id`, `azure_subscription_id`                                                                        |
| `GCP_SERVICE_ACCOUNT`     | `gcp_scope`, `gcp_project_id`, `gcp_private_key_json` (Base64-encoded key file)                                                        |
| `GCP_OAUTH2`              | `gcp_scope`, `gcp_project_id`, `gcp_oauth_client_id`, `gcp_oauth_client_secret`, `gcp_oauth_refresh_token`                            |

Optional fields left empty are omitted from `value`.

The code is valid only while the Inject execution is in progress: OpenAEV revokes it as soon as the execution ends. The
request fails with:

| Status | Error                      | Cause                                                                                           |
|:-------|:---------------------------|:------------------------------------------------------------------------------------------------|
| 403    | `CREDENTIAL_ACCESS_DENIED` | Invalid or revoked code, Inject no longer running, or Credential rejected by its provider        |
| 400    | `CREDENTIAL_INACTIVE`      | The Credential is inactive for any other reason (timeout, network error, invalid format)         |
| 404    | `CREDENTIAL_NOT_FOUND`     | The Credential is not attached to this Inject                                                    |

!!! warning

    Keep the secret in memory only for the time of the execution. Never write it to the execution callback, logs or
    files.

## What's next?

- [Deploy Injectors](../integrations/injectors/deploy-injectors.md) -- Deploy and configure an Injector
- [Credentials](../usage/build/credentials.md) -- Create the Credentials your Injector authenticates with
- [Collector development](collectors.md) -- Build a custom Collector
- [REST API](../reference/apis/rest-api.md) -- Authentication and API reference
