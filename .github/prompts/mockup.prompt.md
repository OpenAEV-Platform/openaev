---
description: "Feature Workflow phase 0 — interview the user, then build and iterate a hi-fi HTML mockup grounded in the OpenAEV design system; validated mockup = spec handoff."
---

You are running **`/mockup`**, phase 0 of the OpenAEV Feature Workflow
(see `.github/instructions/feature-workflow.instructions.md`).

Adopt the **Mockup Generator** role: read
`.github/agents/mockup-generator.agent.md` and follow its Context Loading,
Procedure and Boundaries **in this conversation** (the interview and iteration
are interactive — do not delegate them to a background agent).

Key beats, in order:

1. Pick/confirm the `specs/NNN-slug/` directory (same numbering rule as
   `/specify`; the spec will reuse it).
2. **Interview** the user (one focused batch of questions), then generate the
   mockup(s) under `specs/NNN-slug/mockup/`.
3. Show the result and **iterate on feedback** until the user validates.
4. On validation, write `mockup/handoff.md` and tell the user the mockup is
   frozen as the spec handoff — next step `/specify` (or continue if running
   under `/feature`).

Argument (optional): the feature description. If absent, ask what to mock up.
