# Collectors

!!! question "Collectors list"

    You are looking for the available collectors? The list is in the [OpenBAS Ecosystem](https://filigran.notion.site/OpenBAS-Ecosystem-30d8eb73d7d04611843e758ddef8941b).

## Introduction

Collectors are one of the cornerstones of the OpenBAS platform, they are responsible for pulling data from various
external services for two purposes:

- Collect all alerts, logs and traces related to attacks, incidents or crisis and match them to simulated injects to
  evaluate the security posture.
- Collect any data that may help to schedule breach and attack simulations such as list of assets, groups, identities,
  payloads, etc.

### 🛡️ Detection & Prevention (SIEM, XDR, EDR, NDR)

Those collectors are the most important ones as they are used to evaluate the security posture (response to injects) from
various detection and response systems and fulfill expectations for detection and prevention.

These collectors will try to fetch data for 45 minutes after the execution of an inject. At the end of
these 45 minutes, if no data has been found for a given inject, this inject's result will be updated to "Not detected".

#### Detection & Prevention with EDR

For EDRs, we analyze the tool's logs to identify matches for the hostname and the parent process name associated with
the attack. If the attack is initiated by the OpenBAS agent, the parent process name will follow this format:
openbas-implant-INJECT_ID.exe.

#### Detection & Prevention with SIEM

For SIEMs, we rely on the upstream-deployed EDR, whose logs are collected by the SIEM.  
If the EDR confirms an expectation of type detection or prevention, we trace this information back in the SIEM to
validate it as well.

This means that within the workflow, the EDR collector must first validate the expectation before the SIEM collector can
perform its task.

### 🧬 Threat Intelligence

Those collectors are used to collect threat intelligence data such as kill chains, scenarios, TTPs, payloads, etc.

### 📺 Endpoint management

Those collectors are pulling alternative information about your endpoints and assets to complete the overview about your
current posture in terms of vulnerabilities and compliance.

### 🎭 Identities

Those collectors are pulling all information related to identities, including human assets, to be used in scenario or to
complete the view overview about your current posture.

### 🔭 Others

All other system OpenBAS can pull from, to add more meaningful and relevant information to the view of your security
posture.
