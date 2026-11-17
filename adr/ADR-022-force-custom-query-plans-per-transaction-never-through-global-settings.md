# ADR-022: Force custom query plans per transaction, never through global settings

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8734, #8764 (implementation), #8774 (open follow-up) |

## 1. Context

A *custom plan* is built with the actual parameter values of a query; a *generic plan* is built once,
without them. After 5 executions of the same query on a connection, the PostgreSQL JDBC driver prepares
it on the server. After 5 more custom plans, PostgreSQL may switch to a generic plan if it looks no
costlier than the average custom plan so far, and then keeps it for that statement on that connection.
This saves planning time.

For a cursor query run in a loop (`WHERE updated_at >= :from ... LIMIT :n`), a generic plan can be very
slow: it cannot know that the rows past the cursor are a thin slice of the table. This hit the
snapshot indexing of attack observations: 7 s per batch instead of 0.3 s (#8734). The symptom is
intermittent, since it depends on the pooled connection, and a plain `psql` run hides it, because
`psql` sends literal values and always gets a custom plan.

## 2. Decision drivers

1. **Fix the queries that need it without changing how every other query of the platform is planned.**
2. **Ship the fix in the code**, so that every instance, SaaS or on-premise, gets it by upgrading.
3. **No new privilege or instance configuration.**

## 3. Considered options

### Option 0: do nothing

**Cons**: affected batches stay slow and load the database, on every instance.

### Option A: rewrite the query or add an index

**Cons**: does not fix this cause. The query is fast once planned for the actual cursor, with the
existing indexes. #8764 also rewrote the query for a separate cost (`AS MATERIALIZED`); the generic plan
was still slow after it.

### Option B: `prepareThreshold=0` on the JDBC URL

**Pros**: one line. **Cons**: drops server-side prepared statements for every query of the platform, to
fix a few. A per-statement `setPrepareThreshold(0)` would be narrower, but Spring Data native queries do
not expose the JDBC statement.

### Option C: `plan_cache_mode` set on the database role or the database

**Pros**: no code. **Cons**: changes the planning of every query, and lives in each instance's
configuration instead of the code, so an upgrade does not bring it.

### Option D: force a custom plan in the transaction that runs the query

**Pros**: limited to that transaction; shipped in the code; needs no superuser; transaction-local, so it
never leaks to other work through the connection pool. **Cons**: one planning per execution; opt-in.

## 4. Decision

We chose **Option D** because it fixes the slow queries without touching the rest of the platform.

- **When**: a query run again and again on the same connection (a loop, a scheduled job) whose best
  plan depends on its parameter values, typically a cursor query, once a generic plan is shown to be
  the cause. It is not a default for every query.
- **How**: call `QueryPlanHelper.forceCustomPlan()` inside the transaction, last before the query, in a
  transaction that holds little else: the setting (`set_config('plan_cache_mode', 'force_custom_plan',
  true)`) applies to every query after it until the transaction ends. The helper throws outside a
  transaction. Add a test that asserts the setting after the call, as `AttackObservationIndexingTest`
  does.
- **Never** fix a slow plan with a global `prepareThreshold`, or with `plan_cache_mode` set on a role, a
  database or the server. Setting `prepareThreshold=0` because a transaction pooler does not support
  prepared statements is a separate matter and stays allowed.
- **Diagnose**: `PREPARE` the query in `psql`, then compare `EXPLAIN ANALYZE EXECUTE ...` under
  `SET plan_cache_mode = force_generic_plan` and `force_custom_plan`. Generic slow and custom fast: use
  the helper. Both slow: the query itself is the problem.

- **Operations**: nothing to configure or upgrade. A slow plan seen at a customer goes to engineering,
  not to a database setting.

Find the current callers with `grep -rn forceCustomPlan`.

This ADR does not decide how the indexing handles a full reindex, nor whether every indexing handler
should force custom plans: both are open in #8774.

## 5. Consequences

### Positive

- The plan of a cursor query fits the actual cursor, whatever ran before on the same connection.
- No instance configuration and no migration.

### Negative / trade-offs

- Every query after the call in the same transaction is planned again on each execution, which costs
  tens of milliseconds for a large query.
- Opt-in: a new query with the same problem stays exposed until someone adds the call.

### Neutral

- Query results do not change, only their plan.
- Needs PostgreSQL 12 or later; the code already requires 15.
