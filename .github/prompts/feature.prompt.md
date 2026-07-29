---
description: "Orchestrate the full Feature Workflow: mockup interview → five-gate spec board (blocker loop) → implementation → five-gate delivery review board."
---

You are running **`/feature`**, the orchestrator of the OpenAEV Feature Workflow.
Read `.github/instructions/feature-workflow.instructions.md` first — it defines
the phases, the five gates, the blocker protocol and the CVSS protocol. You drive
the phases; each phase's detailed procedure lives in its own prompt.

## Phases

1. **Mockup** — run `.github/prompts/mockup.prompt.md`. Interactive; do not
   proceed until the user validates the mockup (`mockup/handoff.md` written).
2. **Spec package + spec board** — run, in order,
   `.github/prompts/specify.prompt.md`, `plan.prompt.md`, `tasks.prompt.md`
   (each already runs its own gates and records verdicts in `gates.md`).
   Then enforce the board: if any gate recorded a BLOCKER, apply the blocker
   protocol — fix the target artifact, re-run the blocking gate and every gate
   whose input changed — and loop until `gates.md` shows **5× GO**.
3. **Checkpoint** — present the validated package (spec, plan, tasks, gate
   verdicts, blocker history) to the user and get an explicit go before
   implementing.
4. **Implementation + delivery review board** — run
   `.github/prompts/implement.prompt.md` (it ends with the five-gate delivery
   review board, including the security CVSS protocol).
5. **Report** — what shipped, both boards' verdicts, security issues opened
   (CVSS < 7.0), follow-ups.

## Rules

- Run the gates as **subagents** (`.github/agents/*-gate.agent.md`), telling each
  its mode (spec board / delivery review). Gates whose inputs are independent may
  run in parallel.
- Never skip a phase; never start implementing without 5× GO **and** the user's
  checkpoint go.
- If the user arrives mid-flow (mockup or spec already done), detect the state
  from `specs/NNN-slug/` and resume at the right phase.

Argument (optional): the feature description, or an existing `specs/NNN-slug` id
to resume.
