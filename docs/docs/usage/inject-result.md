# Inject result

### Overview

The first screen displayed when you click on a specific inject executed (Atomic testing or Simulation)  is a breakdown of your security
posture against this test.

Results are broken down into:

- Prevention: the ability of your security posture to prevent the inject
- Detection: the ability of your security posture to detect the inject
- Human response: the ability of your security teams to react as intented facing the inject
- Vulnerability: the ability of your security posture to detect common vulnerabilities and exposures (CVEs)

At the top, big metrics summarize how all targets performed. On the left, a list of targets lets you quickly check
results for each one. When you select a target, the right side shows a timeline of the test and its results, including
execution logs.

![Atomic testing Overview with Results](assets/atomic_testing_overview.png)
![Atomic testing Overview with Results](assets/atomic_testing_overview_expectations.png)

### Findings

The Findings screen displays what was detected during the inject, based on the output parser in the payload. You can
filter findings by name, type, creation date, target, value, or tag.

![Atomic testing Overview with Results](assets/atomic_testing_findings.png)

### Execution details

This screen shows the full trace of the inject’s execution, including logs and status information.

![Execution trace of a successfull atomic testing](assets/atomic_testing_execution_details.png)

### Payload info

This screen is available for technical injects only. You can see the details of the payload related to the test.

![Payload info of atomic testing](assets/atomic_testing_payload_info.png)

### Remediations (EE)

This screen is available for technical injects only. It displays remediation content related to the executed payload,
specifically focused on detection logic. You will see one Remediation tab per collector available in the platform.

Ariane can generate AI‑based rules from an executed inject with the following:

- Payload types: Command, DnsResolution
- Collectors: Splunk, CrowdStrike

Remediation statuses:
- No remediation:
  ![Detection Remediations-no-present](assets/atomic_testing_detection_remediation_no_present.png)

- No remediation and Ariane not available:
  ![Detection Remediations-no-present-ariane-not-available](assets/atomic_testing_detection_remediation_no_present_use_ariane_not_available.png)

- Remediation written by a human:
  ![Detection Remediations-human](assets/atomic_testing_detection_remediation_human.png)

- Remediation generate with Ariane
  ![Detection Remediations-ariane](assets/atomic_testing_detection_remediation_use_ariane.png)

- Remediation outdated
  ![Detection Remediations-outdated](assets/atomic_testing_detection_remediation_outdated.png)
