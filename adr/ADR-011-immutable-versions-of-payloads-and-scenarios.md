# ADR-011: Immutable versions of payloads and scenarios

|  |  |
| --- | --- |
| Status | Proposed |
| Related | [RFC 0005 — Content marketplace](https://app.notion.com/p/3f38fce17f2a8171be45cf7bb56d27f9) · [PRD — Payload & Scenario Marketplace](https://app.notion.com/p/3f18fce17f2a81ff9462d11e474e52c2) · [RFC 0004 — Release trust chain](https://app.notion.com/p/3f28fce17f2a81299755e8af1e762fab) |

## 1. Context

The platform matches payloads by `payload_external_id` and overwrites them on every collector run. A scenario tested against one payload content silently runs another, a broken or dangerous payload cannot be revoked (`DEPRECATED` blocks no execution), and scenarios that embed the same payload each import their own copy, so customers end up with duplicates.

Payloads and scenarios are now distributed from immutable releases with content hashes (RFC 0004) and installed from a marketplace (PRD). The platform needs a data model where a published version never changes, can be shared, and can be withdrawn.

## 2. Decision drivers

1. Data correctness: what runs is what was tested; nothing installed is overwritten.
2. Security: a bad version can be withdrawn everywhere, and a mistaken withdrawal can be undone.
3. No new duplicates: one payload version is stored once per tenant and shared.
4. Migration of existing content without breaking injects, scenarios or past simulations.
5. Small change to the existing model (payload ↔ contract 1:1, inject → contract).

## 3. Considered options

### Option A: keep updating in place

Status quo: one row per payload, upserted by external ID.

**Pros**: nothing to build.
**Cons**: silent changes under tested scenarios, no revocation, no rollback.

### Option B: content-derived IDs, payloads copied per scenario

Every content change gets a new external ID, and each scenario carries its own copy (first draft of RFC 0004).

**Pros**: works with today's importer, no schema change.
**Cons**: more duplicates, a bad payload cannot be revoked across its copies, scenario versions cannot share a payload version.

### Option C: stable ID + version + content hash, versions side by side

Each published version is its own row, identified by external ID and version, checked by a content hash, and shared by every scenario version that references it.

**Pros**: immutable versions, sharing, revocation by (ID, version), rollback by using the older version.
**Cons**: schema change on `payloads`, `scenarios` and `injects`, rows grow with every version.

## 4. Decision

We chose **Option C** because it is the only one that gives correctness, revocation and deduplication together, on top of the existing payload ↔ contract and inject → contract links.

- A payload version is one `payloads` row with its own injector contract. Unique key: `(payload_external_id, payload_version, tenant_id)`; a partial unique index keeps one unversioned row per external ID.
- A managed version (non-null `payload_version`) is read-only server-side and cannot be deleted. Editing means duplicating into a local copy (null version, link to its origin).
- Changing version repoints injects (and chaining steps) to the other version's contract. A scenario version references exact payload versions.
- Identity is checked by a content hash: SHA-256 over the canonical JSON of the payload (RFC 0005, Appendix B). Linking an existing or imported payload to a version requires the same external ID and the same hash; otherwise it stays a local copy.
- Revocation is stored at platform level (`content_revocations`, no tenant) and checked before every execution. A revoked version is hidden, not deleted, so lifting a revocation restores it.
- Scenarios get `scenario_catalog_id` and `scenario_version`; injects get `inject_catalog_id`, stable across versions.
- The platform, not the collector, runs the RFC 0004 checks before anything is installed.

Details, rules and the delivery plan are in RFC 0005.

## 5. Consequences

### Positive

- An installed version never changes; a fix is a new version installed next to the old one.
- One payload version per tenant, shared by every scenario that uses it.
- A revocation stops execution everywhere and can be lifted.

### Negative / trade-offs

- Schema changes and a background migration; hard to reverse once data is written.
- Several versions coexist: rows grow, payload names are no longer unique, so the UI always shows the version.
- Managed payloads are read-only; users duplicate to edit.

### Neutral

- Simulations keep reusing the scenario's contract; a version change on a scenario never touches a created simulation.
- Existing duplicates without an external ID are not merged.
