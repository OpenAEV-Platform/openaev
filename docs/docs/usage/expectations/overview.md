# Expectations

This page explains how Expectations work in OpenAEV: what they are, why they matter, how to configure them, and how they
impact the outcome of a simulation.

Expectations define what success means when an Inject targets an [Asset (endpoint)](../assets.md) or
a [Player](../people.md#players) during an [Inject](../inject-overview.md) execution.

## What are Expectations?

### What is this?

An **Expectation** defines a **measurable outcome** for an Inject.
It answers the question:

> *What should happen for this Inject to be considered successful?*

Each expectation:

* is attached to an Inject
* has a **score**
* has a **validation mode**
* is validated either **manually** or **automatically**
* contributes to the **simulation result**

### Why use Expectations?

Expectations allow you to:

* Objectively measure **security posture**
* Evaluate both **technical controls** and **human behavior**
* Standardize scoring across exercises
* Decide when a **simulation is complete**

## Expectation Types (Overview)

Expectations fall into **two categories**, depending on how validation occurs.

### Manual Expectations (Human validation)

Manual expectations are validated by an **organizer**.
They are used to evaluate **human actions** such as decision-making or escalation.

⚠️ These expectations can only be used in the context of human-based injects, such as email or SMS, where a real person
is expected to read, interpret, and react to inject.

Typical use cases:

* tabletop exercises
* incident response drills
* awareness and training scenarios

### Automatic Expectations (Technical validation)

Automatic expectations are validated by **technical signals**.

They rely on:

* connected security tools
* or manually provided technical results

⚠️ These expectations can only be used in the context of technical injects, where system or security signals are
produced automatically (for example: logs, alerts, detections, or telemetry).

They are essential for **technical simulations** and **Breach & Attack Simulation** use cases.

## Validation Modes (Overview)

A **Validation Mode** defines how individual target results aggregate at **group level**.

Each expectation uses **exactly one** validation mode.

### All targets must validate

* All targets succeed → **100**
* At least one target fails → **0**

Typical use cases:

* compliance checks
* mandatory training
* baseline security requirements

### At least one target must validate

* ≥ 1 success → **100**
* No success → **0**

Typical use cases:

* SOC detection
* escalation workflows
* redundancy testing

![Validation mode](assets/validation_mode.png)

## When is a Simulation Finished?

A simulation is considered **finished** once **all expectations are resolved**:

* Technical expectations → detection, prevention, vulnerability
* Human expectations → manual validation

Until then, the simulation remains **in progress**.

## Go further

* [Validation, aggregation & propagation](validation.md)
* [Scoring, expiration & UI](management.md)