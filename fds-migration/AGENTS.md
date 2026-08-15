# AGENTS.md — fds-migration (OpenAEV)

GENERATED — do not edit by hand. Regenerate: `pnpm generate:fds-migration --product openaev --write-to-product` (filigran-design-system repo).

This file is the agent contract for the Filigran Design System migration
work in this repo. Read it before touching anything under `fds-migration/`
or any file listed in `migration-state.json`'s `wiredFiles`.

## Source of truth

Tokens, components and their docs live in a separate repo:
`@filigran/design-system` — the sibling `filigran-design-system/` checkout
in the Filigran workspace. This repo NEVER defines a design-system token
locally: every color, spacing, radius and typography value used here traces
back to `filigran-design-system/packages/filigran-design-system/src/tokens/theme.css`.

Full machine-readable reference: <https://silver-doodle-mnyv84e.pages.github.io/llms-full.txt>
(same content as the sibling checkout's `filigran-design-system/llms-full.txt`,
served by the docs site).

## Non-negotiable rules

1. **Never hand-edit a generated file.** `openaev-front/src/components/fds-tokens.generated.ts` and its sidecar
   `.meta.json` are produced by `pnpm generate:mui-bridge` in the
   filigran-design-system repo. If a value looks wrong, fix `theme.css`
   upstream (a Figma export, delivered by a human designer) — never patch
   the generated file here.
2. **Never invent a token value.** A color/spacing/typography value with no
   design-system equivalent is a gap to flag (TOKEN-MAPPING.md, section
   "Tokens to create in Figma"), not something to improvise.
3. **Branch discipline.** All work happens on `fds/*` branches, never on
   this product's main/master. Run `git branch --show-current` before
   every commit. No push to any remote without explicit human validation.
4. **Missing component → flag, never fork.** If a design-system component
   doesn't exist yet for something you're migrating, report the gap
   (filigran-design-system's `process/AI-BACKLOG.md` or `ROADMAP.json`)
   and move on — never build a local approximation.
5. **This phase is TOKENS ONLY.** The current workstream
   (IMPLEMENTATION-ROADMAP.md, "Phase 1") wires design-system token
   *values* into this product's existing MUI theme — it does not touch
   component code. Migrating individual components to design-system
   components is a separate, future process with its own prompt; do not
   start it here unless explicitly asked.

## Where things are

| What | Where |
|---|---|
| Generated token data | `openaev-front/src/components/fds-tokens.generated.ts` (+ `.meta.json` sidecar) |
| Token → theme-field wiring decisions | `fds-migration/TOKEN-MAPPING.md` |
| What to migrate, in what order, current state | `fds-migration/IMPLEMENTATION-ROADMAP.md` |
| Session journal (append, never rewrite) | `fds-migration/IMPLEMENTATION-LOG.md` |
| MUI component → design-system component reference | `fds-migration/COMPONENT-MAPPING.md` |
| Conformity check (run before every commit touching a wired file) | `node fds-migration/scripts/check-fds-conformity.mjs` |
| Upstream state manifest | `filigran-design-system/ROADMAP.json` (`implementations`, id `tokens-openaev`) |

## Conformity check

Run `node fds-migration/scripts/check-fds-conformity.mjs` before committing
any change to a file listed in `migration-state.json`'s `wiredFiles`. It
verifies the generated bridge file hasn't been hand-edited, that wired
files still import it, and that no hardcoded value has crept back into a
migrated zone. Fix everything it reports before committing — it lists
concrete file:line issues, it does not need re-deriving by hand.

### Declaring a component-adoption site (`libComponentUsage`)

Once a real design-system COMPONENT (not just its tokens) is adopted in a
file, declare it in `migration-state.json`'s `libComponentUsage` so the
check keeps watching it:

```jsonc
"libComponentUsage": [
  {
    "component": "Paper",
    "importFrom": "@filigran/design-system",
    "files": ["openaev-front/src/.../PanelWidget.tsx"],
    "guards": ["imported-from-library", "no-hardcoded-padding"],
    "reason": "Paper owns padding as a typed prop (0|8|16|24|32) since the Phase 0 round"
  }
]
```

Two things to know before writing one:

- **You declare intent, never a pattern.** `guards` names checks the design
  system implements and maintains; an unknown name is reported `INVALID`
  rather than passing quietly, so a typo can never read as coverage. Run
  the check with an obviously wrong name once if you want to see the list.
- **The scan is structural, not textual.** Each `<Component …>` opening tag
  is walked to its own closing `>` with quotes, template literals and `{}`
  nesting tracked, after comments are stripped — so a multiline element is
  the normal case, a padding on a sibling element is not attributed to this
  one, and a commented-out class cannot produce a finding. Findings name one
  element and one line.

Available guards today:

| Guard | What it catches |
|---|---|
| `imported-from-library` | the component is rendered but no longer imported from the library, or has fallen back to `@mui/material` — a revert the JSX alone cannot show |
| `no-hardcoded-padding` | a rendered instance sets padding through `className`, `sx` or `style` instead of the component's own `padding` prop, re-forking a scale the component now owns |

## Notes

MUI 7.3 + tss-react (consumes the MUI theme directly — no separate wiring needed). Repo, clone directory and front directory (openaev-front/) all use the openaev name since the upstream rename.
