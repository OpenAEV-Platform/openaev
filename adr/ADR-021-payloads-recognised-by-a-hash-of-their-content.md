# ADR-021: Payloads recognised by a hash of their content

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8969 |

## 1. Context

When a scenario zip is imported, each payload in it is either linked to an existing payload or created.
The importer guessed "same payload" from the name and a few fields, so it could link a payload whose
content differs. The scenarios repository needs the same answer to publish its catalogue.

## 2. Decision drivers

1. **Never link a payload whose content differs.**
2. **The same answer in the platform and in the scenarios repository.**
3. **No migration that reads every stored file.**

## 3. Considered options

### Option A: keep the guess

**Pros**: no work. **Cons**: links different payloads, misses identical ones.

### Option B: compare candidates field by field at import

**Pros**: exact. **Cons**: loads every candidate and its files on each import; another repository
cannot reuse it.

### Option C: a hash of the payload's canonical form, stored on the payload

**Pros**: one indexed lookup; any repository computes the same hash. **Cons**: the list of hashed
fields becomes a contract.

## 4. Decision

We chose **Option C** because it is exact and shareable with one lookup.

- The hash is the SHA-256 of the payload's JSON written by RFC 8785, plus the techniques of its
  contract. A file enters by the SHA-256 of its bytes.
- Every persistent field says whether it enters, with `@Hashed` or `@NotHashed`; a test fails when
  a field says neither.
- `NULL` means "not computed yet": a document gets its hash on first use, an old payload when a zip
  imports a payload of the same name. A payload with an unreadable file keeps `NULL` and is never
  linked.
- Changing what enters the hash changes every hash. The same pull request updates the shared test
  vectors, here and in the scenarios repository, and adds a migration that sets
  `payload_content_hash` to `NULL` so the hashes are computed again.

## 5. Consequences

### Positive

- An import links only identical payloads.

### Negative / trade-offs

- The hashed fields are a contract with the scenarios repository.
- A payload whose file cannot be read is hashed again at each import of its name.

### Neutral

- The hash is not exposed by the API.
