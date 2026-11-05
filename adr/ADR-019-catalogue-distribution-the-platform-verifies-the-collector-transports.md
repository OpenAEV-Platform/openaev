# ADR-019: Catalogue distribution — the platform verifies, the collector transports

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/5912, RFC 0005 (content marketplace), RFC 0004 (release integrity) |

## 1. Context

Filigran publishes payloads and scenarios as immutable releases of the `scenarios` repository: a
manifest with each version's hash, and the files. A collector brings each release to the platform.
Today the `openaev` collector overwrites payloads one by one, and nothing checks what it sends.
Content now comes from outside the tenant, so someone must check it before it enters.

## 2. Decision drivers

1. **Trust stays on the platform**: a compromised or misconfigured collector cannot make a tenant
   accept content that does not match its release.
2. **Simple collectors**: one call per release, no state kept in the collector.
3. **Each tenant decides for itself**: its own catalogue, its own last applied release.

Driver 1 wins over driver 2.

## 3. Considered options

### Option A: do nothing

Keep the payload upsert. **Pros**: no work. **Cons**: no release, no check, no scenarios.

### Option B: the collector verifies, the platform trusts it

**Pros**: the platform only imports. **Cons**: a compromised collector installs anything, and several
collectors feeding one tenant cannot tell an old release from a new one.

### Option C: the platform fetches the releases itself

**Pros**: no collector. **Cons**: the platform takes over the network work (mirrors, proxies,
tokens) that collectors already do. Rejected for now.

### Option D: the collector transports, the platform verifies

**Pros**: the check happens where the content lands, collectors stay simple. **Cons**: the platform
receives whole releases in one call.

## 4. Decision

We chose **Option D** because it keeps trust on the platform without moving the collector's
network work into it.

- The collector sends the manifest and every file of a release in one call.
- The platform checks each file against the manifest and refuses what does not match, alone or with
  the whole release.
- Each tenant keeps its own catalogue and refuses a release older than its last one.
- A refusal is reported to the collector and sent as a "Sync blocked" notification event.

The checks themselves are described in RFC 0005.

## 5. Consequences

### Positive

- The platform, not the collector, decides what enters a tenant.
- A broken release is visible on the platform, not only in the collector logs.

### Negative / trade-offs

- Without signing (RFC 0004 §5.1), the checks prove a file matches its release, not who made it.
- A release is held in memory during the call.
- Nobody is told of a refusal until someone subscribes to "Sync blocked".

### Neutral

- The `openaev` collector and its payload upsert keep working until the new collector replaces them.
