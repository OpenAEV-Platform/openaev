# Findings

Findings provide insights into discovered security weaknesses, misconfigurations, or vulnerabilities that could be
exploited.

They are generated from the injector's structured output, which can take multiple forms, including IPv4, Text, IPv6,
Port, PortScan (object), and Credentials (object).

Each finding is associated with an [Inject](injects.md) and an [Asset (endpoint)](assets.md).

Findings can be accessed at various levels across the platform. In the Findings view, only aggregated values are
initially displayed to provide a high-level overview.

When a specific finding is clicked, a drawer opens to reveal more detailed information. This includes:
* The associated inject(s) where the finding was discovered
* For CVE-type findings, enriched data previously sourced from [taxonomies](../administration/taxonomies.md) is available, such as:
  - General information about the CVE 
  - A Remediation tab with actionable recommendations (EE)

- Global level
  ![Global](assets/findings-global-view.png)
  ![Non-cve](assets/findings-drawer-non-cve.png)
  ![Cve](assets/findings-drawer-cve-general.png)
  ![Related-injects](assets/findings-drawer-related.png)
  ![Remediation](assets/findings-drawer-remediation.png)
- Scenario level  
  ![Scenario](assets/findings-scenarios-view.png)
- Simulation level  
  ![Simulation](assets/findings-simulation-view.png)
- Inject level   
  ![Inject](assets/findings-inject-view.png)
- Atomic level
  ![Atomic](assets/findings-atomic-view.png)
- Endpoint level   
  ![Endpoint](assets/findings-endpoint-view.png) 


