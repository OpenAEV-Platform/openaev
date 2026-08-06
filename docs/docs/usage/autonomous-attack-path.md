# Autonomous attack path (AI penetration testing)

An **Autonomous attack path** is an AI-driven penetration test: you give the platform an objective in plain
language, and an autonomous AI orchestrator plans, executes, and adapts a full attack path against your authorized
environment on its own. It reasons about what to do next, launches the relevant Injects, reads the results, pivots,
and keeps going until the objective is reached or it needs your input - all animated live on an attack-path graph
and a decision timeline.

This is the highest level of automation in OpenAEV. It builds directly on **chained Scenarios**: a chained Scenario is
a Scenario whose Injects are linked into an attack-path workflow (see **inject chaining**). **Autonomy is not a separate
kind of Scenario - it is a way to launch a chained Scenario.** You author (or AI-plan) the chained Scenario once, then
choose at launch time whether to run it yourself (normal mode) or hand it to the orchestrator (autonomous mode), which
seeds itself with your authored steps and then adapts, extends, and drives them live.

!!! warning

    An autonomous attack path is authorized to perform **real exploitation** against the environment you scope it to.
    Only launch it against systems you own or are explicitly authorized to test. The run is bounded by the scope,
    rate limits, and safe-mode settings you configure, and you can pause, steer, or cancel it at any time.

## Why use it?

- **Close the gap to autonomous pentest tooling**: continuous, objective-driven testing without writing a Scenario.
- **Find real attack paths**: the AI chains techniques the way an attacker would, instead of running a fixed list.
- **Advice on missing capabilities**: when a technique needs an injector or contract you have not installed, the run
  records a **capability gap** and tells you which marketplace connector to install to close it.
- **Proof of exploitation**: every confirmed compromise is captured as a case-file fragment you can export as a
  report.

## Prerequisites

| Requirement | Why |
|---|---|
| **Enterprise Edition** | Autonomous attack path is an AI feature and, like every other AI capability in OpenAEV, is Enterprise Edition only. |
| **`AUTONOMOUS_ATTACK_PATH` feature enabled** | The feature is gated behind a preview flag. It transitively relies on `ATTACK_PATH` and `INJECT_CHAINING`; once those ship enabled, `AUTONOMOUS_ATTACK_PATH` is the single operator-visible gate. |
| **A registered XTM One platform** | The autonomous orchestrator (the AI "brain") runs in XTM One. OpenAEV is the execution and visualization substrate; it must be connected to an XTM One instance. |
| **Installed injectors / collectors** | The AI can only execute techniques your installed arsenal supports. Gaps are surfaced as advice, not silent failures. |

See [Enterprise editions](../administration/enterprise.md) and [Parameters](../administration/parameters.md) for how
to enable the license and preview features.

## How it works

An autonomous attack path splits responsibilities across the two platforms:

1. **OpenAEV** stores the run, executes the Injects it is told to run through a chained Simulation, enforces scope and
   isolation, and renders the live UI (attack-path graph + decision timeline).
2. **XTM One** hosts the autonomous orchestrator - a multi-agent system that plans the attack, decides the next
   action, calls back into OpenAEV to record what it is doing, and reads your steering directives.

The two sides talk over a small callback API. The orchestrator streams its reasoning and decisions back as
**timeline events**, updates the **run status**, consumes your **steering directives**, and resolves **capabilities**
against your installed arsenal.

### The decision timeline

Everything the AI does is streamed to a timeline next to the animated graph. Each entry has a type:

| Event | Meaning |
|---|---|
| **Narration** | Free-form reasoning streamed from the orchestrator as it thinks. |
| **Decision** | A concrete choice the AI made (pick a technique, a target, a pivot). |
| **Tool action** | A call into OpenAEV (recon, launch an Inject, read scope). |
| **Handover** | A switch between orchestrator sub-agents (recon -> exploit -> lateral movement...). |
| **Gap** | A capability gap: no installed injector or contract can perform a needed technique. |
| **Status** | A run-status transition. |
| **Directive** | A steering directive you sent that the run consumed. |
| **Question** | A question the AI raised to you when it is blocked (human-in-the-loop). |
| **Proof** | A proof-of-exploitation case-file fragment for the final report. |

### Run lifecycle

A run moves through these states:

| Status | Meaning |
|---|---|
| **Created** | The run exists but has not been handed to the orchestrator yet. |
| **Running** | The orchestrator is actively planning and executing. |
| **Paused** | You paused the run; the underlying chained Simulation is paused too. |
| **Waiting input** | The AI is blocked and asked you a question. It resumes once you answer. |
| **Completed** | The objective was reached, or the AI decided to stop successfully. |
| **Failed** | The run ended with an error. |
| **Canceled** | You stopped the run. |

## Build a chained Scenario

An autonomous attack path always runs from a **chained Scenario**, so you build (or plan) that Scenario first. There
are two ways to get one:

- **Author it manually**: create a Scenario, add Injects, and link them into an attack-path workflow with inject
  chaining - the classic hand-authored path.
- **Plan it with AI**: on a chained Scenario, use **Plan with AI**. The orchestrator designs the attack path -
  scoping the perimeter and authoring the steps (recon, exploitation, lateral movement, objective) - and writes them
  **directly onto the Scenario's workflow template**. Nothing is executed and no Simulation is created; you get a
  reusable Scenario you can review, edit, and launch whenever you like.

!!! note

    **Plan with AI** is design-time only. It produces a reusable Scenario, not a run. Execution is a separate,
    explicit step (see below), so you can plan once and launch many times.

## Launch modes: normal vs autonomous

Once you have a chained Scenario, the **Launch** action lets you choose how to run it:

1. **Launch a simulation (normal mode)**: starts a standard, operator-driven Simulation from the Scenario, exactly
   like any chained Scenario. You can launch as many Simulations as you like over time.
2. **Launch in autonomous mode**: hands the Scenario to the orchestrator. It **seeds itself with the Scenario's
   authored steps** as the starting attack path, then verifies, executes, and adapts/extends the path live from what
   it finds - the AI-driven pentest experience. Describe or confirm the **objective**, then confirm the **scope**,
   **rate limits**, and **safe mode**. The run starts in **Created**, then moves to **Running** as the orchestrator
   engages.

An autonomous run owns exactly one Simulation, kept fully read-only and in sync. You drive and observe it from the
**Simulation** detail page: the AI overview, the live attack map, and an always-open reasoning panel on the right,
with pause / resume / stop controls in the header. Manual configuration (scope, logic, manual launch) is not exposed
while the run is autonomous - the AI owns it.

## Relaunch a finished autonomous run

Because the attack path lives on the Scenario (not the run), a completed autonomous run is fully repeatable. From the
Scenario you can:

- **Launch a simulation (normal mode)** to replay the same path yourself, or
- **Launch in autonomous mode** again to let the orchestrator drive a fresh run from the same authored steps.

### Objectives and scope modes

Every objective has a **scope mode** that tells the AI whether it needs a specific target:

- **Environment** objectives operate over the whole authorized environment (for example *"find and prove any path to
  domain admin"*). The AI does not need you to pick a target - it discovers its own.
- **Target** objectives need a specific target the AI must focus on (for example *"exploit this web application"*).
  If the objective needs a target and you did not provide one, the AI raises a **Question** and waits for you rather
  than guessing.

!!! note

    Scope modes apply the same way whether you plan the Scenario with AI or launch it in autonomous mode: an
    *environment* objective lets the orchestrator discover its own targets, while a *target* objective without a
    target makes it raise a **Question** and wait rather than guess.

## Steer a running attack path

You do not have to stop a run to change its direction. Steering is a first-class, real-time capability.

- **Send a directive**: type an instruction ("focus on the finance subnet", "avoid the production database",
  "try phishing the helpdesk") and it is queued for the run. The orchestrator consumes pending directives on its
  next decision cycle and records a **Directive** timeline event when it applies one - without stopping.
- **Edit live configuration**: adjust scope, rate limits, or safe mode on the fly. The change is applied to the live
  run and its chained Simulation without a restart.
- **Answer questions**: when the AI is in **Waiting input**, answer its question to unblock it.
- **Pause / resume / cancel**: full lifecycle control over the run and its underlying Simulation.

## Capability gaps and advice

The AI can only run techniques your installed arsenal supports. When a needed technique has no matching injector or
contract, the run does not fail silently: it records a **capability gap** and, where possible, tells you which
marketplace connector to install to close it. The run continues with the paths it can still execute. This turns
missing capabilities into actionable advice for hardening your testing coverage.

## Proof of exploitation

Every confirmed compromise is captured as a **Proof** event with its supporting evidence. The run view aggregates
these into a **proof-of-exploitation** panel - a case file of what was actually proven, not just attempted. You can
**export the report** as Markdown for sharing or archiving.

## Long-running actions

Some techniques take a long time to resolve - a phishing Inject may wait hours or days for a click, a scheduled task
may fire later. The orchestrator does not block on these. It records the action, moves on to other productive paths,
and re-evaluates on its decision cycle: when a long-running expectation finally resolves, the AI folds the outcome
back into its plan. This keeps a run progressing instead of stalling on a single slow step.

## What's next?

- Inject chaining and transfer - the conditional-execution engine autonomous runs build on.
- [Scenarios and Simulations](foundations/scenarios-and-simulations.md) - the hand-authored counterpart to autonomous runs.
- [Threat Arsenal](build/threat-arsenals/threat-arsenals.md) - the injectors and contracts the AI draws from.
- [Enterprise editions](../administration/enterprise.md) - enabling the license required for AI features.
