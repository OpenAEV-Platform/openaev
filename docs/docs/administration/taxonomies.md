# Taxonomies

Taxonomies are the reference data used to classify platform data. Manage them in **Settings > Taxonomies**: tags,
attack patterns, kill chain phases and vulnerabilities.

## Tags

Tags categorize data, such as Assets or Teams, to make it easier to filter and search.

## Kill chain phases

Kill chain phases are used in OpenAEV to structure and analyze the data related to cyber threats and attacks. They
describe the stages of an attack from the perspective of the attacker and provide a framework for identifying, analysing
and responding to threats.

OpenAEV supports the following kill chain models:

- **MITRE ATT&CK Framework (Enterprise, PRE, Mobile and ICS (Industrial Control Systems))**

You can add, edit, or delete kill chain phases in the settings page, and assign them to attack patterns in the platform.
Additionally, you can filter data by kill chains phases, visualize relationships between kill chain phases and
Injects, Simulations or Scenarios.

## Attack patterns

Attack patterns are structured representations of the tactics, techniques, and procedures (TTPs) used by adversaries to
compromise systems. In OpenAEV, attack patterns help analyze and classify threats, providing a standardized approach to
identifying and mitigating cyber risks.

OpenAEV supports the following attack pattern models:

- **MITRE ATT&CK Framework (Enterprise, PRE, Mobile, and ICS)**

You can add, edit, or delete attack patterns in the settings page and assign them to Threat Arsenal Actions or Injectors.

## Vulnerabilities

CVEs (Common Vulnerabilities and Exposures) are standardized identifiers for publicly disclosed cybersecurity
vulnerabilities. Each CVE provides a unique reference, enabling consistent communication and tracking across tools and
Teams.

In OpenAEV, CVEs are used to associate known vulnerabilities with Assets, Threat Arsenal Actions, and Injects. This allows users to
simulate attacks based on real-world flaws, enhancing the relevance and precision of security testing.

You can add, edit, or delete CVEs.

!!! tip "Enterprise Edition"

    The **Remediation** tab of a vulnerability requires an Enterprise Edition license.

## What's next?

- [Default Asset rules](default-asset-rules.md) -- Apply Asset groups to Injects from Scenario tags
- [Parameters](parameters.md) -- Platform settings
