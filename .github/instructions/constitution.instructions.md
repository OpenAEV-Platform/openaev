---
applyTo: "**/*"
description: >-
  Project constitution — the non-negotiable engineering principles that govern
  every change in OpenAEV. Read first, applies everywhere, cited by the
  spec-driven workflow (/specify, /plan, /tasks, /implement).
---

# OpenAEV Constitution

> Governing principles for a codebase cleaner than yesterday's. Tool-agnostic:
> loaded by GitHub Copilot (via `applyTo`) and by Claude Code (via `AGENTS.md`
> + the `.claude/` bridge). These principles **override convenience** — when a
> shortcut conflicts with a principle, the principle wins or the deviation is
> justified in writing (in the spec/plan).

## Article 1 — Spec before code

Non-trivial work starts from an intent, not a diff. A change should be traceable
to a spec/plan/task (see `specs/` and the `/specify` → `/plan` → `/tasks` →
`/implement` workflow). Trivial fixes (typo, one-line) are exempt.

## Article 2 — Respect module boundaries

- **Never add new code to `openaev-framework`** — it is deprecated. New utilities
  go in `openaev-api` or `openaev-model`.
- New backend code lives in `io/openaev/api/**`, not the legacy `io/openaev/rest/**`.
- Layering is one-directional: Controller → Service → Repository. No repository
  injection in controllers; business logic lives in `@Service`.

## Article 3 — Never expose entities

The API never returns JPA entities. Always map through Output records + a Mapper.
Reads are `@Transactional(readOnly = true)`; use Spring's `@Transactional`
(never `jakarta.transaction.Transactional`).

## Article 4 — Fix the root cause, not the symptom

If a bug originates in the backend, fix it in the backend — do not paper over it
with frontend workarounds (onError handlers, fallback states). Address the
correct layer. (See `.github/skills/review-code/SKILL.md`, anti-patterns.)

## Article 5 — Don't repeat yourself, deliberately

Shared values (config keys, filenames, formatted strings) are defined once —
extract to a constant or a private method rather than recomputing in several
places.

## Article 6 — Tests proportionate to risk

Cover behavior that can break in ways that matter; write tenant-isolation tests
for tenant-scoped changes (`.github/skills/add-test`). Do **not** demand tests
for small mechanical changes (parameter threading, one-line additions).

## Article 7 — Security & multi-tenancy are not optional

Respect access control, capabilities, filters, and tenant scoping. Changes
touching `@AccessControl`, `@Filter`, `Capability`, native `@Query`, `TenantBase`,
or `tenant_id` must pass the Security / Multi-Tenancy reviewers before merge.

## Article 8 — Non-critical startup work is best-effort

Startup interactions with external services (MinIO, S3…) for non-critical assets
(logos, thumbnails) are wrapped in try/catch with `log.warn` so a failure never
blocks application startup. Use `@Slf4j`; never `System.out`/`printStackTrace`;
never log secrets.

## Article 9 — Keep the change focused

One PR does one thing. Don't fix unrelated pre-existing issues inside a feature/bug
PR — track them as follow-ups. PRs > 500 lines or > 20 files are flagged for
splitting before detailed review.

## Article 10 — Reviewer agents gate merge

The specialized reviewers (`code-reviewer` hub → security / performance /
multi-tenancy / frontend / migration / test specialists) are the quality gate.
`/implement` must run the relevant reviewers and resolve their findings before a
change is considered done.

## Article 11 — Commit & PR hygiene

Conventional Commits ending with the issue reference `(#issue)`; no `[frontend]`/
`[backend]` bracket prefixes in PR titles. Small logical commits. Sign your
commits. Every PR is linked to an issue. (See `AGENTS.md` conventions.)

---

**Amendment process:** this constitution is a shared source of truth in `.github/`.
Amend it via `/constitution`, keep it principle-level (the *why* and the *rule*),
and let the domain `.github/instructions/*.md` hold the detailed *how*.
