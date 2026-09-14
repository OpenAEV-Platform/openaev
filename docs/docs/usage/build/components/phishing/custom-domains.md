# Custom domains

By default, phishing Landing Pages are served from the platform host, so the lure link points at your OpenAEV address. A custom domain lets you serve them from a hostname you own, for example `security.acme.com`, so the link in the email and the address bar match your organization's branding.

Open **Settings > Customization > Custom domains** to manage them. Managing custom domains requires *Manage tenant settings*, the same capability as the other Tenant customizations.

## Why use a custom domain?

- Make the lure realistic: a link to a hostname the recipient recognizes is far more convincing than a link to your testing platform.
- Keep the platform address out of the exercise, so a curious recipient cannot identify the tool from the URL.
- Use different hostnames for different exercises or business units, and link each Landing Page to the one it belongs to.

## Register a domain

1. Go to **Settings > Customization > Custom domains**.
2. Click **Add a custom domain** and enter the hostname.
3. The platform returns two DNS records to publish. Add both at your DNS provider:

    | Purpose | Type | Name / host | Value |
    |---|---|---|---|
    | Route traffic to the platform | `CNAME` | the hostname itself | the platform host |
    | Prove ownership | `TXT` | `_openaev-challenge.<hostname>` | the verification token shown in the panel |

4. Wait for the records to propagate, then click **Verify domain**.
5. On success the domain becomes **Verified** and can be linked to Landing Pages.

If your DNS provider cannot create a `CNAME` at that name (typically at a zone apex), publish an `A` record pointing at the platform IP address instead. The `TXT` challenge is required either way.

!!! note

    DNS propagation usually takes a few minutes but can take longer. A failed check is not final: use **Re-check DNS** as many times as needed. The panel shows the last check time and the reason the last attempt failed.

## Domain statuses

| Status | Meaning |
|---|---|
| Pending verification | Registered, ownership challenge not yet satisfied. |
| Verified | The `TXT` challenge was found. The domain can be linked and served. |
| Verification failed | A verification attempt ran and the challenge was not found. Fix the record and re-check. |

Re-checking an already-verified domain never downgrades it: a transient DNS failure leaves the domain verified and only records the error.

## Why a TXT record is required

A `CNAME` or `A` record only proves that a hostname points at the platform, and anyone can create one for a name they do not control. The secret `TXT` challenge proves you control the DNS zone, which is what prevents one Tenant from claiming a hostname belonging to another.

Hostnames are unique across the whole platform: registering a hostname another Tenant already registered is rejected with *This domain is already registered*.

## Link a domain to a landing page

1. Open the [Landing Page](landing-pages.md) you want to serve on the custom domain.
2. Set **Serve on domain** to the hostname.
3. Save.

Only verified domains appear in that list. From then on, lure links generated for that Landing Page use `https://<your-hostname>/auth/<token>`.

Deleting a custom domain does not delete the Landing Pages that used it: they silently fall back to the platform default domain. Links already sent on the deleted hostname stop working once DNS no longer resolves.

## Serving traffic and certificates

Two things must be in place for an inbound request on your hostname to reach the platform:

1. **DNS** -- the `CNAME` or `A` record you published in the registration step.
2. **A certificate** -- the platform exposes a public ownership check that an on-demand TLS edge (a reverse proxy such as Caddy with `on_demand_tls ask`) calls before issuing a certificate for an inbound hostname:

    ```http
    GET /api/hosted/domain-check?domain=<hostname>
    ```

    It answers `200` for a verified custom domain and `404` for anything else. An unverified or unknown hostname is therefore refused at the edge and never gets a certificate.

Configure your edge to use that endpoint as its allow-list. See [Configuration](../../../../deployment/configuration.md) for the platform-side settings.

!!! warning

    Point a custom domain only at hostnames you own and are authorized to use for awareness exercises. Serving a lure page on a hostname that belongs to someone else is not an exercise.

## What's next?

- [Landing pages](landing-pages.md) -- Link a page to a verified domain
- [Run a phishing exercise](run-a-campaign.md) -- See how the lure link is built
