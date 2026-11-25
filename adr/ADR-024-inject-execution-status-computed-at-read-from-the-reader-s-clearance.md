# ADR-024: Inject execution status computed at read from the reader's clearance

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8559 |

## 1. Context

An inject stores one execution status (`injects_statuses.status_name`), computed over every agent it ran
on. With markings, a reader may not be cleared for some of those agents: an inject that succeeded on a RED
agent and failed on a GREEN one is stored PARTIAL, and a GREEN reader who sees only the failure must not
learn, from the status, that something succeeded where they cannot look.

The stored status is also read by the system, which acts on every target: inject dependencies, the
chaining engine, the simulation auto-finish, the search index. They need the real outcome, not one
narrowed to a reader.

## 2. Decision drivers

1. **No leak**: the status a person sees tells nothing about the agents their clearance hides.
2. **The system keeps the real outcome**: background consumers are unchanged.
3. **No breaking change**: `status_name` stays in the API, the enum and the index.
4. **One source of truth**: the marked rows, not copies per clearance.
5. **Cost on the read path stays small**, and nothing changes when the MARKING feature is off.

## 3. Considered options

### Option A: store the system verdict, compute the reader's status at read

**Pros**: no migration; background consumers keep their meaning; the launcher and admins read the stored
value as is. **Cons**: every place that shows the status to a person must recompute it; filtering and
sorting on the stored verdict leak it, so they must be refused or recomputed.

### Option B: store the lifecycle only, always compute the verdict

**Pros**: one truth, the traces. **Cons**: breaking for the enum, the API (`status_name` read by clients
and connectors) and the index; every background consumer recomputes; SQL filters and dashboards lose
their column.

### Option C: store one verdict per clearance

**Pros**: the read path stays a join. **Cons**: the combinations grow with the markings; re-marking an
asset invalidates them; it copies the marking out of the marked row.

## 4. Decision

We chose **Option A** because it hides nothing from the system and breaks no contract, at the cost of a
recompute in the few places that show the status to a person.

- `status_name` keeps the verdict over every agent. The write path does not change.
- The status is computed in the API layer, in the reader's transaction, whose marking filter already
  hides the traces of the agents above their clearance. One rule (`InjectStatusUtils.visibleStatus`)
  serves every place, so they cannot disagree: compare the agents the inject was dispatched to
  (`status_expected_agent_count`, stored at launch) with the agents whose final trace the reader sees.
  All seen: the stored status. None seen: `NOT_VISIBLE`, a display value never stored. Some seen: the
  status of the visible traces.
- Five places show the status: the inject searches, the status mappers, the attack path, the
  endpoints returning the inject entity, and the stream. Lists add one batched query per page, backed by
  a partial index on the final traces.
- With the MARKING feature off, every place returns the stored status, with no query and no recompute.

## 5. Consequences

### Positive

- A reader learns nothing about the agents above their clearance from the status.
- Dependencies, chaining, auto-finish and the index keep the real outcome.

### Negative / trade-offs

- One more query per page of injects while markings filter.
- The status filter and sort of the API compare the stored verdict, so they leak it. No screen offers
  them, and filtering on the reader's status would cost about 1.5 s per request: to be addressed in
  #9538.
- A new place that shows the status must use the rule, or it shows the stored verdict.
- The endpoints returning the inject entity overlay the status on its JSON to keep their public shape,
  until smaller outputs replace them (#9439).

### Neutral

- No change to the API types, the stored values or the index: `NOT_VISIBLE` is hidden from the API schema.
- Notifications still match the stored status (#8924).
