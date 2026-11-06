# ADR-020: Immutable payload versions under a shared identity

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8969, RFC 0005 (content marketplace) |

## 1. Context

Filigran will publish payloads as releases: version 1.0.0, then 1.1.0. A simulation built on 1.0.0 must keep running exactly that content when 1.1.0 arrives.
Today a payload is one row, changed in place, and its external ID, source and collector type sit on
that row, so a second version of the same payload has nowhere to go.

## 2. Decision drivers

1. **A version never changes once injects use it.**
2. **Local payloads keep working as today**: users still edit them in place.

## 3. Considered options

### Option A: do nothing

Keep one row per payload. **Pros**: no work. **Cons**: a catalogue update rewrites the content under
running simulations.

### Option B: a version column on payloads, rows grouped by external ID

**Pros**: no new table. **Cons**: local payloads without an external ID cannot be grouped, and the
per-tenant unique external ID forbids two versions.

### Option C: an identity shared by immutable version rows

**Pros**: a new version is a new row, the old ones stay untouched. **Cons**: one more table and one
more join on every payload read.

## 4. Decision

We chose **Option C** because it is the only one where adding a version never touches a row in use.

- `payload_identities` holds what every version shares: external ID, source, collector type.
- Each payload row is one version of an identity, numbered `payload_version`.
- Existing payloads become version 1.0.0 of their own identity.

## 5. Consequences

### Positive

- The catalogue can add versions without changing the ones simulations run.

### Negative / trade-offs

- External ID, source and collector type leave the `payloads` table: during a rolling deploy, pods
  of the previous release fail on payload reads and writes until they are replaced.

### Neutral

- Saving a local payload still changes it in place; only the catalogue adds versions for now.
