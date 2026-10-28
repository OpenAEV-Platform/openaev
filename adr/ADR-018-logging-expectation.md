# ADR-018: Logging expectation — validating that security tools record an attack

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8362 (backend), https://github.com/OpenAEV-Platform/openaev/issues/8363 (visibility), https://github.com/OpenAEV-Platform/openaev/issues/8639 (collectors and injectors) |
| Feature flag | `LOGGING_EXPECTATION` |

## 1. Context

OpenAEV validates two outcomes of a simulated attack: was it **prevented** (blocked) and was it
**detected** (a rule raised an alert). Both rely on **alerts**. They cannot answer a more basic question
customers ask first: did the attack leave a trace in the security stack at all? A security product
produces two different things: **events** (logs, recorded all the time, whatever happens) and
**alerts** (only when a detection rule reacts to events). An attack can be fully logged and never
alerted on; today OpenAEV reports it as "Not Detected" and cannot tell a missing log source from a
missing detection rule.

The expectation model was refactored before this feature: `BaseInjectExpectation` is a single table
discriminated by `inject_expectation_type` (a varchar), with `TechnicalInjectExpectation` (agent, asset,
asset group, signatures, traces) for Detection, Prevention and Vulnerability, and one
`ExpectationBehavior` per type. A new technical type therefore needs no schema change for the
expectation itself, but it crosses every layer: contracts, execution, collectors, injectors, result
computation, dashboards, the attack path and OpenCTI.

The first version targets one SIEM (**Splunk ES**) and one EDR.

## 2. Decision drivers

In priority order:

1. **No false "Not Logged".** A result must only fail when a tool that can answer has looked and found
   nothing. A wrong failure is worse than no result.
2. **Break nothing.** Detection and Prevention keep their behaviour, and collectors and injectors that
   are not upgraded keep working, in any version mix.
3. **Never store log content.** Evidence is a link and metadata, not the customer's raw events.
4. **Consistency with Detection and Prevention.** Wherever they appear, Logging appears, with the same
   mechanics (tree, scoring, expiration, manual results).
5. **Progressive rollout.** Everything ships behind a feature flag and can be enabled per platform.

## 3. Considered options

### Q1. How to model "logged"

- **A. A status or metadata on the Detection expectation.** No new type, but it mixes two answers from
  different data (alerts vs events) in one result, and every Detection consumer would have to learn the
  difference. Rejected.
- **B. A new technical expectation type `LOGGING`.** ✅ Chosen (§4.1): it reuses the technical
  expectation machinery and stays independent from Detection.

### Q2. How collectors fetch LOGGING expectations

- **A. Return them from the existing `GET /api/injects/expectations/assets/{sourceId}`.** No new route,
  but collectors that are not upgraded would receive a type they cannot handle (pyoaev turns unknown
  types into Prevention expectations). Rejected.
- **B. A dedicated endpoint.** ✅ Chosen (§4.2): only upgraded collectors ever see LOGGING.

### Q3. How to avoid waiting on collectors that cannot answer

- **A. Accept it.** Every SIEM / EDR collector gets a pending slot and the expectation expires to
  "Not Logged" when one of them never answers. Violates driver 1. Rejected.
- **B. Collectors declare the expectation types they support.** ✅ Chosen (§4.2).

### Q4. What a collector searches

- **A. Alerts, like Detection.** Answers "detected", not "logged". Rejected.
- **B. Raw events, matched on the attack's signatures.** ✅ Chosen (§4.3).

### Q5. Which injectors offer LOGGING

- **A. Every injector that offers Detection or Prevention.** Most external injectors send no
  signatures, so collectors would have nothing to search for and every result would end "Not Logged".
  Rejected.
- **B. Only injectors that send signatures.** ✅ Chosen (§4.4).

### Q6. What happens to existing content

- **A. New content only.** Simplest, but existing scenarios, the bulk of customer value, would never
  validate logging. Rejected by product.
- **B. Available but unchecked on existing injects.** Users would have to edit every inject by hand.
  Rejected by product.
- **C. One migration adding LOGGING to every existing inject.** Runs ahead of the contracts: an inject
  of an external injector still on an old pyoaev would carry a type its injector cannot handle, and the
  injector would send no signatures at all, breaking Detection and Prevention too. Rejected.
- **D. Backfill the injects of a contract when that contract starts offering LOGGING.** ✅ Chosen
  (§4.5).

## 4. Decision

We add a **`LOGGING` technical expectation**, fetched by collectors through a **dedicated endpoint**,
answered only by **collectors that declare it**, matched on **raw events**, offered only by contracts
whose executions carry **signatures**, and **backfilled on existing injects** as their contracts gain it.
This is the only combination that never produces a false "Not Logged" and never changes what older
collectors and injectors receive.

### 4.1 Model and lifecycle (platform)

- `EXPECTATION_TYPE.LOGGING`, entity `LoggingInjectExpectation` and `LoggingBehavior` extending
  `AbstractTechnicalBehavior`, like Detection. Labels **Logged** / **Not Logged**: a single result has
  no partial state; an aggregate over several targets can be **Partially Logged**, like Partially
  Detected.
- The tree is the Detection one: one result slot per capable collector on each agent leaf, asset and
  asset group scores recomputed from their children with the `expectation_group` AND / OR rule.
- **One source is enough**: LOGGING joins Detection and Prevention in `isSettledByAnySource`
  (`ExpectationResultBuilder`): the first "Logged" from any collector settles the leaf.
- Expiration: `openaev.expectation.logging.expiration-time`, falling back to the technical expiration,
  exposed in `PlatformSettings`. An expired slot becomes "Not Logged".
- Every place that lists technical types explicitly learns LOGGING: payload default expectations, the
  contract builder (`PayloadService.createContractExpectationsBasedOnPayload`, whose default branch
  throws), result restitution (`InjectExpectationResultUtils.computeExpectationResults`), the target
  status `target_logging_status`, chaining (`InjectExecutionStep`) and manual results.

### 4.2 Collector contract

- **Registration**: `CollectorCreateInput` gains `collector_supported_expectation_types` (stored on
  `Collector`, with a migration). When execution seeds pending results
  (`filterCollectorsForExpectation`), a LOGGING slot is created only for collectors that declared
  LOGGING. A collector that declares nothing keeps its current behaviour for every other type.
- **Fetch**: `GET /api/injects/expectations/logging/{sourceId}` returns the LOGGING expectations this
  collector has not answered yet, with their signatures, in the same output format as `/assets/{sourceId}`.
  It returns an empty list while the flag is off. `/assets/{sourceId}` keeps returning Prevention and
  Detection only.
- **Answer and evidence**: unchanged routes, `PUT /api/injects/expectations/bulk` and
  `POST /api/inject-expectations-traces/bulk`.

Version mix:

| Platform | Collector | Result |
| --- | --- | --- |
| Older | Upgraded | The registration field is ignored (unknown JSON properties are accepted); the LOGGING route answers 404, which pyoaev treats as "no LOGGING" with a warning |
| Upgraded, flag off | Upgraded | Empty LOGGING list |
| Upgraded, flag on | Older | Declares nothing: never awaited on LOGGING, never fetches it |

### 4.3 Matching and evidence

- Collectors search **events**, not alerts, between the execution start and end dates.
- A match is the implant **marker** (strong) **OR** the target **IP** (weak, reported as "IP only").
  Only the execution window narrows the search.
- The trace stores the search link, the time of the first matching event and the match type, **never**
  event content.
- "Not Logged" only comes from an expired slot or an explicit answer after a completed search. An
  authentication, licence or API failure disables the search with a warning and produces no result.

### 4.4 Who answers and who offers LOGGING

**pyoaev**

- `LoggingExpectation` model, independent from `DetectionExpectation`, and
  `logging_expectations_for_source`. Unknown expectation types are skipped with a warning instead of
  becoming Prevention expectations.
- LOGGING in the contract `ExpectationType` and signature `ExpectationType` enums, and in
  `ExtraSignatureData.get_extra()`, which raises on unknown types today.
- Supported types are sent at registration and are **opt-in per collector**: the collectors release
  script bumps pyoaev in every collector, so a default declaration would make collectors claim a type
  they cannot answer.

**Collectors (answer)**

- **Splunk ES**: a separate flow next to the unchanged `notable` (alerts) flow, enabled by
  `SPLUNKES_LOGGING_ENABLED`. The customer lists their raw event indexes in a configuration variable;
  the search looks for the marker or the IP in `_raw`, with a result limit.
- **EDR**: a new query strategy on the EDR's raw event search, next to the unchanged alerts strategy,
  enabled by its own `*_LOGGING_ENABLED` variable. Event searches are often asynchronous and
  rate-limited: they are batched per cycle and kept within the API limits. The event search may
  require an additional licence.
- **No retry and no local verdict**: a search without a match sends nothing. The expectation comes back
  at the next cycle, until a match or until the platform expires it to "Not Logged"; a collector's own
  end-of-window handling never writes a LOGGING result. A backoff between cycles limits the load on the
  searched tool.

**Contracts (offer)**

- **Payloads**: `payload_expectations` gains LOGGING and the payload contract offers it pre-checked.
  Imported payloads are rewritten at every sync, so their collectors send it: **atomic-red-team**
  (hardcoded list) and **openaev** (the `OpenAEV-Platform/payloads` manifest and its default expected
  security platforms). While the flag is off, the payload upsert drops LOGGING instead of storing it.
- **External injectors**: their contract is replaced by what the injector sends at every registration,
  so LOGGING is declared in their code. Only injectors that send signatures offer it: **nmap, netexec,
  nuclei**. The others (http-query, aws, stratus, email, slack, teams) wait until they send signatures;
  ai-redteam keeps its own flow. Built-in Java contracts other than payloads (phishing landing page) do
  not offer LOGGING in this version.

### 4.5 Existing content

Product decision: existing injects get LOGGING by default; past results are never modified.

- An inject gets LOGGING only once **its contract offers it**. The platform then adds a LOGGING
  expectation to every inject of that contract that has Detection or Prevention: score and group copied
  from its Detection expectation, expected platforms from the contract. Injects that already have
  LOGGING are skipped.
- Three triggers, each firing when a contract **newly** offers LOGGING:
  - a one-shot `RuntimeMigration` per tenant for payload contracts, whose `doMigrate` returns `false`
    while the flag is off so that it only completes once the flag is on;
  - injector registration (`InjectorService`, where the contract content is replaced);
  - payload upsert, when the regenerated contract gains LOGGING.
- Injects of finished or canceled simulations are not modified: they never run again.
- Writes are batched. A user who removes LOGGING from an inject does not get it back, since the
  triggers only fire on a contract change. After the backfill, injects and contracts agree, so drift
  detection needs no special case.

### 4.6 Restitution

Logging appears wherever Detection appears, with the same layout:

- **Execution views**: result tiles, target details (with match type and search link), manual results,
  simulation validation.
- **Aggregations**: scenario and simulation scores, ATT&CK coverage (`logging_success` /
  `logging_total`), attack observations, scenario degradation notifications, home and custom
  dashboards, reports, asset and security platform pages, starter pack dashboards.
- **Attack path**: a `logging_status` per execution and a new severity between Detected and nothing:
  **GREEN** prevented > **ORANGE** detected > **YELLOW** logged only > **RED** not even logged. The
  colour is computed by the backend; the frontend maps it.
- **OpenCTI security coverage**: `SecurityCoverageService.computeCoverage` sends a `LOGGING` score next
  to Prevention and Detection. `coverage_name` is a free string on the OpenCTI side, whose display is
  built by the OpenCTI team.
- **Frontend**: the duplicated type → label / colour maps are merged into shared helpers first, then
  LOGGING is added there once (labels, icon, colours, i18n).

### 4.7 Feature flag and rollout

`LOGGING_EXPECTATION` (`PreviewFeature`, `isFeatureEnabled('LOGGING_EXPECTATION')` in the frontend).
While it is off: no LOGGING is created or offered, execution ignores LOGGING found in inject content,
the collector endpoint returns an empty list, the payload upsert drops LOGGING, nothing is sent to
OpenCTI, and the backfill waits.

Delivery order: the type and flag first, then the collector contract (its route, field and trace format
are frozen up front so pyoaev can be built in parallel), then the platform wiring. pyoaev is released
before the Splunk ES, EDR, injector and payload collector changes.

## 5. Consequences

### Positive

- Customers can tell a missing log source (Not Logged) from a missing detection rule (Logged, Not
  Detected).
- No false "Not Logged" from collectors or injectors that cannot answer, whatever the version mix.
- Existing scenarios validate logging without manual edits.
- Older collectors, injectors and platforms keep working unchanged.

### Negative / trade-offs

- Coverage depends on what the customer feeds their tools: Splunk ES needs the right event indexes
  configured, and the EDR event search may need an additional licence.
- An "IP only" match is weak evidence: other traffic from the same IP in the window can match.
- Most external injectors do not offer LOGGING until they send signatures.
- One more expectation type in every aggregation and screen, and one more collector flow per product to
  maintain.
- The backfill has three triggers instead of one migration.

### Neutral

- No schema change for expectations themselves (single-table discriminator); one new column on
  collectors and one on attack path executions.
- Detection and Prevention routes, payloads and results are unchanged.
- Past results are never modified.
