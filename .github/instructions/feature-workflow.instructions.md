---
applyTo: "specs/**"
description: "End-to-end feature workflow: hi-fi mockup handoff, five-gate spec board with blocker loop, implementation, five-gate delivery review board with CVSS protocol"
---

# Feature Workflow

The end-to-end path for building a feature. It grafts onto the spec-driven
skeleton (`/specify` → `/plan` → `/tasks` → `/implement`); `/feature` orchestrates
all phases, `/mockup` runs phase 0 alone.

```
Phase 0  /mockup     Hi-fi HTML mockup, interview-style, iterated with the user.
                     Validated mockup = the handoff for the spec.
Phase 1  /specify    Spec package, gated by the five-gate SPEC BOARD.
         /plan       Any gate may raise BLOCKERs → fix upstream → re-gate,
         /tasks      loop until all five record GO.
Phase 2  /implement  Execute tasks.md (unchanged core procedure).
Phase 3  (end of     DELIVERY REVIEW BOARD: the same five gates, review mode.
         /implement) Security gate applies the CVSS protocol below.
```

## Artifacts (all under `specs/NNN-slug/`)

| File | Written by | Purpose |
|---|---|---|
| `mockup/*.html` | mockup-generator | Self-contained hi-fi mockups (inline CSS, design-system tokens) |
| `mockup/handoff.md` | mockup-generator | Decisions taken during interview/iteration; the spec input |
| `spec.md` | `/specify` | WHAT & WHY (acceptance criteria, Gherkin scenarios) |
| `plan.md` | `/plan` | HOW (incl. **Documentation impact** section) |
| `tasks.md` | `/tasks` | Ordered chunks, one ≈ one commit |
| `gates.md` | the gates | Verdicts + blockers log for both boards |

## The five gates

Each gate is an agent in `.github/agents/*-gate.agent.md` with two modes.

| Gate | Agent | Spec board (phase 1) | Delivery review board (phase 3) |
|---|---|---|---|
| Product | `product-gate` | Acceptance criteria & Gherkin scenarios are complete, testable, unambiguous | Every acceptance criterion is demonstrably satisfied |
| Design | `design-gate` | Spec + mockup respect the design system | Implemented UI matches the validated mockup and the design system |
| Staff | `staff-gate` | Spec/plan are implementable; tasks.md chunking is sound | Everything that was planned has been delivered, nothing more |
| Security | `security-gate` | The planned work (plan + tasks) is secure by design | Delivered code is secure; CVSS protocol applies |
| Docs | `docs-gate` | Documentation updates are planned as explicit tasks | Documentation was actually updated |

## Blocker protocol (both boards)

- A gate returns exactly one verdict: **GO** or **BLOCKER** (spec board),
  **PASS** or **FAIL** (review board).
- A BLOCKER must state: the **target artifact** (mockup / spec.md / plan.md /
  tasks.md — or another gate's output), **what is wrong**, and a **proposed
  improvement**. A blocker without a proposal is not a valid blocker.
- Loop: fix the target artifact, then re-run the blocking gate **and every gate
  whose input changed**. Repeat until the board is unanimous.
- Record every verdict and blocker in `gates.md` (template below).
- Hard rules: **no implementation before the spec board shows 5× GO**; **no
  merge/PR before the delivery review board passes**.

## Security CVSS protocol (delivery review board only)

For each confirmed vulnerability in the delivered code, `security-gate` computes
a **CVSS v3.1 vector and score**, then:

- **Score < 7.0** — do **not** block. Create a GitHub issue (`gh issue create`)
  containing: the CVSS vector and score, why that scoring, the affected
  code (`file:line`), and the proposed fix. Reference the issue in `gates.md`
  and verdict stays **PASS** (with the issue listed).
- **Score ≥ 7.0** — verdict **FAIL**. The review board is blocked until the
  vulnerability is fixed and re-audited. Do not open a public issue for it;
  report it in `gates.md` and to the user only.

## `gates.md` template

```markdown
# Gates: <Feature name>

## Spec board
| Gate | Verdict | Round | Notes |
|---|---|---|---|
| product | GO | 2 | |
| design | GO | 1 | |
| staff | GO | 2 | |
| security | GO | 1 | |
| docs | GO | 1 | |

## Blockers log
- [resolved] B1 · staff → spec.md: FR3 ambiguous ("fast") → proposed measurable
  threshold; spec.md updated, product re-ran GO.

## Delivery review board
| Gate | Verdict | Refs | Notes |
|---|---|---|---|
| staff | PASS | | all 7 tasks delivered |
| security | PASS | #1234 (CVSS 5.3) | non-blocking finding, issue opened |
| product | PASS | | AC1–AC5 verified |
| docs | PASS | | |
| design | PASS | | matches mockup v3 |
```
