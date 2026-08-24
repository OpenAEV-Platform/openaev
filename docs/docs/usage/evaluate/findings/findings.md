# Findings

Findings are structured security insights automatically extracted from Inject execution results. They surface discovered vulnerabilities, exposed credentials, open ports, IP addresses, and other actionable data produced by Injectors during Simulations and Atomic Tests.

## Why use Findings?

Findings transform raw execution output into searchable, categorized technical indicators. They help you:

- **Identify exposure**: see which CVEs, open ports, and credentials were discovered across your infrastructure
- **Track remediation**: monitor whether previously detected issues reappear in subsequent Simulations
- **Correlate with Assets**: understand which endpoints are affected by each Finding
- **Prioritize action**: CVE-type Findings include CVSS scores and enrichment data from [Taxonomies](../../../administration/taxonomies.md)

## How Findings are created

Findings are created automatically during Inject execution. When an Inject produces structured output (e.g., a port scan result, a CVE detection, or extracted credentials), OpenAEV parses the output and creates one Finding per discovered indicator.

Each Finding is deduplicated by its combination of value, type, and field. If the same Finding is detected again in a later execution, the existing record is updated with a new "last seen" timestamp rather than creating a duplicate.

## Finding types

| Type | Description |
|---|---|
| CVE | Known vulnerabilities (e.g., CVE-2021-44228) with optional CVSS enrichment |
| Credentials | Extracted username/password pairs |
| IPv4 / IPv6 | Discovered IP addresses |
| Port | Open ports detected on an endpoint |
| PortScan | Structured port scan results |
| Text | Free-form textual indicators |
| Number | Numeric indicators |
| File / Share | Discovered files or network shares |
| Username | Discovered usernames and accounts |

Additional types exist for Active Directory findings (SID, delegation, Kerberoastable accounts, ASREPRoastable accounts, etc.).

## Sensitive Findings

Some Finding types carry secret material. Credentials Findings, whose value is a `username:password`
or `username:hash` pair, are flagged as **sensitive**: their value is redacted everywhere the
platform returns it (list, detail, Simulation, Scenario, Endpoint and Inject views), so
`jdoe:Sup3rS3cret` is displayed and returned by the API as `jdoe:******`.

The identity part is kept so you can still recognise which account was compromised; the secret part
is never disclosed.

!!! warning "The secret is not deleted"

    The full value is still stored in the database, because deduplication, correlation and attack
    path computation rely on it. Only its API representation is redacted: it is not possible to
    retrieve the cleartext value of a sensitive Finding through the REST API.

Sensitivity is decided per Finding type, not per Finding: Credentials is the only sensitive type
today. Findings created before the upgrade are flagged retroactively, so previously detected
credentials are redacted as well.

## Findings list

Navigate to **Findings** in the left menu to see all Findings in an aggregated view. The list groups Findings by unique value and type, merging Assets from all occurrences into a single row.

Each row displays:

| Column | Description |
|---|---|
| Type | The Finding category (CVE, Port, Credentials, etc.) |
| Value | The technical value (monospace display), redacted for sensitive Findings |
| Assets | Endpoints where the Finding was detected |
| Asset groups | Asset groups containing affected endpoints |
| First seen | When the Finding was first detected |
| Last seen | When the Finding was most recently detected (default sort) |

Use the search bar and filters to narrow results by type, date range, Assets, or Asset groups.

![Findings list](assets/findings-list.png)

## Finding detail

Click on a Finding to open its detail view. The **Overview** presents the Finding at a glance:

- **Finding type and value** with occurrence count and impacted Asset count
- **Information**: type, value, field, first seen, last seen, tags
- **Occurrences**: every Inject execution that produced this Finding, shown both as a list and as a timeline, with links to the parent Simulation and Scenario
- **Vulnerability panel**: for CVE-type Findings, a summary of the vulnerability enrichment surfaced directly in the Overview

The Overview loads a lightweight Finding summary so counts and enrichment appear without fetching every occurrence up front.

![Finding detail](assets/findings-detail.png)

### CVE enrichment

For CVE-type Findings, additional tabs appear:

- **General tab**: vulnerability description, CVSS v3.1 score, CISA exploitability data (KEV catalog), CWE classifications, and reference URLs. This data is sourced from the [Taxonomies](../../../administration/taxonomies.md) configured on the platform.

![CVE Finding detail with enrichment](assets/findings-detail-cve.png)

!!! tip "Enterprise Edition"

    The **Remediation tab** displays actionable remediation recommendations for CVE-type Findings. This tab is available with a valid Enterprise Edition license.

## Where Findings appear

Findings are accessible from multiple locations in the platform:

| Location | Description |
|---|---|
| **Findings** (left menu) | Global aggregated view across all Simulations |
| **Simulation detail** | Findings produced by Injects in that Simulation |
| **Scenario detail** | Findings aggregated across all Simulations of the Scenario |
| **Inject execution results** | Findings produced by a specific Inject |
| **Endpoint detail** | All Findings linked to a specific Asset |

## What's next?

- [Inject results](../injects/inject-result.md) -- Understand Inject execution results
- [Assets](../../build/assets.md) -- Manage endpoints and Asset groups
- [Taxonomies](../../../administration/taxonomies.md) -- Configure CVE and attack pattern data
- [Reporting](../reporting/reporting.md) -- Generate reports that include Findings
