## Expectations — Validation, Aggregation & Propagation

This page explains **how OpenAEV computes expectation results**, from individual signals to organizational outcomes.

## Manual Expectations (Detailed)

A **Manual Expectation** requires validation by the **organizer**.

OpenAEV does not infer the result — a human explicitly decides whether the expectation is met.

### When should I use it?

Use manual expectations to evaluate **human-driven actions** that cannot be detected technically:

- decision-making
- communication
- process adherence

### How does it work?

1. Add a **Manual Expectation** to an Inject.
   ![Create manual expectation](assets/create_manual_expectation.png)
2. Define: score, validation mode, expiration time
3. During the simulation, manually validate the expectation from the UI.
   ![Validate a manual expectation from the animation tab](assets/manual_expectation_validation_animation_tab.png)

### Example

You simulate a phishing campaign.

Expectation:

> *The Incident Response team acknowledges and escalates the phishing email.*

- The SOC receives the inject
- The analyst follows the playbook and opens a ticket
- The organizer validates the expectation

✅ Result: **Success (100)**

## Automatic Expectations (Detailed)

An **Automatic Expectation** is validated when a **technical condition** is met.

Validation relies on:

- connected security integrations
- or manually provided technical results

### Why use it?

Automatic expectations allow you to:

- Measure **real security controls**
- Avoid subjective validation
- Scale exercises across many Assets and Agents

They are essential for **technical simulations** and **Breach & Attack Simulation** use cases.

### Available automatic expectation types

| Type          | What it validates                       |
|---------------|-----------------------------------------|
| Prevention    | A control blocks the attack             |
| Detection     | A security alert or incident is raised  |
| Vulnerability | A CVE is present on the target          |
| Article       | Targets read or acknowledge the article |

![Add expectations to an inject](assets/inject_expectations_list.png)

### Example

You launch a malware simulation.

Expectation:

> *Endpoint protection detects the payload.*

- The EDR raises an alert
- OpenAEV receives the detection event

✅ Result: **Success (100)**

## Validation Modes (Detailed)

A **Validation Mode** defines how individual target results are **aggregated at group level**.

Each expectation uses **exactly one** validation mode.

### All targets must validate

#### When should I use it?

Use this mode when **every member must succeed**, such as:

- mandatory training
- compliance requirements
- baseline security checks

#### How does it work?

- All targets succeed → **100**
- At least one target fails → **0**

#### Example

Group of 4 players:

- 3 succeed, 1 fails → **0 (Failed)**
- 4 succeed → **100 (Success)**

### At least one target must validate

#### When should I use it?

Use this mode when **a single successful response is enough**, such as:

- SOC detection
- on-call escalation
- redundancy testing

#### How does it work?

- ≥ 1 success → **100**
- No success → **0**

#### Example

Group of 4 players:

- 1 succeeds, 3 fail → **100 (Success)**
- 4 fail → **0 (Failed)**

## Result Aggregation per Expectation

### What is this?

A single **Expectation** can receive **multiple validation results** for the same Inject.

This happens when:

- several security tools monitor the same Asset
- multiple collectors report results
- results are added both manually and automatically

![Add the validation of a technical expectation in atomic testing](assets/add_technical_expectation_validation.png)

### Why does OpenAEV work this way?

OpenAEV evaluates **effective protection**, not individual tool accuracy.

If at least one control detects or prevents the attack, the organization **did not fully fail**.

This avoids:

- false negatives caused by tool overlap
- penalizing layered defense strategies

### How is the final result computed?

1. All results are collected
2. Results are ordered by **severity**
3. The **highest result always wins**

⚠️ A negative result never overrides a positive one.

### Example

| Tool               | Result       |
|--------------------|--------------|
| Microsoft Defender | Detected     |
| CrowdStrike        | Not Detected |

Final expectation result:  
✅ **Detected**

## Status Propagation

Expectation results propagate from **technical entities** to **organizational entities**.

| Entity      | Rule                                 |
|-------------|--------------------------------------|
| Agent       | Direct result (0 or 100)              |
| Asset       | Valid only if **all Agents succeed**  |
| Asset Group | Depends on validation mode            |
| Player      | Depends on validation mode            |
| Team        | Aggregated from Players               |

⚠️ Validation mode always applies at **group level**.