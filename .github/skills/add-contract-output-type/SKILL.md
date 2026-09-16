---
name: add-contract-output-type
description: >-
  Adds a new contract output type (finding type) end to end: model enum, output processor,
  chaining registry, frontend, and the matching injector-side declaration. Use when asked to
  support a new kind of finding (file, certificate, registry key…) produced by an injector.
---

# Add a contract output type

A contract output type is what an injector declares it produces and what the platform stores as a
`Finding`. Adding one touches **three repositories** — the type must exist in all of them before an
injector can emit it, or the output is silently dropped at parse time.

## Prerequisites

Answer these before writing code; they decide half the steps below.

- **Primitive or complex?** A primitive is a single scalar the chaining engine can compare directly
  (`text`, `number`, `port`, `ipv4`). A complex type is an object with named sub-fields (`share` =
  host + share_name + permissions). The test is not "is the display value one word" but "does the
  finding need to carry context to be correct". A **bare** file name is a primitive, but a real
  `file` finding is **complex**: the same basename means different things by location, so it carries
  `file_name` + `path` + `share` (empty for local files) + `host`. Its `toFindingValue` is the full
  location (unique dedup key) while the front shows only the basename; the `share` sub-field reuses
  `PrimitiveType.ShareName` so a share-hosted file links back to its `share` finding. Defaulting to
  "a name is a primitive" is the trap — a lone scalar cannot distinguish a share file from a local
  file, nor link to its share.
- **Which injector will actually produce it?** A type with no producer is dead weight that misleads
  the UI into showing a category that can never fill. Land the producer in the same milestone.
- **Does it feed an attack-path summary card?** If not, the generic auto-generated card already
  covers it and you skip step 7.

Never relabel one type as another to make a card look populated. Add the type, or rename the card.

## Procedure

### Step 1 — Platform enum (openaev)

`openaev-model/src/main/java/io/openaev/database/model/ContractOutputType.java`

```java
  @JsonProperty("file")
  File("file"),
```

The `@JsonProperty` label is the wire format and the value stored in `finding_type`. It is a public
contract: never rename an existing one without a Flyway migration rewriting stored findings.

### Step 2 — Shared enum (client-python)

`pyoaev/contracts/contract_config.py`, class `ContractOutputType`:

```python
    File: str = "file"
```

Every injector and collector depends on this package. A type missing here cannot be declared by any
Python injector, and the release must ship before (or with) the injector that uses it.

### Step 3 — Output processor (openaev)

Create `openaev-api/src/main/java/io/openaev/output_processor/<Name>OutputProcessor.java`, modelled
on `ShareOutputProcessor` (complex) or `TextOutputProcessor` (primitive). Annotate `@Component` —
`OutputProcessorFactory` auto-registers every `OutputProcessor` bean by its type, so there is no
list to edit, but a **missing processor means the output is logged and discarded**.

Implement:
- `validate(JsonNode)` — the mandatory sub-fields.
- `toFindingValue(JsonNode)` — the human-readable value stored on the finding. Keep it stable: it is
  the deduplication key everywhere downstream (counters, graph node ids, kill-chain matching).
- `toFindingAssets(JsonNode)` — when the payload carries `asset_id`.

### Step 4 — Chaining engine (openaev)

Two files, both in `openaev-model/.../database/model/`:

1. `PrimitiveType` — add the scalar(s) the type exposes (e.g. `FileName("file_name")`). Complex
   types expose their sub-fields as primitives; this is what a later step can consume.
2. `ChainingOutputType` — register the type in the static block:
   `registerPrimitive(ContractOutputType.File, PrimitiveType.FileName)` or
   `registerComplex(ContractOutputType.X, ComplexType.X)` (add the `ComplexType` constant too), then
   declare the sub-field → primitive map in `ChainingTypeRegistry.CONTEXTUAL_COMPLEX_FIELD_PRIMITIVES`.

An unregistered type throws at chaining resolution — this registry is the single source of truth.

### Step 5 — Frontend (openaev)

- `openaev-front/src/admin/components/findings/ContractOutputElementType.ts` — add the label.
- `openaev-front/src/components/FindingIcon.tsx` — add a `case` with its icon; without it the finding
  renders with the fallback glyph.
- Regenerate `openaev-front/src/utils/api-types.d.ts` with `yarn generate-types-from-api` (backend
  running). Do not hand-edit unless the change is a single field rename.

### Step 6 — Injector side (injectors repo)

For a Python injector, mirroring `netexec/`:

- `contracts/contract_outputs.py` — a `ContractOutputElement(type=ContractOutputType.X, field="…",
  isMultiple=…, isFindingCompatible=True, labels=[…])`.
- `contracts/output_registry.py` — map the option/module to the type set, e.g. `"shares": {TEXT, SHARE}`.
- `helpers/*_output_parser.py` + an extractor returning the dicts whose keys **exactly match** the
  processor's fields from step 3. A key mismatch fails `validate()` silently.
- Declare the output on the contract so the UI can chain from it.

  **Output on stdout vs. a file.** Most netexec parsers read the finding from stdout lines. Some
  modules write structured data to a **file instead** — `spider_plus` prints only stats to stdout
  and dumps the per-file list to a JSON metadata folder. For those: force a controlled output path
  in the command builder (mirror `OPTIONS_REQUIRING_OUTPUT_FILE`, e.g. force `-o OUTPUT_FOLDER=<tmp>`
  for `spider_plus`), record it on the parsed data, then in `openaev_netexec.execute` read that
  file/folder after the run, parse it into findings, merge them into `parse_result["outputs"]`, and
  clean it up in `finally`. Do NOT append raw JSON to stdout for the line parser to pick up — a
  dedicated JSON parser keyed on the file name (netexec writes `<ip>.json`) is the clean path.

### Step 7 — Attack path (only if it gets its own card)

`openaev-api/src/main/java/io/openaev/service/attackpath/AttackPathGraphService.java`:
- `categoryTypes()` — map the plural category to the type.
- Both counter switches (full graph and `collapsedCounters`) — add a `case`.
- `AttackPathCounters` — add the field, then the front's `CATEGORY_OF_TYPE`,
  `COVERED_FINDING_TYPES`, `FILTER_TO_FINDING_TYPES` and the card list.

If a step consumes a complex type's sub-field, add the key → type reconciliation in
`AttackPathKeyMatcher.KEYTYPE_TO_FINDING_TYPE` **and** verify the operator: `EQ` compares the key
against the whole formatted finding value, so a sub-field key only matches today through `IN`'s
single-token substring fallback.

### Step 8 — Tests

- Processor unit test: `validate` rejects incomplete payloads, `toFindingValue` formats as expected.
- Chaining: the type resolves to the right kind and primitives.
- If step 7 applies, an integration test asserting the type survives every attack-path read
  (endpoint expand, graph node, drawer, execution detail, counter) — see
  `AttackPathShareTypeApiTest`, including its "two types are counted separately" case.

## Verification

```bash
# backend
docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-21-noble \
  mvn -q -pl openaev-model,openaev-api -am test -Dtest='*OutputProcessor*,*ChainingType*'
# frontend
cd openaev-front && yarn eslint src && yarn tsc --noEmit
```

## Pitfalls

- **Adding the type in one repo only.** The platform enum and `client-python` must agree; otherwise
  the injector's declaration fails to deserialize and the contract is rejected.
- **Presenting type A as type B to fill a card.** It corrupts counters (two different findings count
  as one category) and kill-chain matching (a consumed key matches the wrong finding). If the real
  type is not there yet, rename the card instead.
- **Changing `toFindingValue` later.** Findings deduplicate on the value, so a format change orphans
  every stored finding and doubles the counters until re-ingestion.
- **Treating a complex type as a primitive.** Its finding value is the formatted whole (e.g.
  `\\host\share (READ,WRITE)`), so an `EQ` on a sub-field never matches.
- **Declaring an output type is not the same as producing findings.** Adding the type to the
  registry only makes the `ContractOutputElement` appear on the contract; a finding is created only
  if an extractor/parser actually populates that field's list. netexec's `text` output, for
  instance, is declared on almost every contract but has no extractor in `_DISPATCHERS`, so it never
  yields `text` findings — the raw stdout surfaces in the execution result, not as findings. Land
  the type AND its extractor together, or the card/counter stays empty even though the contract
  advertises the type.
