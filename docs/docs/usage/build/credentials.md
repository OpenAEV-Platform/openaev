# Credentials

A Credential stores the secret an Inject needs to authenticate against its target: a username and password, a password
hash, or an access to an AWS, Azure or Google Cloud account. This page explains how to create Credentials, how OpenAEV
checks them, and how to attach them to an Inject.

!!! note "Preview feature"

    Credentials are a preview feature. They are only available when `CREDENTIAL_ASSET` is listed in
    `openaev.enabled-dev-features`. See [Configuration](../../reference/deployment/configuration.md).

## Why use Credentials?

Many Injects act on a target that requires authentication: a cloud attack technique needs access to the cloud account,
a lateral movement test needs an account or a hash. Without Credentials, you would type the secret in every Inject.

With Credentials:

- **You store each secret once** and reuse it in as many Injects as you need.
- **Secrets never appear in the Inject**: the Inject only holds a reference to the Credential. The secret is never
  displayed back in the UI, and the Injector receives it only while the Inject runs.
- **You see a revoked secret before it breaks a Simulation**: OpenAEV regularly checks cloud Credentials against their
  provider and shows their status.
- **You rotate a secret in one place**: update the Credential and every Inject that uses it picks up the new value at
  its next execution.

Credentials belong to a Tenant: they are only visible and usable inside the Tenant where you created them.

## Credential types

When you create a Credential, choose a **Type**, then an **Auth method**. The form then shows the fields of this
authentication method. Secret fields (passwords, keys, tokens) are write-only: once saved, they are never shown again.
To change one, type a new value; leave the field untouched to keep the stored value.

### Identity

| Auth method           | Field              | Required | Description                                  |
|:----------------------|:-------------------|:---------|:---------------------------------------------|
| Username and password | **Username**       | Yes      | Account name                                 |
|                       | **Password**       | Yes      | Account password (secret)                    |
| Hash                  | **Hash**           | Yes      | Password hash (secret)                       |
|                       | **Hash algorithm** | Yes      | Algorithm of the hash: `SHA` or `NTLM`       |

### Cloud AWS

| Auth method      | Field                                | Required                                  | Description                                                                                               |
|:-----------------|:-------------------------------------|:------------------------------------------|:----------------------------------------------------------------------------------------------------------|
| AWS access key   | **Default region**                   | Yes                                       | AWS region used by default, for example `us-east-1`                                                        |
|                  | **Access key id**                    | Yes                                       | IAM (Identity and Access Management) access key ID                                                                                         |
|                  | **Secret access key**                | Yes                                       | IAM secret access key (secret)                                                                            |
|                  | **Session token**                    | No                                        | Session token, for temporary credentials only (secret)                                                    |
| AWS assume role  | **Default region**                   | Yes                                       | AWS region used by default                                                                                |
|                  | **Role arn**                         | Yes                                       | ARN (Amazon Resource Name) of the IAM role to assume                                                      |
|                  | **External id**                      | No                                        | External ID required by the role trust policy, if any (secret)                                            |
|                  | **Identity type**                    | Yes                                       | Identity that assumes the role: `Static access key` (a key you provide) or `Instance default` (the identity of the machine that runs the Injector) |
|                  | **Source profile access key id**     | When **Identity type** is `Static access key` | Access key ID of the identity that assumes the role                                                   |
|                  | **Source profile secret access key** | When **Identity type** is `Static access key` | Secret access key of the identity that assumes the role (secret)                                      |

### Cloud Azure

| Auth method             | Field               | Required | Description                                                                                         |
|:------------------------|:--------------------|:---------|:----------------------------------------------------------------------------------------------------|
| Azure service principal | **Environment**     | Yes      | Azure cloud: `AzureCloud`, `AzureChinaCloud`, `AzureUSGovernment` or `AzureGermanCloud`               |
|                         | **Client ID**       | Yes      | Application (client) ID of the service principal                                                    |
|                         | **Client secret**   | Yes      | Client secret of the service principal (secret)                                                     |
|                         | **Tenant ID**       | Yes      | Microsoft Entra ID tenant (directory) ID                                                            |
|                         | **Subscription ID** | No       | Azure subscription to work on                                                                       |
| Azure managed identity  | **Environment**     | Yes      | Azure cloud                                                                                         |
|                         | **Client ID**       | No       | Client ID of a user-assigned managed identity. Leave empty to use the system-assigned identity       |
|                         | **Subscription ID** | No       | Azure subscription to work on                                                                       |

!!! note

    A managed identity holds no secret: it is the identity of the Azure machine that runs the Injector. Use it only
    with an Injector deployed in Azure.

### Cloud GCP

| Auth method         | Field                        | Required | Description                                                                          |
|:--------------------|:-----------------------------|:---------|:-------------------------------------------------------------------------------------|
| GCP service account | **Scope**                    | Yes      | OAuth scope, `https://www.googleapis.com/auth/cloud-platform` by default             |
|                     | **Service account key file** | Yes      | JSON key file of the service account (secret)                                        |
|                     | **Project ID**               | No       | Google Cloud project to work on                                                      |
| GCP OAuth 2.0       | **Scope**                    | Yes      | OAuth scope, `https://www.googleapis.com/auth/cloud-platform` by default             |
|                     | **Client ID**                | Yes      | OAuth 2.0 client ID                                                                  |
|                     | **Client secret**            | Yes      | OAuth 2.0 client secret (secret)                                                     |
|                     | **Refresh token**            | Yes      | OAuth 2.0 refresh token (secret)                                                     |
|                     | **Project ID**               | No       | Google Cloud project to work on                                                      |

## How to create a Credential

1. Go to **Assets > Credentials**.
2. Click **Create**.
3. Enter a **Name**, and optionally a **Description** and **Tags**.
4. Select the **Type** and the **Auth method**.
5. Fill in the fields of the authentication method. See [Credential types](#credential-types).
6. Click **Create**.

![Creation form of an AWS access key Credential](assets/credential-form.png)

OpenAEV stores the secret encrypted and checks a cloud Credential against its provider right away. The Credential then
appears in the list.

To update or delete a Credential, open its menu (**⋮**) and click **Update** or **Delete**. To delete several Credentials
at once, select them in the list and use the delete action of the toolbar.

!!! danger

    Deleting a Credential removes it from every Inject that uses it. An Inject whose Credential field is mandatory is
    then no longer ready to run.

## Credential status

The **Status** column of the list and the detail page of a Credential show whether OpenAEV can still authenticate with
it. The **Last verified** column shows the date of the last check.

![Credentials list with their status](assets/credential-list.png)

| Status       | Meaning                                                                                       |
|:-------------|:----------------------------------------------------------------------------------------------|
| **Active**   | The provider accepted the Credential at the last check                                        |
| **Inactive** | The last check failed. Hover over the status to see the reason (see the table below)          |
| `-`          | The Credential has not been checked. Identity Credentials (username and password, hash) have no remote counterpart and are never checked |

| Reason              | What it means                                                       |
|:--------------------|:--------------------------------------------------------------------|
| `AUTH_FAILED`       | The provider rejected the access key or secret                      |
| `PERMISSION_DENIED` | The Credential authenticates, but lacks the permissions to be used  |
| `TIMEOUT`           | The provider did not answer in time                                 |
| `NETWORK_ERROR`     | OpenAEV cannot reach the provider: check the network or the region  |
| `UNSUPPORTED`       | This type or authentication method cannot be checked                |
| `FORMAT_ERROR`      | The Credential format is invalid                                    |
| `UNKNOWN`           | The check failed for an unknown reason                              |

OpenAEV checks a Credential when you create or update it, then again on a schedule, once its last check is older than
one day by default. A check that cannot conclude (timeout, provider outage) never overwrites the previous status. You
can tune the schedule in [Credential status validation](../../reference/deployment/configuration.md#credential-status-validation).

## Use a Credential in an Inject

Credentials are offered only by the Inject types whose contract declares a Credential field, for example cloud attack
techniques. For these Inject types, the Inject form shows a Credential section, labelled by the Injector.

1. Create or open an Inject in a Scenario, a Simulation or an Atomic Test.
2. In the Credential section, click **Update credential** (or **Update credentials** when the Inject accepts several).

    ![Credential section of a Prowler AWS Inject form](assets/inject-form-credential-update.png)

3. In the dialog, search and select the Credentials to use. When the Inject expects a given type, for example
   `Cloud AWS`, the list is filtered on this type.

    ![Credential selection dialog filtered on the Cloud AWS type](assets/inject-form-credential-popover.png)

4. Validate the selection, then save the Inject.

To detach a Credential from an Inject, open its menu (**⋮**) in the Credential section and click **Remove from the inject**.

!!! note

    You can save an Inject without its mandatory Credential. The Inject is then flagged as not ready, and fails at
    execution if the Credential is still missing.

When you create a Simulation from a Scenario, or duplicate an Inject, the Injects keep their Credentials.

### What happens at execution

The Inject never carries the secret itself. When the Inject runs:

1. OpenAEV sends the Inject to its Injector with the references of its Credentials and a single-use authorisation code.
2. The Injector exchanges this code with OpenAEV for the secret value.
3. Once the Inject execution ends, successfully or not, OpenAEV revokes the code: the secret can no longer be retrieved
   through this Inject.

OpenAEV refuses to hand over a Credential whose status is **Inactive**: the Inject then fails with a
`CREDENTIAL_ACCESS_DENIED` (rejected by the provider) or `CREDENTIAL_INACTIVE` (any other failure) error. Fix or replace
the Credential, then run the Inject again.

!!! example

    You want to check whether your cloud detection catches the discovery of IAM users in your AWS test account:

    1. Create a `Cloud AWS` Credential with the `AWS assume role` method, pointing to a dedicated read-only role.
    2. Wait for its status to turn **Active**.
    3. In a Scenario, add an AWS discovery Inject and select this Credential in its Credential section.
    4. Add a **Detection** expectation, then launch the Simulation.

## Permissions

Credentials have their own capabilities, set on Tenant roles in **Settings > Security > Roles**:

| Capability              | Allows                                                                                                         |
|:------------------------|:---------------------------------------------------------------------------------------------------------------|
| `Access credentials`    | See the Credentials list and details, and select Credentials in an Inject                                       |
| `Manage credentials`    | Create and update Credentials                                                                                   |
| `Delete credentials`    | Delete Credentials                                                                                              |
| `Resolve inject secret` | Retrieve the secret of a Credential attached to a running Inject. Grant it only to the account of your Injectors |

See [Users and RBAC](../../administration/users-and-rbac.md) for how to create roles.

## What's next?

- [Injects](../run-and-evaluate/injects/inject-overview.md) -- Create and configure Injects
- [Assets](assets.md) -- Manage the Endpoints and Asset groups targeted by your Injects
- [Configuration](../../reference/deployment/configuration.md#credential-status-validation) -- Tune the Credential status check
- [Injector development](../../development/injectors.md#credentials) -- Use Credentials in your own Injector
