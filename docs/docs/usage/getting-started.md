# Getting started

OpenAEV allows you to validate your security posture by simulating real-world adversary techniques.  
It has been designed as part of the Filigran XTM suite and can be integrated
with [OpenCTI](https://filigran.io/solutions/open-cti/) to generate meaningful attack scenarios based on real threats.

This guide introduces the **key concepts** and **workflows** behind the platform.

---

## What you can do with OpenAEV

Some typical use cases include:

- Designing attack scenarios based on real threats
- Evaluating your security posture against technical simulations on endpoints
- Enhancing team skills during exercises and simulations
- Organizing Capture The Flag events with multiple challenges
- Conducting atomic testing on assets

---

## Players & Teams

Before running a simulation, define **who will participate**.

- [Players](people.md#players) represent humans or roles (SOC analyst, sysadmin, end-user).
- [Teams](people.md#teams) group players into units (SOC, IT Ops, HR).

Creating players and teams lets you measure not only **technical outcomes** but also the **human response**: who reports
an alert, who escalates, who reacts according to playbooks.

## Agents & Assets

[Assets](assets.md) are the systems you want to test: workstations, servers, VMs, or logical groups.

You can:

- Deploy an **OpenAEV agent** for agent-based testing (executes payloads, reports telemetry, supports automated checks)
- Use **agentless endpoints** when software installation is not possible

Assets are reused across scenarios and simulations — it’s worth naming and tagging them carefully (OS, owner,
environment).

## Payloads & Injects

[Payloads](payloads/payloads.md) are the technical actions: running a command, scanning a network, or checking for a
vulnerability.

[Injects](inject-overview.md) wrap payloads with context:

- *who* is the target
- *when* it should run
- *what* is expected in return

OpenAEV includes collectors with ready-to-use payloads: OpenAEV curated payloads and Atomic Red Team.

## Scenarios & Simulations

A [scenario](scenario.md) is a blueprint: a sequence of injects that tell the story of an attack.

You can:

- Import pre-built scenarios from the **XTM Hub**
- Create your own from scratch

Once defined, a scenario can be turned into a [simulation](simulation.md): a live execution in your environment, either
one-shot or scheduled regularly.

During simulations, [expectations](injects_and_expectations.md) are validated:

- **Automatically**, via integrations with your stack
- **Manually**, by observers validating human reactions

## Results & Dashboards

After a simulation, results are consolidated along four axes:

- **Prevention** — were attack steps blocked?
- **Detection** — were they detected?
- **Vulnerability** — which exposures were identified?
- **Human response** — how did players/teams react?

[Dashboards](dashboards/custom-dashboards/custom-dashboards.md) let you explore these results at different levels: from
a global overview of your posture to the detailed timeline of a simulation.

---

## The Starter Pack

OpenAEV includes a **Starter Pack** to accelerate onboarding.  
It provides:

- Pre-built scenarios (tabletop, agentless, agent-based)
- Four dashboards
- Injectors (Nmap, Nuclei)
- Collectors (Atomic Red Team, MITRE ATT&CK, OpenAEV payloads, CVE/NVD feed)
- One agentless endpoint + an asset group

With the Starter Pack, you can launch a complete simulation right after installation.

---

## An end-to-end atomic example (with agent)

Let’s walk through the simplest possible set-up, using only an agent and an atomic payload.

Imagine you deployed an OpenAEV agent on a Linux endpoint named `endpoint-lin-01`.

### Step 1 — Create the payload

   ```
   echo "OpenAEV Atomic Test"
   ````

### Step 2 — Build the inject

* Create an **atomic testing** in the UI
* Use the created payload
* Target `endpoint-lin-01`

### Step 3 — Run the atomic testing

Click **Launch now**.
The platform executes the payload via the agent.
The result should appear in the atomic testing overview.

---

## Next steps

* Create custom injects and payloads
* Import threat-informed scenarios from the XTM Hub
* Connect with [OpenCTI](https://filigran.io/solutions/open-cti/)
* Track improvements over time in dashboards

OpenAEV is more than running tests — it is about **continuously validating your exposure** and transforming insights
into stronger defense.
