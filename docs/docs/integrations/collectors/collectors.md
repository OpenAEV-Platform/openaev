# Collectors

Collectors pull data from external services for two purposes:

- Match alerts, logs and traces with the Injects of a Simulation, to check what your security tools detected and prevented.
- Import data that helps build Simulations, such as Assets, identities or Threat Arsenal Actions.

To see the Collectors you can deploy, open **Integrations** and the **Available** tab, or browse the [collectors repository](https://github.com/OpenAEV-Platform/collectors).

### Detection & prevention (SIEM, XDR, EDR, NDR)

These Collectors connect to SIEM (Security Information and Event Management), XDR (Extended Detection and Response), EDR (Endpoint Detection and Response) and NDR (Network Detection and Response) tools. They fill the detection and prevention expectations of Injects.

If no matching data is found before the expectation expires (6 hours by default), the Inject is marked as not detected or not prevented.

#### Detection & prevention with EDR

The platform analyzes EDR logs to identify matches for the hostname and the parent process name associated with
the attack. If the OpenAEV agent runs the attack, the parent process name follows this format:
`oaev-implant-INJECT_ID-agent-AGENT_ID` (with `.exe` on Windows).

#### Detection & prevention with SIEM

For SIEMs, the platform relies on the upstream-deployed EDR, whose logs the SIEM collects.
If the EDR confirms a detection or prevention Expectation, the platform traces this information back in the SIEM to
validate it as well.

This means the EDR Collector must first validate the Expectation before the SIEM Collector can perform its task.

### Threat intelligence

These Collectors import threat intelligence data such as kill chains, Scenarios, TTPs (Tactics, Techniques, and Procedures), Threat Arsenal Actions, etc.

### Endpoint management

These Collectors import information about your endpoints and Assets, such as vulnerabilities and compliance.

### Identities

These Collectors import identities, such as users and groups, to use as Players and Teams in Scenarios.

### Others

Other Collectors import any other data that helps assess your security posture.

## What's next?

- [Deploy Collectors](deploy-collectors.md) -- Deploy and configure Collectors
- [Collector development](../../development/collectors.md) -- Build your own Collector
