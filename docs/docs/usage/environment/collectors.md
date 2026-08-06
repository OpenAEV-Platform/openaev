# Collectors

!!! tip "Collectors list"

    You are looking for the available collectors? The list is in the [OpenAEV Ecosystem](https://filigran.notion.site/OpenAEV-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Introduction

Collectors are one of the cornerstones of the OpenAEV platform, they are responsible for pulling data from various
external services for two purposes:

- Collect all alerts, logs and traces related to attacks, incidents or crisis and match them to simulated injects to
  evaluate the security posture.
- Collect any data that may help to schedule breach and attack simulations such as list of assets, groups, identities,
  Threat Arsenal Actions, etc.

### Detection & prevention (SIEM, XDR, EDR, NDR)

*SIEM: Security Information and Event Management, XDR: Extended Detection and Response, EDR: Endpoint Detection and Response, NDR: Network Detection and Response.*

Those collectors are the most important ones as they are used to evaluate the security posture (response to injects) from
various detection and response systems and fulfill expectations for detection and prevention.

These Collectors fetch data for 45 minutes after an Inject executes. If no data is found after 45 minutes, OpenAEV updates the Inject result to "Not detected".

#### Detection & prevention with EDR

The platform analyzes EDR logs to identify matches for the hostname and the parent process name associated with
the attack. If the OpenAEV Agent initiates the attack, the parent process name follows this format:
`openaev-implant-INJECT_ID.exe`.

#### Detection & prevention with SIEM

For SIEMs, the platform relies on the upstream-deployed EDR, whose logs the SIEM collects.
If the EDR confirms a detection or prevention Expectation, the platform traces this information back in the SIEM to
validate it as well.

This means the EDR Collector must first validate the Expectation before the SIEM Collector can perform its task.

### Threat intelligence

Those collectors are used to collect threat intelligence data such as kill chains, Scenarios, TTPs (Tactics, Techniques, and Procedures), Threat Arsenal Actions, etc.

### Endpoint management

Those collectors are pulling alternative information about your endpoints and assets to complete the overview about your
current posture in terms of vulnerabilities and compliance.

### Identities

Those collectors are pulling all information related to identities, including human assets, to be used in scenario or to
complete the view overview about your current posture.

### Others

All other system OpenAEV can pull from, to add more meaningful and relevant information to the view of your security
posture.
