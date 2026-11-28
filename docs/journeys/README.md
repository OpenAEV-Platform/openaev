# User journeys

A user journey is a task a user does from start to end in OpenAEV, such as running a first atomic test on an endpoint. Each journey lists its steps, the expected result and the documentation pages it relies on.

Journeys are used three ways:

- **Documentation**: an agent follows the journey in the docs and checks each step against the code, then opens a small PR when a page is wrong or missing.
- **Non-regression**: the steps and the expected result are checked before a release. P0 journeys are candidates for Playwright E2E tests.
- **Dogfooding**: the journey is played in the app to find bugs and friction.

## Format

One file per journey, `UJ-<number>-<slug>.md`, with this front matter:

```yaml
---
id: UJ-001
title: Short task name
status: draft        # draft | validated | needs-update
owner:               # GitHub handle of the PO/PM who validates it
persona: [SOC analyst]
priority: P0         # P0 | P1 | P2
edition: [CE]        # CE | EE
doc_pages:           # paths under docs/docs
  - usage/run-and-evaluate/atomic-testing/atomic-testing.md
last_checked:        # date of the last check against the docs
---
```

The body follows the same order every time: overview, prerequisites, steps, expected result, doc gaps, completion criteria.

## Rules

- A PO or PM writes or validates the journey; `status: validated` is set in a PR they approve.
- Agents only work on validated journeys.
- When a release changes a journey, set `status: needs-update` and say why in the PR.
- `python docs/scripts/docs-freshness.py` lists the journeys and the doc pages they rely on that still need a check.
