# Domains

Domains classify Threat Arsenal Actions by the type of security control they target. Each Domain represents a defensive capability being evaluated (endpoint protection, network security, email filtering, etc.), giving operational context to Inject executions and Simulation results.

Domains are predefined by the platform and assigned automatically based on the Threat Arsenal Action. They cannot be created or modified by users.

## Available Domains

| Domain | Description |
|---|---|
| Endpoint | Endpoint security controls: EDR (Endpoint Detection and Response), SIEM (Security Information and Event Management) |
| Data exfiltration | Data exfiltration detection and blocking: DLP (Data Loss Prevention), SIEM |
| URL filtering | Web access control and URL categorization: proxy, ZTNA (Zero Trust Network Access) |
| Table-top | Process-oriented exercises involving manual decision-making or coordinated response |
| Cloud | Cloud-native security controls: IAM (Identity and Access Management), SIEM |
| Network | Network security: segmentation, IDS/IPS (Intrusion Detection/Prevention System), firewalls, SIEM |
| Email infiltration | Email flow protection: phishing detection, malicious attachment filtering, antispam, antispoofing |
| Web app | Web application security: vulnerability detection, WAF (Web Application Firewall), SIEM |

## How Domains are applied

Domains are primarily defined at the Threat Arsenal Action level. Each Action declares one or more Domains that describe the security control category involved.

When an Injector Contract also carries a Domain, the Threat Arsenal Action's Domain takes precedence. This ensures the Domain reflects the actual technical behavior of the executed Action.

## Domains from Injectors and Collectors

Some Injectors and Collectors define their own Domains. The Domain is attached to the Injector Contracts or Threat Arsenal Actions they produce, without requiring manual configuration.

Injectors and Collectors are updated weekly. During updates:

- Missing Domains are re-added to match the latest Injector or Collector definition
- Domains manually added by users are preserved

## Security coverage visualization

The Security Coverage Dashboard widget uses Domains to display a high-level view of your security posture. Each Domain appears as an icon with sub-domains (Prevention, Detection, Vulnerability, Table-Top) colored by the success rate of their associated Expectations.

The main Domain icon reflects the aggregated state using a worst-case rule: if any sub-domain shows weak coverage, the overall Domain status reflects this.

## What's next?

- [Threat Arsenal](threat-arsenals.md) -- Manage Actions and their Domains
- [Custom Dashboards](../../evaluate/dashboards/custom-dashboards.md) -- Build Dashboards with the Security Coverage widget
