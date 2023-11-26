# Authentication

## Introduction

Welcome to the authentication documentation for OpenEx. This documentation provides details on setting up and utilizing the authentication system, which supports multiple authentication methods to cater to different user needs and security requirements.

## Supported Authentication Methods

### Username-Password

Simple local authentication.

### OAuth (OpenID) and SAML2

OAuth (OpenID) authentication and SAML2 enable secure and streamlined Single Sign-On (SSO) by allowing users to authenticate using external identity providers or their organization's identity providers.

## Getting Started

Before you can start using any authentication method, follow these steps to set up the authentication system.

### Environment Variables

The authentication system relies on environment variables to function correctly.

#### Username/Password Environment Variables

- `OPENEX.AUTH-LOCAL-ENABLE`: Set this to `true` to enable username/password authentication.

#### OAuth (OpenID) Environment Variables

- `OPENEX.AUTH-OPENID-ENABLE`: Set this to `true` to enable OAuth (OpenID) authentication.
- Example for Auth0 :

```properties
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_{registrationId}_ISSUER-URI=https://auth.auth0.io/
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-NAME=Login with auth0
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-ID=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT-SECRET=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_REDIRECT-URI=${openex.base-url}/login/oauth2/code/{registrationId}
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_SCOPE=openid,profile,email
```

- Example for Github (or others pre handled oauth2 providers) :

```properties
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_NAME=Login with Github
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_ID=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_{registrationId}_CLIENT_SECRET=
```

!!! tip "Tips"

      *{registrationId} is an arbitrary identifier you choose.*

#### SAML2 Environment Variables

- `OPENEX.AUTH-SAML2-ENABLE`: Set this to `true` to enable SAML2 authentication.
- Example for Microsoft :

```properties
SPRING_SECURITY_SAML2_RELYINGPARTY_REGISTRATION_{registrationId}_ENTITY-ID=
SPRING_SECURITY_SAML2_RELYINGPARTY_REGISTRATION_{registrationId}_ASSERTINGPARTY_METADATA-URI=

OPENEX_PROVIDER_{registrationId}_FIRSTNAME_ATTRIBUTE_KEY=
OPENEX_PROVIDER_{registrationId}_LASTNAME_ATTRIBUTE_KEY=
```

!!! tip "Tips"
     
      *{registrationId} is an arbitrary identifier you choose.*
      metadata-uri is the uri of the xml file given by your identity provider

### Single Sign On URL

#### SAML2

Url for the config of your sso provider
```
${openex.base-url}/login/saml2/sso/{registrationId}
```


### Administrative Access

To grant administrative roles, you can utilize OAuth and SAML2 integration. If you opt for this approach, you'll need to include the following variables:

```properties
OPENEX_PROVIDER_{registrationId}_ROLES_PATH=
OPENEX_PROVIDER_{registrationId}_ROLES_ADMIN=
```

However, if you intend to manage administrative roles within the OpenEx platform itself, there's no need to provide these variables.


## Error Handling

!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenEx-Platform/docs).
