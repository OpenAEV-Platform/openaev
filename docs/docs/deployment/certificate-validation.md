# Certificate validation

OpenAEV enforces TLS/SSL certificate validation on the server side by default. This page explains how to configure certificate trust for outgoing and incoming connections.

## What is this?

Certificate validation ensures that OpenAEV only communicates with trusted endpoints. When OpenAEV connects to injectors, collectors, or external APIs over HTTPS, it verifies that the remote certificate is valid, not expired, and issued by a trusted Certificate Authority (CA).

## Why does it matter?

- **Security** — Prevents man-in-the-middle (MITM) attacks on all outgoing connections.
- **Compliance** — Many security frameworks require strict TLS validation in production.
- **Flexibility** — OpenAEV supports custom trust stores, so you can trust internal CAs without disabling validation.

## Outgoing connections (OpenAEV as client)

### Strict validation (default)

By default, OpenAEV validates all outgoing TLS certificates against the JVM's default trust store (`cacerts`). No configuration is needed.

| Parameter | Environment variable | Default | Description |
|:--|:--|:--|:--|
| `openaev.unsecured-certificate` | `OPENAEV_UNSECURED-CERTIFICATE` | `false` | Reject untrusted SSL certificates |

⚠️ **Warning** — Keep this set to `false` in production. Setting it to `true` disables certificate chain validation for **all** outgoing connections and exposes the platform to MITM attacks.

### Allow self-signed certificates (dev/test only)

For development or air-gapped environments with self-signed certificates, disable strict validation:

```yaml
services:
  openaev:
    image: openaev/platform:latest
    environment:
      - OPENAEV_UNSECURED-CERTIFICATE=true
```

⚠️ **Warning** — Use this only in controlled, non-production environments.

### Add custom trusted certificates (recommended)

Instead of disabling validation, add your internal CA or self-signed certificates to the OpenAEV trust store:

| Parameter | Environment variable | Default | Description |
|:--|:--|:--|:--|
| `openaev.extra-trusted-certs-dir` | `OPENAEV_EXTRA-TRUSTED-CERTS-DIR` | — | Directory containing additional trusted PEM certificates |

**Requirements:**

- Certificate files must be in **PEM format** (`.pem`) or **DER-encoded X.509** format.
- The directory must be readable by the OpenAEV process.

### Example

You have an internal CA that signs certificates for your CrowdStrike and Tanium integrations.

1. Export your internal CA certificate as `internal-ca.pem`.
2. Place it in a `./my-custom-certs/` directory on the host.
3. Mount it into the container:

```yaml
services:
  openaev:
    image: openaev/platform:latest
    environment:
      - OPENAEV_EXTRA-TRUSTED-CERTS-DIR=/opt/openaev/certs
    volumes:
      - ./my-custom-certs:/opt/openaev/certs:ro
```

OpenAEV now trusts your internal CA **while keeping strict validation** for everything else.

💡 **Tip** — This is the recommended approach for production environments with internal PKI.

## Incoming connections (TLS termination)

OpenAEV can terminate TLS directly using Spring Boot's built-in SSL support, without a reverse proxy.

| Parameter | Environment variable | Default | Description |
|:--|:--|:--|:--|
| `server.ssl.enabled` | `SERVER_SSL_ENABLED` | `false` | Enable SSL on the server |
| `server.ssl.key-store-type` | `SERVER_SSL_KEY-STORE-TYPE` | `PKCS12` | Keystore type |
| `server.ssl.key-store` | `SERVER_SSL_KEY-STORE` | `classpath:localhost.p12` | Keystore path |
| `server.ssl.key-store-password` | `SERVER_SSL_KEY-STORE-PASSWORD` | `admin` | Keystore password |
| `server.ssl.key-alias` | `SERVER_SSL_KEY-ALIAS` | `localhost` | Key alias in keystore |

### Example

```yaml
services:
  openaev:
    image: openaev/platform:latest
    environment:
      - SERVER_SSL_ENABLED=true
      - SERVER_SSL_KEY-STORE-TYPE=PKCS12
      - SERVER_SSL_KEY-STORE=/opt/openaev/keystore.p12
      - SERVER_SSL_KEY-STORE-PASSWORD=my-secure-password
      - SERVER_SSL_KEY-ALIAS=openaev
      - OPENAEV_COOKIE-SECURE=true
    volumes:
      - ./keystore.p12:/opt/openaev/keystore.p12:ro
```

💡 **Tip** — Most production deployments terminate TLS at a reverse proxy (Nginx, Traefik, HAProxy). In that case, leave `server.ssl.enabled=false` and set `openaev.cookie-secure=true` if end-users connect over HTTPS.

## Proxy support

If OpenAEV connects to external services through a proxy, enable proxy support:

| Parameter | Environment variable | Default | Description |
|:--|:--|:--|:--|
| `openaev.with-proxy` | `OPENAEV_WITH-PROXY` | `false` | Enable proxy environment support |

When enabled, OpenAEV respects the standard `HTTP_PROXY`, `HTTPS_PROXY`, and `NO_PROXY` environment variables.

## Quick reference

| Scenario | Configuration |
|:--|:--|
| Production with public CA-signed certificates | Default (no changes needed) |
| Internal CA or self-signed certs for integrations | `openaev.extra-trusted-certs-dir` → your PEM directory |
| Development / testing | `openaev.unsecured-certificate=true` (temporary only) |
| TLS termination on OpenAEV | `server.ssl.enabled=true` + keystore config |
| TLS termination on reverse proxy | `server.ssl.enabled=false` + `openaev.cookie-secure=true` |

## What's next?

- [Configuration reference](../configuration.md) — Full list of OpenAEV platform parameters.
- [Deployment overview](../overview.md) — Architecture and deployment options.
