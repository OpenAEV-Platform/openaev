# Implementation Roadmap — OpenAEV

GENERATED SKELETON — scaffolded once by `pnpm generate:fds-migration --product openaev --write-to-product`.
Sections below are then maintained by agents as work progresses;
re-running the generator does NOT overwrite this file (only creates it if
missing).

## Phase 1 — Tokens (current chantier)

Upstream tracking: filigran-design-system `ROADMAP.json` `implementations[]`,
id `tokens-openaev`. Mirror major state changes back there (AGENTS.md
"Roadmap — règles d'usage": owner before branch, status in the same commit
as the work).

- [ ] Bridge generated (`openaev-front/src/components/fds-tokens.generated.ts` present, unedited)
- [ ] Theme files wired (see TOKEN-MAPPING.md)
- [ ] Visual validation done (dark + light, key screens)
- [ ] Conformity check green (`node fds-migration/scripts/check-fds-conformity.mjs`)
- [ ] Env de test deployed and validated

## Phase 2 — Components (future, not started)

Not scoped yet. See fds-migration/AGENTS.md rule 5 — do not start component
migration under this phase without an explicit go-ahead. When it starts,
order by filigran-design-system `ROADMAP.json` `priority`; readiness for
this product is COMPONENT-MAPPING.md's "Product status" column.

### Planned steps

- [ ] Navbar — planned. Coverage audit already done: see COMPONENT-MAPPING.md
  ("Navbar" row — MUI identifiers, occurrences, lib/product status). No
  implementation started; this is a scoping placeholder only, rule 5 above
  still applies.
