# Proxy Support

## Overview

XTM Composer can use system proxy settings for outgoing network calls.

### YAML configuration

```yaml
openaev:
  daemon:
    with_proxy: true
```

### Environment variable configuration

```bash
export OPENAEV__DAEMON__WITH_PROXY="true"
export HTTP_PROXY="http://proxy.example.com:8080"
export HTTPS_PROXY="http://proxy.example.com:8080"
export NO_PROXY="localhost,127.0.0.1,.example.com"
```

When enabled, the Integration Manager automatically applies the proxy settings to:

- Docker API calls
- Kubernetes image pulls
- Portainer API requests

## HTTPS Proxy Certificate Support (optional)

Some environments use HTTPS proxies with TLS interception (for example, corporate proxies or debugging proxies like Burp).
In these cases, additional certificate settings may be required.

### Environment variables

```bash
export HTTPS_PROXY_CA='["/path/to/proxy-ca.pem"]'
export HTTPS_PROXY_REJECT_UNAUTHORIZED="false"
```

  - HTTPS_PROXY_CA — List of CA certificates (file paths or PEM blocks) used to validate the proxy’s certificate.   
  - HTTPS_PROXY_REJECT_UNAUTHORIZED — If set to "false", certificate validation is disabled for proxy connections (default behavior).

### Important: Certificate Scope Clarification

Composer distinguishes two independent certificate configurations:

| Purpose                           | Keys                                                  | Description                                                      |
|-----------------------------------|-------------------------------------------------------|------------------------------------------------------------------|
| OpenAEV HTTPS server certificates | app.https_cert.ca, app.https_cert.reject_unauthorized | TLS configuration for the OpenAEV web server                     |
| Proxy HTTPS certificates          | https_proxy_ca, https_proxy_reject_unauthorized       | Validation settings for HTTPS connections made through the proxy |

These settings must not be mixed.

### Proxy Configuration in config.json

Example of equivalent configuration in a JSON file:

```json
{
  "http_proxy": "http://proxy.example.com:8080",
  "https_proxy": "http://proxy.example.com:8080",
  "no_proxy": "localhost,127.0.0.1,internal.domain",
  "https_proxy_ca": ["/path/to/proxy-ca.pem"],
  "https_proxy_reject_unauthorized": false
}
```


## Certificate Separation

⚠️ **Important**: Proxy certificates are separate from OpenAEV server certificates.

| Purpose                         | Configuration Keys                                          | Used For                                           |
|---------------------------------|-------------------------------------------------------------|----------------------------------------------------|
| **Proxy certificates**          | `https_proxy_ca`<br>`https_proxy_reject_unauthorized`       | Validating HTTPS connections **through the proxy** |
| **OpenAEV server certificates** | `app:https_cert:ca`<br>`app:https_cert:reject_unauthorized` | TLS for the OpenAEV web server itself              |

**Do not confuse these two configurations.**

---

### Troubleshooting - Collector, executor or injector integration

### Automatic Injection

When proxy is enabled, XTM Composer automatically injects these environment variables into all managed collectors, injectors and executors containers:

- `HTTP_PROXY`
- `HTTPS_PROXY`
- `NO_PROXY`
- `HTTPS_CA_CERTIFICATES` (when `https_proxy_ca` is configured)

### Verification

To verify that proxy settings are correctly injected, call the following API endpoint:

```
GET /api/connector-instances/{instance-id}
```

Replace `{instance-id}` with your connector instance ID.



See also: [Private Registry Authentication](registry-authentification.md)