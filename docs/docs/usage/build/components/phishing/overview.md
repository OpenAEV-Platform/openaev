# Phishing

Phishing lets you run built-in phishing exercises directly from OpenAEV, without deploying any external component. You author reusable **Landing Pages** and **Email Templates** as Components, and each Landing Page automatically becomes a Threat Arsenal action you can use in an Inject.

The built-in Injector reuses the platform's global SMTP (Simple Mail Transfer Protocol) configuration, the same mail service as the built-in email Injector, to deliver the lure emails, so there is nothing extra to install.

## Why use Phishing?

- Run credential-harvesting awareness exercises against your own Players, with content you fully control.
- Reuse a single Landing Page or Email Template across many Scenarios and Simulations instead of rebuilding the content each time.
- Measure the human response per recipient: email opened, link followed, data submitted.
- Turn submitted credentials into Findings that feed Dashboards, reporting, and Inject chaining.

!!! warning

    Phishing is intended for authorized security awareness exercises against your own users only. You are responsible for obtaining the proper authorization before running any exercise.

## How it works

A phishing exercise is a regular Inject. The only unusual part is where the action comes from: instead of picking a shared static action, you pick the action that your Landing Page generated.

1. You create a **Landing Page**, the page the recipient sees after clicking the link. It appears as an action in the [Threat Arsenal](../../threat-arsenals/threat-arsenals.md), tagged with the *Email infiltration* [domain](../../threat-arsenals/domains.md) and the MITRE ATT&CK techniques `T1566.002` (Spearphishing Link) and `T1598.003` (Spearphishing Link, reconnaissance).
2. You create one or more **Email Templates**, the lure emails. Every Email Template is offered as a choice on every Landing Page action.
3. You create an **Inject** on that action, target Teams, and pick the Email Template.
4. On execution, each recipient receives their own lure email carrying a unique, opaque tracking link.
5. Open, click, and submit events are recorded per recipient, score the Inject expectations, and, when capture is enabled, produce a Credentials Finding.

## Components and settings

| Where | What you manage |
|---|---|
| **Components > Phishing > Pages** | [Landing Pages](landing-pages.md): the page recipients land on, its content, and its capture settings. |
| **Components > Phishing > Emails** | [Email Templates](email-templates.md): the lure email subject, body, sender identity, and tracking pixel. |
| **Settings > Customization > Custom domains** | [Custom domains](custom-domains.md): hostnames you own, used to serve landing pages under your own branding. |

## Default content

On first startup, the platform is seeded with a default Landing Page (*Default login page*, a credential-capture sign-in form) and a default Email Template (*Default lure email*). You can run an exercise immediately, and you can edit or delete both like any other Component.

## Permissions

Phishing content is gated on its own capabilities, in the **Content** capability group:

| Capability | What it allows |
|---|---|
| `Access phishing` | Read and search Landing Pages and Email Templates. |
| `Manage phishing` | Create, update, and duplicate them. Requires *Access phishing*. |
| `Delete phishing` | Delete them. Requires *Manage phishing*. |

Custom domains are not covered by these capabilities: they are a Tenant-level customization and reuse *Manage tenant settings*, like the other Tenant customizations. See [Users and RBAC](../../../../administration/users-and-rbac.md).

## Editions

The Phishing feature itself is available in the Community Edition. Only the **Generate with AI** helper in the Landing Page and Email Template editors requires the Enterprise Edition with XTM One configured.

!!! tip "Enterprise Edition"

    See [XTM Suite connector](../../../evaluate/xtm-suite-connector.md) to configure XTM One and enable AI generation.

## What's next?

- [Landing pages](landing-pages.md) -- Author the page recipients land on and choose what gets captured
- [Email templates](email-templates.md) -- Author the lure email and its tracking
- [Run a phishing exercise](run-a-campaign.md) -- Build and execute the Inject
- [Results and scoring](results.md) -- Read the expectations, the forensic evidence, and the Findings
- [Custom domains](custom-domains.md) -- Serve landing pages from a hostname you own
