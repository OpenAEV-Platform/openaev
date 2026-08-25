# Brainstorming

Working design documents: the reasoning *behind* a decision, while it is still being made.

## What belongs here

Exploratory and in-flight design material for a feature that is not yet built — option comparisons,
schema spikes, risk lists, open questions, delivery plans. These documents are **living**: they get
reversed, re-scoped and rewritten as the work teaches us things.

## What does not

| Kind | Home | Why |
| --- | --- | --- |
| The **decision** — options weighed, choice made, consequences accepted | [`adr/`](../adr/) | Short, reviewed, numbered, dated. Stable enough to cite in two years |
| **How to work with a shipped mechanism** | [`docs/docs/development/`](../docs/docs/development/) | Published to docs.openaev.io. Documents what exists, not what we intend |
| Reusable **agent prompts** | [`.github/prompts/`](../.github/prompts/) | Invoked as slash commands — a different kind of artifact entirely |

The distinction that matters: an ADR is *reviewed and stable*, a brainstorming doc is *honest and
current*. When the two disagree, the ADR is the one that was reviewed.

A feature typically produces one ADR and one folder here. Once it ships, the folder can be pruned and its
durable content promoted to `docs/docs/development/`.

## Current

| Folder | Feature | Decision |
| --- | --- | --- |
| [`marking/`](./marking/) | Marking-based access control (STIX TLP/PAP) for assets | [ADR-007](../adr/ADR-007-Marking-based-access-control.md) — Proposed |
