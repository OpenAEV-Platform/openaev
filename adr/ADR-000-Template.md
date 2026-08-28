# ADR-0002: Multi-tenant data isolation strategy

|  |                                                        |
| --- |--------------------------------------------------------|
| Status | AcceptedDraft / Proposed / Accepted / Superseded by ADR-XXXX / Deprecated                                               |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/XXX |

## 1. Context

What problem are we solving and why now. Cover the business need, the technical constraint, and the history if relevant. Stay focused on what someone reading this in two years needs to understand the decision.

Keep it short. Two or three paragraphs is usually enough. If you need more, ask yourself if you are writing a design doc instead of an ADR.

## 2. Decision drivers
The criteria we used to weigh the options. Make them explicit so the trade-off is auditable. Typical drivers:

- Security and data isolation
- Operational simplicity (install, upgrade, debug)
- Performance and scalability
- Time to market
- Cost (infra, licensing, team effort)
- Compatibility with existing stack and standards (STIX 2.1, OWASP ASVS, etc.)
- Maintainability and team skill fit

Pick the 3 to 5 that actually matter for this decision. List them in order of importance. The reader should be able to tell which driver wins when two of them conflict.

## 3. Considered options
List every option that was on the table, including the ones rejected fast. The "no decision" option (do nothing) is also valid and should appear when relevant.

### Option A: <short name>

One paragraph description.

**Pros**: what this option gives us.
**Cons**: what it costs us.

### Option B: <short name>

Same structure.

### Option C: <short name>

Same structure.

> If an option was considered and dropped without deep analysis, say so and give the one-line reason. Do not leave a TBD on a published ADR.

## 4. Decision

We chose **Option X** because <one sentence linking the choice to the decision drivers>.

Then describe what this means concretely. Code snippets, config samples, sequence of operations, all welcome if they clarify the decision. Stay at the level of "what we do", not "how we implement every detail" (that goes in the design doc or the code).

## 5. Consequences
### Positive

What gets better. Write this in plain language, no marketing.

### Negative / trade-offs

What costs we accept. Be honest. An ADR with no negative consequences is suspicious.

### Neutral

What stays the same but is worth noting (operational steps unchanged, no impact on API contract, etc.).
