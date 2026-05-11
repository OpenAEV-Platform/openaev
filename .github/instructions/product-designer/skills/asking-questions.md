# Asking the Right Questions

## When to ask

| Brief type | Questions needed | Action |
|------------|-----------------|--------|
| Vague one-liner ("fais-moi un dashboard compliance") | 8-12 questions | Full interview |
| Clear feature with open design choices | 3-5 scoping questions | Focused interview |
| Specific iteration ("ajoute un filtre par framework") | 0-1 confirmation | Just do it |
| Explicitly says "decide for me" | 0 | Lock defaults and state them |

## The "starting line" questions (always ask these for new features)

### 1. Scope
"What's included in this view? Just [X], or also [Y] and [Z]?"

### 2. Variation count
"Should I explore multiple contrasting directions, or do you already have a clear vision?"

### 3. Visual ambition
- **Sober** — minimal, data-dense, bordering on utilitarian
- **Balanced** — the filigran-ui default: clean, professional, some signature moments
- **Signature** — bold layout choices, visualization-forward, opinionated

### 4. Persona
"Who's the primary user of this screen?"
- Security analyst (daily operator, wants density + keyboard nav)
- CISO / executive (weekly glancer, wants summary + trends)
- Auditor (episodic, wants exhaustive data + export)
- Developer (integration-focused, wants API/config/code)

### 5. Interactivity
- **Static** — just visuals for option-comparison
- **Clickable** — drill-down, modals, navigation between states
- **Full workflow** — multi-step flow with state changes

## Domain-specific follow-ups (pick 4-6 relevant ones)

### Data & content
- What entities does this screen show? (threats, controls, evidence, observables, etc.)
- What's the typical data volume? (10 items? 10,000?)
- What does "day zero" look like vs. "6 months in"?
- What filters/sorting does the user need?
- What's the hero metric — the first thing the user's eyes should land on?

### Context & navigation
- Where does this sit in the navigation? (top-level page? tab within a feature? modal?)
- What do they do BEFORE arriving here? What AFTER?
- What's the parent/sibling page for visual reference?

### Edge cases
- What does "empty" look like?
- What does "overwhelmed" look like (too much data)?
- What does "stale" or "disconnected" look like?

### Actions
- What can the user DO here? (filter, create, attest, export, assign, dismiss)
- Any destructive actions? (delete, revoke, override)
- Any bulk actions?

## How to format questions

Present them as a **numbered list with options** (not a wall of text):

```
Quelques questions avant de commencer :

1. **Scope** — La vue compliance inclut quels cadres ?
   - [ ] Un seul framework (lequel ?)
   - [ ] Multi-framework avec navigation
   - [ ] Vue transverse (tous les contrôles, quel que soit le framework)

2. **Persona principal** — Qui regarde cette page au quotidien ?
   - [ ] Analyste sécurité (dense, opérationnel)
   - [ ] CISO (résumé, tendances)
   - [ ] Auditeur (exhaustif, exportable)

3. **Volume de données** — Combien de contrôles en moyenne ?
   - [ ] < 50
   - [ ] 50-200
   - [ ] 200+

4. **Hero metric** — Quelle est LA première info que l'œil doit capter ?
   - [ ] Ratio couverture globale (ex: 142/287)
   - [ ] Nombre d'alertes/drifts actifs
   - [ ] Autre (précise)
```

## Rules after receiving answers

1. **Acknowledge briefly** — restate the key decisions in one sentence
2. **State your interpretation** — "Je vais donc faire 2 directions: l'une dense/analyste, l'autre synthèse/CISO"
3. **Start building** — don't ask more questions unless genuinely blocked
4. **If answers are contradictory** — point it out, suggest a resolution, move on

## The "decide for me" defaults

When the user explicitly says to decide:
- **2 contrasting directions** on the most interesting axis for the feature
- **Balanced ambition** (the filigran-ui standard)
- **Analyst persona** (most demanding — if it works for analysts, it works for executives)
- **Clickable interactivity** (drill-down + hover states)
- **Anti-bullshit treatment** (honest numbers, real copy)

State these explicitly: "Je choisis: 2 directions, ambition balanced, persona analyste, cliquable. Si tu veux changer un axe, dis-moi."

## Anti-patterns

- ❌ Asking about things already defined in the design system (fonts, colors, radius)
- ❌ Asking more than 12 questions
- ❌ Asking in a wall of text without checkboxes/options
- ❌ Asking again after receiving clear answers
- ❌ Not asking when the brief is genuinely ambiguous
