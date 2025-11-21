# Scenario Generation from OpenCTI Security Coverage

## Overview

OpenAEV can automatically generate scenarios from OpenCTI Security Coverage entities, transforming threat
intelligence, such as Attack Patterns, Vulnerabilities, Artifacts, and others, into actionable operational scenarios.

This integration enables organizations to track simulation results and security coverage directly within OpenCTI,
creating a complete feedback loop between threat intelligence and security validation.

This integration works across multiple OpenCTI entity types:

- Reports
- Incident Response
- Incidents
- Campaigns
- Intrusion Sets
- Groupings

## How It Works

When you click on the **Add Security Coverage** button in
OpenCTI ([see OpenCTI documentation](https://docs.opencti.io/latest/usage/security-coverage/)), OpenAEV receives a **STIX 2.1 bundle** representing the Security Coverage. 

![Button Security Coverage](assets/octi-security-coverage-button.png)

OpenAEV then:

1. Parses the STIX object
2. Builds a scenario in OpenAEV
> **Note:** At time of writing, the automated Security Coverage feature can assess coverage for the following entities: Attack Patterns
3. Extracts relevant **Attack Patterns references**
4. Generates injects for each extracted entity
5. Schedules the scenario for execution

## STIX Fields Used for Scenario Creation

OpenAEV requires specific fields from the STIX object, divided into **Mandatory** (id, name, covered_ref) and **Optional** fields (e.g., description, labels, periodicity) for additional context.

### Mandatory Parameters

| STIX Field      | STIX Path     | Description                                                    | Used For                            |
|-----------------|---------------|----------------------------------------------------------------|-------------------------------------|
| **id**          | `id`          | Unique STIX identifier of the Security Coverage                | Mapping, de-duplication, externalId |
| **name**        | `name`        | Name of the Security Coverage                                  | Scenario name in OpenAEV            |
| **covered_ref** | `covered_ref` | Reference to the main entity (intrusion-set, malware, report…) | Build external link to OpenCTI      |

### Optional Parameters

| STIX Field                 | STIX Path                 | Description                                 | Used For                       | 
|----------------------------|---------------------------|---------------------------------------------|--------------------------------|
| **description**            | `description`             | Description of the Security Coverage        | Scenario description           |
| **labels**                 | `labels[]`                | Labels/tags from OpenCTI                    | Tags in scenario and tag rules |
| **periodicity**            | `periodicity`             | Scheduling frequency (e.g., `P1D`)          | Scenario scheduling            |
| **created_at** (extension) | `extensions.*.created_at` | Creation timestamp inside OpenCTI extension | Period start date              |

## How OpenAEV Builds the Scenario

After parsing and validating the **Security Coverage STIX** object, OpenAEV follows the process below:

- **Create or update an OpenAEV scenario**

  ![Scenario](assets/scenario-openaev.png)

- **Extract all references** related to Attack Patterns.

    - For each **Object Reference** identified:

        - If the referenced **Object** already exists in OpenAEV as an **Attack Pattern** and a related [Payload](../payloads/payloads.md) is available → a **concrete inject** is created.

        - If the referenced **Object** does **not** exist in OpenAEV → a **Placeholder Inject** is created to highlight the missing element.

> **Note:** To ensure meaningful inject generation, the **Attack Patterns** in OpenCTI should be aligned with those configured in OpenAEV.

Inject creation depends on matching the **Object Reference** values between OpenCTI and OpenAEV, example:
  
  | OpenCTI Attack Pattern | Exists in OpenAEV? | Result                     |
  |------------------------|--------------------|----------------------------|
  | T1059.001              | Yes                | Concrete inject created    |
  | T1059.001              | No                 | Placeholder inject created |

![Inject Scenario](assets/inject-scenario-openaev.png)  
![Inject Placeholder Scenario](assets/inject-placeholder.png)

After the injects are generated, review and customize the Scenario to ensure it fits your organization’s specific needs. You will also need to assign appropriate **Targets** to each inject. Additionally, you can configure default **Asset Groups** for scenarios created from OpenCTI using the [Default Asset Groups](../default_asset_rules.md) page.

![Inject Asset Groups](assets/inject-asset-group.png)

- Finally, the **Scenario will execute** according to its configured schedule.

## Execution and Reporting Back to OpenCTI

Once the scenario is finalized and scheduled:

- OpenAEV executes the scenario according to the periodicity.
- After simulation, the results are compiled into new SROs.
- OpenAEV sends these results back to OpenCTI as part of automated [Enriched Security Posture Assessment](../xtm-suite-connector.md) in the same **STIX 2.1 bundle** representing the Security Coverage.
- OpenCTI displays the updated coverage assessment.

![Octi results](assets/octi-security-coverage-results.png)

This creates a complete feedback loop between threat intelligence and security validation.

## What’s next?

The next step is to expand the integration between OpenCTI and OpenAEV by incorporating additional threat intelligence data, such as Observables, Artifacts, Malware, and more.


