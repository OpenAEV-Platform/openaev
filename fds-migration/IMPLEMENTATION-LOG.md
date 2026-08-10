# Implementation Log — OpenAEV

Append-only session journal — never rewrite a previous entry, only add new
ones at the bottom. One entry per work session: date, what changed, why,
and any friction that should feed back into the process (prompts/scripts
in filigran-design-system).

## Log format

```
### YYYY-MM-DD — <short summary>
- Branch: fds/...
- Changed: <files>
- Friction / process feedback: <none, or what to fix upstream>
```

---

### 2026-07-18 — review pass: dedupe tonic-primary lookups
- Branch: fds/tokens-colors
- Changed: openaev-front/src/components/ThemeDark.ts, ThemeLight.ts —
  secondary/gradient.main/xtmhub.main now reuse the EE_COLOR constant
  instead of repeating the raw `--color-filigran-tonic-primary` lookup
  (4 Copilot suggestions on PR #6684); pure refactor, no value change.
- Friction / process feedback: Copilot also flagged that
  `scripts/check-fds-conformity.mjs` writes a volatile `generatedAt`
  timestamp into the tracked `reports/conformity-latest.json`, dirtying
  the working tree on every re-run even when results are identical. The
  script is a generated template copied verbatim from
  filigran-design-system (`scripts/fds-migration-templates/`), so per its
  own header the fix belongs upstream (make the report deterministic or
  gate the timestamp behind a flag), not in a local hand-edit here.

### 2026-08-10 — Header pilot: the bar's interior converts to the library
- Branch: sandyghs-miniature-enigma
- Changed: openaev-front/src/admin/components/nav/TopBar.tsx,
  TopBarIconLink.tsx (new), TopBarNotifications.tsx,
  BulkOperationsIndicator.tsx, admin/components/ariane/AskArianeButton.tsx,
  CtemCommandCenterButton.tsx, plus the matching tests and
  __tests__/utils/designSystemAssertions.tsx (new).
- What changed: the bar was `Header` + `HeaderGroup` from the library wrapped
  around an entirely MUI interior. Search is now the library `SearchField`,
  "Ask Ariane" the library `Button` (`ia`/`tertiary`, which paints the AI
  gradient the product used to hand-roll with `backgroundClip`), every icon
  button the library `IconButton` at `tertiary` priority, the account dropdown
  the library `Menu`, and every tooltip the library `Tooltip`. No MUI control
  is left in the bar — asserted, not reviewed.

- **SCOPE RULE (designer, 2026-08-10) — candidate for the playbook.**
  *Wherever the library ships a component, the pilot uses it.* Adopting the
  container and leaving the contents on the old stack is not adoption: a MUI
  control painted to look right still carries MUI's focus, hover and selected
  states, which is what the designer saw. Sole maintained exception: icon
  GLYPHS stay MUI for now (same arbitration as the Navbar pilot).
  Corollaries worth carrying upstream:
    - the rule needs a *test*, not a review — `expectNoMuiControls` fails on
      any surviving MUI control, so the rule cannot rot;
    - it needs an explicit answer for "the library has no such component"
      (here: Divider, Badge, Popover — recorded as feedback #22) so the
      exception is named rather than assumed;
    - it needs an explicit answer for "the library's component cannot express
      the required behaviour" (here: `IconButton` cannot be a link, feedback
      #21) — the escape hatch is the library's own variant function, never a
      hand-painted look-alike.

- Friction / process feedback: two traps cost real time and neither is
  visible from the library side.
  1. Default variants are not neutral. `iconButtonVariants({})` resolves to
     `priority: 'primary'`, a FILLED brand button. Swapping "the icon button"
     without naming the priority repaints every icon in the bar solid blue.
     Step 6b should say: name the variant, never take the default on trust.
  2. **Layered utilities lose to unlayered host CSS** (feedback #24). Two
     elements with the *same* class list rendered in different colours because
     MUI's CssBaseline injects an unlayered `body a`. Class present, stylesheet
     correct, no error, wrong pixels. The playbook should require, at Step 5b,
     measuring the same library class on more than one element type — that is
     what surfaced it here.
