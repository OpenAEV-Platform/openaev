# Anti-Slop Checklist

Read this **before every visual decision**. The 10 cardinal sins of AI-generated UI, and how to avoid them in OpenAEV prototypes.

## The 10 cardinal sins

### 1. Filler content
**Symptom:** Lorem ipsum, "Description goes here", "Feature X helps you achieve Y", generic placeholder text.
**Cure:** Write real copy or leave the space empty with a labeled placeholder. OpenAEV shows real entity names, STIX IDs, actual framework codes (NIST CSF, ISO 27001), plausible dates.

### 2. Aggressive gradients
**Symptom:** Gradient backgrounds on cards, gradient buttons, gradient text.
**Cure:** Flat solid colors from tokens only. The only allowed gradients are `--gradient-ia` (AI features) and `--gradient-focus` (branding element). Never on content surfaces.

### 3. Emoji as UI
**Symptom:** 🚨 for alerts, ✅ for success, 📊 for data.
**Cure:** Use severity colors + text labels for status. Use Lucide icons for actions. Zero emoji tolerance.

### 4. Marketing-page tropes
**Symptom:** "Welcome to OpenAEV!", hero sections, "Get started" CTAs, feature selling language, testimonials.
**Cure:** This is an internal security tool. Users arrived because they need it. Jump straight to the data. No selling, no onboarding language in prototypes (unless the mockup IS an onboarding flow).

### 5. Decorative SVGs
**Symptom:** Hand-drawn illustrations, abstract shapes, "empty state" illustrations that add no information.
**Cure:** Labeled placeholders (`[chart · timeline of evidence]`) are more honest. If an empty state needs content, write the actual microcopy.

### 6. Left-border accent overuse
**Symptom:** Every card/alert/section has a colored left border as its only distinguishing feature.
**Cure:** Reserve colored borders for actual status indication (severity). Use subtle full borders (`--border-light`) for default cards. Let content hierarchy speak for itself.

### 7. Dark-mode drop shadows
**Symptom:** Box-shadow on cards in dark theme (invisible against dark background, just creates muddy edges).
**Cure:** In dark mode, use border (`--border-light` = `--gray-800`) for elevation. Save shadows for light mode only, and sparingly.

### 8. Vanity numbers without context
**Symptom:** "87% compliant", "Security Score: 92", "3 issues resolved" without denominators or baselines.
**Cure:** Always show the denominator: "14 of 287 controls covered". Show the tier breakdown (T1/T2/T3). If something is a ratio, show both numbers. Never a percentage alone.

### 9. "3-of-anything" filler
**Symptom:** Exactly 3 cards, 3 metrics, 3 features — the lazy pattern of AI-generated layouts.
**Cure:** Use the number that makes sense for the data. 7 severity levels? Show 7. 12 entity types? Show 12. 1 critical alert? Show 1 prominently. Data drives layout, not aesthetics.

### 10. Generic copywriting
**Symptom:** "Streamline your workflow", "Gain insights", "Take action on threats".
**Cure:** Be specific. "2 controls drifted since last attestation" beats "Action required" every time. OpenAEV copy is conversational but precise.

## OpenAEV-specific traps

### Fake compliance scores
Never invent an aggregate "compliance score" or "security posture percentage." OpenAEV's philosophy: show coverage ratios with tier breakdowns. "142 of 287 controls covered (T1: 98%, T2: 67%, T3: 12%)" — honest.

### Fake AI confidence
Never show "AI Confidence: 87%" or "AI-powered insight" badges. If AI contributed to a result, show the provenance (which model, what input, when), not a made-up confidence percentage.

### Fake "AI insight" callouts
Never add purple-highlighted "AI suggests..." callouts unless the feature genuinely has an AI component AND the user asked for it. AI tokens (`--ai-main`, `--ai-background`) are reserved for actual AI features.

### Color-only severity
Never rely on color alone for severity. Always pair with:
- A text label (Critical / High / Medium / Low / Info)
- Or a number with context ("3 critical, 12 high")
- Accessibility requires text + color, always.

### Entity color misuse
Never use entity colors (`--entity-threats`, `--entity-arsenal`, etc.) for anything other than identifying that specific entity type. They're not decoration — they're a visual vocabulary users rely on.

## 60-second self-audit

Before declaring any prototype done, answer these honestly:

1. Could a user mistake any part of this for a marketing page? → Remove it.
2. Is there ANY lorem ipsum or placeholder text? → Replace with real-feeling copy.
3. Are there exactly 3 of something because "3 felt right"? → Justify the count with data.
4. Does any number lack a denominator or context? → Add it.
5. Is there a gradient that isn't `--gradient-ia` or `--gradient-focus`? → Remove it.
6. Is there an emoji? → Remove it.
7. Is there a colored left border that doesn't indicate severity? → Remove it.
8. Are there drop shadows in dark mode? → Replace with border.

If any answer triggers a change, make it before shipping.
