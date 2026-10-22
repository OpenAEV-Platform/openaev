# ADR-017: Query sensitive finding values on their displayed form

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7893 |
| Builds on | #7500 (masking of the API output), #7892 (masking of the search index) |

## 1. Context

A finding keeps its value in cleartext in `findings.finding_value`: deduplication, correlation and the
attack path compare real values, so the database must hold them. Since #7500 the API only hands out a
masked form, so a credential `admin:motdepasse` is displayed as `admin:mo******`.

The query engine did not follow. `Finding.value` carried `@Queryable(searchable, filterable, sortable)`,
so text search, filters, sorts and notification triggers all ran on the cleartext column. The secret
was never displayed, but the platform answered questions about it. Searching `motdepasse` returned the
finding; a `starts_with` filter rebuilt it letter by letter; an `eq` filter tested a dictionary of
guesses; a sort placed it next to a planted value; a trigger "value contains X" fired only on the right
guess; and distinct mode grouped by cleartext, so two rows reading `jdoe:Su******` revealed that the two
passwords differ. Each of these is an oracle: a yes/no answer that leaks what the screen hides.

## 2. Decision drivers

In priority order:

1. **No oracle.** No search, filter, sort, grouping or trigger may depend on a part of a value the
   platform does not display.
2. **Displayed parts stay usable.** Searching the visible username (`admin`) or the visible fragment
   (`mo`) must still find the finding, as it does in the search index since #7892.
3. **One place.** Every consumer of the query engine (the five search endpoints, distinct mode,
   notification triggers) must be covered by a single change, without new code in the deprecated
   `openaev-framework`.
4. **One masking rule.** The rule lives in `SensitiveValueMaskingUtils`; it must not be copied
   elsewhere, where it would drift.

## 3. Considered options

### Option A: store the masked value in a column

A new column `findings.finding_value_display` holds the masked value, computed by
`SensitiveValueMaskingUtils` on every write. The `finding_value` query key moves to it.

**Pros**: search, filters, sorts, the scoped endpoints, distinct mode and triggers are covered at once;
behaviour matches the search index; the column can be indexed; the masking rule is not duplicated.
**Cons**: a new column and a backfill; every write path must fill it, including the native upsert of
agent findings; stored values must be rewritten when the masking rule changes.

### Option B: compute the mask in SQL

A `CASE` / `split_part` expression rebuilds the masked value inside each query.

**Pros**: no new column.
**Cons**: the masking rule is duplicated in SQL and drifts from the Java one; the expression cannot use
an index. Rejected on drivers 4 and performance.

### Option C: exclude sensitive types from value queries

Value search, filters and sorts skip every sensitive finding.

**Pros**: trivial.
**Cons**: searching `admin` no longer finds `admin:mo******`. Rejected on driver 2; at best a temporary
safety net.

## 4. Decision

We chose **Option A**, because it removes every oracle in one place while keeping the displayed parts
searchable, with a single masking rule.

Concretely:

- `findings.finding_value_display` is **the single masked representation** of a finding value. Display
  (`FindingMapper`), search, filters, sort, distinct grouping and triggers all read it. The cleartext
  `finding_value` stays for deduplication, correlation and the attack path, so the column can always
  be rebuilt from it.
- On the entity, the cleartext field loses `@Queryable` and is hidden from JSON. The masked field
  carries `@Queryable` under the same key `finding_value`, so the front end and saved filters keep
  working. A dedicated field is needed rather than `@Queryable(path)`, because sorting resolves the Java
  field name and ignores `path`.
- One helper computes the value with `SensitiveValueMaskingUtils.maskIfNeeded(type, value)`. JPA writes
  fill it in `@PrePersist` / `@PreUpdate`; the native upsert receives it from `FindingWriter`; a
  migration backfills existing rows.
- A missing value (for example a row inserted by a pod of the previous version during a rolling
  deploy) is displayed with the mask computed on the fly, never with the cleartext, and is absent from
  value search until it is filled. The system fails closed.
- Distinct mode and the finding summary group by type and masked value, so `jdoe:Su111` and
  `jdoe:Su222` form one row, and its summary counts the occurrences of both.
- A change of the masking rule bumps `MASKING_VERSION` and ships one migration that rewrites the column,
  then requests the search index re-feed, in that order. Automating this step is a follow-up.

## 5. Consequences

### Positive

- No query can confirm or rebuild a hidden part of a value, whatever the operator.
- Display, queries and the search index share one masked form, so they cannot disagree.
- The entity's JSON no longer carries the cleartext, which protects any future serialization of it.

### Negative / trade-offs

- Two secrets with the same masked form are grouped into one row, with the assets of both.
- Until the masking rule change is automated, it relies on a migration written by hand.

### Neutral

- The API contract is unchanged: `finding_value` keeps its name and its masked content.
- Values that are not sensitive are stored identically in both columns, so their behaviour does not
  change.
