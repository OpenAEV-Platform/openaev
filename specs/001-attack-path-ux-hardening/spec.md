# Spec: Attack Path — UX & design-system hardening

- **ID**: 001-attack-path-ux-hardening
- **Issue**: #6647
- **Status**: draft

## Intent (why)

The Attack Path POC is functional but reads as a small, sparse diagram whose prioritization
("what do I fix first?") is hidden behind a click, uses one colour (red) for three unrelated
meanings, and diverges from the platform's design system (bespoke drawer/panels, inline styling,
missing accessibility text on the graph). This feature makes the attack path **legible,
prescriptive at a glance, accessible, and consistent with the rest of OpenAEV** — so an analyst
immediately sees the highest-leverage endpoints to fix and can trust what the colours mean.

## Scenarios

- **Prioritization is visible on landing.** *Given* a simulation with attack-path data, *when*
  the tab opens, *then* the graph is auto-fitted/centred and the top chokepoints are visible
  without any click (a persistent ranked surface, not only a card-popover).
- **Colour is unambiguous.** *Given* the graph, *when* an endpoint is both a top chokepoint and
  "neither prevented nor detected", *then* the chokepoint signal and the verdict signal are
  distinguishable by shape/colour (red is used only for the prevention/detection verdict).
- **Accessible status on the graph.** *Given* a screen-reader or colour-blind user, *when* they
  focus an endpoint/cluster/edge, *then* its prevention/detection status is available as
  text (aria-label/tooltip), not colour alone.
- **Every panel is dismissable and consistent.** *Given* any side panel (finding, execution,
  endpoint), *when* it is open, *then* it has a close control and a header consistent with the
  other panels and with the platform's shared drawer/panel components.
- **Path story is readable.** *Given* an execution panel, *when* it opens, *then* a one-line
  breadcrumb summarizes the resolved path (injector → endpoint → finding).
- **Review/export off the graph.** *Given* a large simulation, *when* the analyst needs to review
  or share, *then* a table/list view of chokepoints and findings is available (sortable,
  exportable) instead of only a node-link graph.
- **Colour key stays available.** *Given* a side panel is open, *when* the analyst reads a
  coloured verdict/badge, *then* a minimal verdict-colour key remains visible.

## Functional requirements

### Prioritization & legibility (P0)
- FR1: The graph MUST auto-fit/centre on initial load, on simulation switch, on focus transitions
  (entering/leaving the focused path), and on **collapse** (which removes nodes and would otherwise
  leave the analyst lost). It MUST NOT re-fit on **expand** / reveal-more — the current zoom/pan is
  preserved while drilling down.
- FR2: The top-chokepoints count MUST be visible on landing (summary card), and the ranked list
  MUST be one click away via the card popover — which scales to any number of chokepoints, unlike a
  fixed inline strip.
- FR3: The "most exposed endpoints" ranking MUST remain explained to the user (what a chokepoint
  is and how it is scored), via the existing help affordance.

### Colour semantics & accessibility (P0)
- FR4: Green/orange/red MUST be reserved exclusively for the prevention/detection verdict.
- FR5: The chokepoint indicator MUST use **purple/violet** (distinct from the verdict green/orange/red
  and from the finding-type palette); the hidden-endpoints "+N" indicator MUST use a neutral pill
  (grey/blue). No single colour carries multiple meanings.
- FR6: Every status-coloured graph element (endpoint node, endpoint cluster, finding cluster,
  edge) MUST expose its status as text (aria-label/title), never colour alone.
- FR7: A minimal verdict-colour key MUST stay visible even when a side panel is open.
- FR8: The feature MUST render correctly in both light and dark themes.

### Panels & design-system consistency (P0/P1)
- FR9: The endpoint side panel MUST have a close control and MUST be consistent (header, width)
  with the finding and execution panels.
- FR10: The findings drawer MUST use the platform's shared drawer component (consistent close
  placement and header chrome) rather than a bespoke drawer.
- FR11: New/changed UI MUST follow the platform styling convention (theme tokens via the app's
  standard styling API) rather than mixing ad-hoc inline styles.
- FR12: (**deferred** — no refactor this spec) Status representations SHOULD reuse the platform's
  status component/colour source rather than a parallel private implementation.

### Evidence & story (P1)
- FR13: The execution panel MUST show a one-line path breadcrumb (injector → endpoint → finding).
- FR14: Node interaction affordances SHOULD be discoverable (the differing click outcomes per
  node kind are hinted in the legend or on hover).

### Review, export & filtering (P2)
- FR15: A view switcher (`Graph · Table`) MUST be offered at the top of the tab. Graph is the
  default. The Table view MUST list chokepoints and findings as an alternative to the graph and
  MUST be exportable.
- FR16: A search box next to the view switcher MUST let the analyst find an endpoint (by hostname/
  ip), injector, or finding category and adapt the graph accordingly (focus the endpoint's path,
  highlight the injector, or open the finding-type drawer). Further filtering (by severity, platform/
  OS) MAY follow.

### Polish (P2)
- FR17: Dates displayed to the user MUST be formatted via the platform formatter (no raw ISO).
- FR18: Duplicated card and image-fallback UI SHOULD be factored into shared components.
- FR19: User-facing strings MUST be fully translated (close the i18n debt) before GA.

## Out of scope

- Backend data contracts (real security platforms/alerts, detection remediations, inject/payload
  ids, per-finding verdicts, real ATT&CK techniques) — tracked in
  `ATTACK_PATH_BACKEND_REQUIREMENTS.md`; this spec is front-side UX/design-system.
- New analytic capabilities (blast-radius modelling, identity/AD graphs, cross-simulation
  trending) — tracked in `ATTACK_PATH_ANALYSIS.md`'s action plan, not here.
- Screenshot/visual proof capture in the evidence panel (backend-dependent) — later.
- Server-side chokepoint scoring at scale (v1 stays front-side, endpoint-with-most-findings).

## Constraints & impacts

- **Multi-tenancy**: none new — all changes are front rendering of already tenant-scoped data.
- **Security / permissions**: no new capability; keep secret masking and EE gating intact
  (detection-remediation surfaces stay EE-gated).
- **Data / migration**: none — no persisted data, no Flyway migration. Feature stays behind the
  `ATTACK_PATH` preview flag.
- **Design system**: prefer existing shared components (`common/Drawer`, `ItemStatus`, `Tabs`)
  and theme tokens; net reduction of bespoke UI.

## Resolved decisions

- **Landing model**: graph-first (not list-first). The graph stays the default view; prioritization
  is surfaced by the **Top-chokepoints summary card** (count on landing, ranked list in its popover)
  plus the **Table view**. A separate inline "Fix first" chip strip was dropped — it duplicated the
  card and did not scale past ~8 chokepoints on one line. (FR2)
- **Chokepoint accent**: **purple/violet**, distinct from the verdict green/orange/red and the
  finding-type palette; "+N" hidden-endpoints uses a neutral pill. (FR5)
- **Table/export**: **in scope for this spec**, delivered through a view switcher
  (`Graph · Table`). May still be split into its own implementation slice at `/plan`. (FR15)
- **ATT&CK / MITRE**: **out of this spec.** No aggregate strip, no matrix view. The existing
  per-injector chip in the drawer is untouched; richer ATT&CK surfaces wait on backend technique
  data (tracked in `ATTACK_PATH_BACKEND_REQUIREMENTS.md`).
- **Styling convergence**: **no refactor at this stage.** Only new/changed UI follows the platform
  styling convention (FR11); the repo-wide `sx`/`makeStyles` cleanup and status-component
  convergence (FR12) are **deferred** — not part of this spec's delivery.
- **Feature flag**: the entire feature stays behind the `ATTACK_PATH` preview flag (see Constraints).
