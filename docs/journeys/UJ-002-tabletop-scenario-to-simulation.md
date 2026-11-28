---
id: UJ-002
title: Build a tabletop scenario and play it as a simulation
status: draft
owner:
persona: [Crisis manager, Purple leader]
priority: P0
edition: [CE]
doc_pages:
  - usage/build/scenario/scenario.md
  - usage/build/people.md
  - usage/build/components/variables.md
  - usage/build/components/challenges.md
  - usage/attack-chaining/inject-chaining.md
  - usage/run-and-evaluate/simulation/simulation.md
  - usage/run-and-evaluate/expectations/expectations.md
  - usage/build/components/lessons.md
  - usage/run-and-evaluate/reporting/reporting.md
last_checked:
---

## Overview

- **What**: orchestrate a full tabletop exercise: build the scenario, play it, debrief.
- **Who**: crisis manager or purple leader preparing an exercise.
- **Goal**: a simulation that runs on its own and a report to share.

## Prerequisites

- A Team with at least one active Player (without Players, no expectation is created).
- SMTP configured if email Injects are used.

## Steps

1. Create a time-based Scenario: name, severity, tags, email sender, lessons learned enabled.
2. In **Configuration**, add a Team and a variable.
3. Add at least three Injects on the timeline: an Email to the Team using the variable, a manual Inject, and a challenge or media pressure.
4. Make one Inject depend on another.
5. Launch the simulation and follow the timeline.
6. Check delivered emails in **Mails** and validate manual expectations in **Validations**.
7. Send the lessons learned questionnaire and generate the report.

## Expected result

All Injects executed in order, variables resolved in the email subject and body, expectations validated, report downloadable.

## Doc gaps

- The simulation page lists Execution tabs that no longer exist.
- Inject chaining does not say whether a child waits for a manual expectation of its parent.
- Variables are only replaced in Email and SMS Injects.
- Report generation is barely described.

## Completion criteria

A new user completes the steps from the docs only.
