# Authentication

## Introduction

This documentation provides details on setting up and utilizing the authentication system, which supports multiple authentication methods to cater to different user needs and security requirements.

## Supported authentication methods

!!! tip "Production deployment"

    Please use the LDAP/Auth0/OpenID/SAML strategy for production deployment.

### Local users

OpenAEV uses this strategy as the default, but it's not the one we recommend for security reasons.

| Parameter                 | Environment variable      | Default value         | Description                                                   |
|:--------------------------|:--------------------------|:----------------------|:--------------------------------------------------------------|
| openaev.auth-local-enable | OPENAEV_AUTH-LOCAL-ENABLE | true               | Set this to `true` to enable username/password authentication. |

### OpenID

This method allows using the [OpenID Connect Protocol](https://openid.net/connect) to handle the authentication.

| Parameter                      | Environment variable       | Default value         | Description                                                   |
|:-------------------------------|:---------------------------|:----------------------|:--------------------------------------------------------------|
| openaev.auth-openid-enable                 | OPENAEV_AUTH-OPENID-ENABLE | false               | Set this to `true` to enable OAuth (OpenID) authentication. |

Example for Auth0:

```properties
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_{registrationId}_ISSUER-URI=https://auth.auth0.io
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-NAME=Login with auth0
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-ID=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-SECRET=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_REDIRECT-URI=${openaev.base-url}/login/oauth2/code/{registrationId}
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_SCOPE=openid,profile,email
```

Example for GitHub (or others pre-handled oauth2 providers):

```properties
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_NAME=Login with Github
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_ID=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_SECRET=
```

!!! tip "Tips"

      *{registrationId} is an arbitrary identifier you choose.*

### SAML2

This strategy can be used to authenticate your user with your company SAML.

| Parameter                      | Environment variable           | Default value         | Description                                                   |
|:-------------------------------|:-------------------------------|:----------------------|:--------------------------------------------------------------|
| openaev.auth-saml2-enable                 | OPENAEV_AUTH-SAML2-ENABLE                 | false               | Set this to `true` to enable SAML2 authentication. |
 
Example for Microsoft :
```properties
SPRING_SECURITY_SAML2_RELYINGPARTY_REGISTRATION_{registrationId}_ENTITY-ID=
SPRING_SECURITY_SAML2_RELYINGPARTY_REGISTRATION_{registrationId}_ASSERTINGPARTY_METADATA-URI=
OPENAEV_PROVIDER_{registrationId}_FIRSTNAME_ATTRIBUTE_KEY=
OPENAEV_PROVIDER_{registrationId}_LASTNAME_ATTRIBUTE_KEY=
```

!!! tip "Tips"
     
      *{registrationId} is an arbitrary identifier you choose.*
      metadata-uri is the uri of the xml file given by your identity provider

### Single Sign On URL

#### SAML2

Url for the config of your sso provider
```
${openaev.base-url}/login/saml2/sso/{registrationId}
```

### Map administrators to specific roles (OpenID and SAML 2)

To grant administrative roles, you can utilize OAuth and SAML2 integration. If you opt for this approach, you'll need to include the following variables:

```properties
OPENAEV_PROVIDER_{registrationId}_ROLES_PATH=http://schemas.microsoft.com/ws/2008/06/identity/claims/role
OPENAEV_PROVIDER_{registrationId}_ROLES_ADMIN=
```

However, if you intend to manage administrative roles within the OpenAEV platform itself, there's no need to provide these variables.

## Breakglass local account with SSO enabled

### Why you need a breakglass account

When you configure OpenAEV to use an external identity provider (OpenID Connect or SAML2) as the sole authentication method, your platform becomes dependent on that provider's availability. If the identity provider experiences an outage, a misconfiguration, or a certificate expiry, **all users — including administrators — will be locked out** of the platform.

A **breakglass account** (also known as an emergency access account) is a local administrator account that remains available even when SSO is down. It is a critical component of any production deployment that relies on federated authentication.

!!! warning "Security best practice"

    The breakglass account should **only** be used in emergency situations when the SSO provider is unavailable. It should not be used for day-to-day operations.

### How it works

OpenAEV supports running **multiple authentication methods simultaneously**. The three authentication flags ([Local users](#local-users), [OpenID](#openid), [SAML2](#saml2)) are independent of each other.

When `openaev.auth-local-enable` is set to `true` **alongside** your SSO provider, the login page will display **both** the local login form (username/password) and the SSO button(s). This allows the built-in admin account to authenticate locally even if the external identity provider is unreachable.

### Configuration

To set up a breakglass account alongside SSO, keep local authentication enabled while configuring your SSO provider.

#### Step 1: Keep local authentication enabled

Make sure the [local authentication](#local-users) flag remains `true` (this is the default):

```yaml
OPENAEV_AUTH-LOCAL-ENABLE=true
```

#### Step 2: Configure a strong admin account

Set a strong, unique password for the built-in admin account. This is the account defined at platform initialization:

```yaml
OPENAEV_ADMIN_EMAIL=admin@mycompany.com
OPENAEV_ADMIN_PASSWORD=<a-very-strong-and-unique-password>
OPENAEV_ADMIN_TOKEN=<a-valid-uuidv4-token>
```

#### Step 3: Enable your SSO provider

Configure your SSO provider by following the appropriate section:

- [OpenID Connect](#openid) — for OAuth2/OIDC providers (Auth0, Keycloak, Azure AD, GitHub, etc.)
- [SAML2](#saml2) — for SAML-based identity providers (Microsoft ADFS, Okta, etc.)

!!! tip "Reminder"

    Make sure to set `OPENAEV_AUTH-OPENID-ENABLE=true` or `OPENAEV_AUTH-SAML2-ENABLE=true` depending on your provider, **in addition to** the local authentication flag configured in Step 1.

### Login page behavior

When both local authentication and an SSO provider are enabled, the login page displays:

1. **The local login form** — username and password fields at the top
2. **SSO button(s)** — one button per configured OpenID or SAML2 provider below the form

Regular users authenticate via the SSO button. The breakglass admin uses the local form only when the SSO provider is unavailable.