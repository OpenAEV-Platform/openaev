# Worked Example: Compliance Landing

This example walks through the full 6-step process for a compliance overview page in OpenAEV.

---

## The brief

> "Fais-moi la landing page compliance pour un CISO — il doit voir en un coup d'œil où il en est sur ses frameworks."

---

## Step 1: Understand

**Signals:**
- "landing page" → new page, not a tab
- "compliance" → frameworks, controls, attestations, evidence
- "CISO" → executive persona (summary-first, not deep-dive)
- "en un coup d'œil" → hero metric priority
- "ses frameworks" → plural, multi-framework view

**Decision:** Brief is clear enough to ask 5 focused questions (not the full 12).

---

## Step 2: Questions asked

```
1. Scope — La vue compliance montre :
   - [x] Multi-framework avec navigation par framework
   - [ ] Vue transverse (tous les contrôles mélangés)

2. Volume — Combien de frameworks et contrôles en moyenne ?
   → "3-5 frameworks, 100-300 contrôles par framework"

3. Hero metric — Quelle info en premier ?
   → "Ratio global de couverture, puis breakdown par severity/tier"

4. Actions disponibles — Que peut faire le CISO ici ?
   → "Filtrer par framework, cliquer sur un contrôle pour voir les preuves, lancer une attestation"

5. Empty state — À quoi ressemble la page jour 1 ?
   → "Décide pour moi"
```

---

## Step 3: Plan

**2 directions on the axis: density × metaphor**

| Direction | Name | Axis |
|-----------|------|------|
| A | Dense Matrix | Table-first, compact, every control visible |
| B | Coverage Rings | Visualization-first, concentric rings per framework |

**Hero element:** Ratio "X of Y controls covered" (all frameworks combined)

**Mock data:**
- 3 frameworks: NIST CSF (287 controls), ISO 27001 (93 controls), SOC 2 (61 controls)
- Coverage: ~50% NIST, ~70% ISO, ~80% SOC 2
- Status distribution: mostly covered, some drifting, a few stale

---

## Step 4: Build

**File structure:**
```
docs/design/mockups/
├── colors_and_type.css
├── Compliance Overview.html          ← canvas with both directions
└── compliance-overview-components.jsx
```

**Key decisions:**
- Canvas layout: Direction A (left) + Direction B (right)
- Same mock data in both
- Both have: eyebrow, hero ratio, framework selector, control list/viz
- Direction A emphasizes: table rows, severity badges, attestation dates
- Direction B emphasizes: ring chart, hover-to-drill, progressive disclosure

**Mock data example:**
```jsx
const frameworks = [
  {
    id: 'nist-csf',
    name: 'NIST CSF',
    version: '2.0',
    totalControls: 287,
    covered: 142,
    drifting: 8,
    stale: 3,
    tiers: { t1: { total: 45, covered: 44 }, t2: { total: 120, covered: 81 }, t3: { total: 122, covered: 17 } },
  },
  {
    id: 'iso-27001',
    name: 'ISO 27001',
    version: '2022',
    totalControls: 93,
    covered: 65,
    drifting: 2,
    stale: 0,
    tiers: { t1: { total: 20, covered: 20 }, t2: { total: 48, covered: 38 }, t3: { total: 25, covered: 7 } },
  },
  {
    id: 'soc2',
    name: 'SOC 2 Type II',
    version: '2017',
    totalControls: 61,
    covered: 49,
    drifting: 1,
    stale: 1,
    tiers: { t1: { total: 15, covered: 15 }, t2: { total: 30, covered: 28 }, t3: { total: 16, covered: 6 } },
  },
];
```

---

## Step 5: Verify

Checklist results:
- ✅ No console errors
- ✅ All colors from tokens
- ✅ Both themes work
- ✅ All numbers in IBM Plex Mono
- ✅ Eyebrows on every section
- ✅ No emoji, no lorem ipsum
- ✅ Ratios with denominators everywhere
- ✅ Directions contrast on density×metaphor axis
- ✅ Hover states on all interactive elements

---

## Step 6: Hand-off

**Direction A — Dense Matrix**
Table-first layout. Every control is a row with severity, status, last attestation, evidence count. The CISO can scan 50+ controls per viewport and sort/filter aggressively. Hero metric is top-left: "256 of 441 controls covered" with tier breakdown chips. Best for data-confident executives who want to drill into specifics quickly.

**Direction B — Coverage Rings**
Visualization-first. Each framework is a concentric ring where filled segments represent covered controls (colored by severity). Hovering a segment reveals the control. Progressive disclosure: overview → framework → control → evidence. Best for periodic check-ins where the CISO wants a gestalt read before diving in.

**Next iterations:**
- Add attestation workflow (click a control → modal with evidence + "attest" button)
- Add time dimension (coverage trend over last 6 months)
- Try a "compliance timeline" variant (events-based rather than state-based)
