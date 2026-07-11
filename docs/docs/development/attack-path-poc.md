# Attack path POC

!!! warning "Proof of concept, feature-flagged"

    The attack-path execution store is a proof of concept (issue 6647). It is off by default and gated
    behind the `ATTACK_PATH_POC` preview feature, so its tab and endpoints do not exist unless the flag
    is on. See `adr/ADR-002-attack-path-execution-store-on-postgresql.md` at the repository root for the
    design and the measured results.

## What it is

A chaining simulation's attack path is stored in three normalized PostgreSQL tables
(`attackpath_execution`, `attackpath_finding`, `attackpath_execution_finding`) instead of the
`step_data` JSONB blob, so the interactive graph rebuilds from flat, indexed reads. The POC proves the
store, the rebuild, per-simulation read performance, tenant isolation, and a collapsed (aggregated)
mode for large simulations.

The front adds an **Attack path** tab to a simulation, rendering the graph with React Flow.

## Enable the feature

Add `ATTACK_PATH_POC` to the enabled preview features (or `*` for all), for example in
`application-dev.properties`:

```properties
openaev.enabled-dev-features=ATTACK_PATH_POC
```

Then start the backend and the front (`cd openaev-front && yarn start`). The tab appears on every
simulation page.

## Seed demo data

The `attackpath_*` tables are tenant-active, so a simulation's rows are only visible under **your own
tenant**. The seed endpoint therefore takes a `tenantId`: pass yours (the single-tenant default is
`2cffad3a-0001-4078-b0e2-ef74274022c3`, and it is also the `{tenantId}` segment in the URLs the front
calls) so the seeded simulations show up in your tab.

`POST /api/poc/attack-path/seed` is admin-only. Authenticate with the admin token
(`openaev.admin.token`) or your logged-in session.

| preset   | shape                                                   | ~rows   | seed time |
|----------|---------------------------------------------------------|---------|-----------|
| `smoke`  | 6 tiny simulations                                      | ~1k     | instant   |
| `nav`    | 1 simulation, ~100 endpoints (smooth to navigate)       | ~2k     | ~1 s      |
| `medium` | 20 simulations, 100–450 endpoints, one 100k outlier     | ~0.5M   | ~100 s    |
| `large`  | 80 simulations (volume-scaling comparison)              | ~2M     | minutes   |
| `full`   | ~200 simulations, the 100k/300k/500k outliers           | ~5.8M   | ~20 min   |

`medium` alone gives the full spectrum for a front demo. Use the `AttackPathBenchmark` harness (not the
endpoint) for `full`.

```bash
TENANT=2cffad3a-0001-4078-b0e2-ef74274022c3
TOKEN=<your openaev.admin.token>

# the full spread: 20 simulations from ~100 to ~450 endpoints (one 100k-execution outlier)
curl -X POST http://localhost:8080/api/poc/attack-path/seed \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"preset\":\"medium\",\"seed\":7,\"tenantId\":\"$TENANT\"}"

# a light ~100-endpoint simulation, smooth for testing navigation
curl -X POST http://localhost:8080/api/poc/attack-path/seed \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"preset\":\"nav\",\"seed\":3,\"tenantId\":\"$TENANT\"}"
```

The seed is reproducible: the same `seed` integer produces the same rows. Seeded simulation ids are
`ap-seed-<seed>-sim-<n>`; `sim-0` is the largest.

## Explore the graph

Open any simulation → the **Attack path** tab. The **Simulation id** field is a dropdown listing your
seeded simulations with their **endpoint count** (the number of graph nodes, which drives the render
cost — not the execution count), largest first. Pick one, then:

- **Collapsed / Full / Auto** toggle. Collapsed is a DB aggregation (one node per endpoint, bounded);
  full materializes every node and the per-execution feed. On the 100k outlier (`ap-seed-7-sim-0`)
  collapsed is ~0.3 s / ~450 nodes vs full ~2.3 s / ~2,800 nodes plus a 100k-row feed — the lever the
  POC is about. Above a threshold a simulation is served collapsed automatically.
- **Click an endpoint** to lazy-load its findings (collapsed) and its executions (side panel), and to
  highlight its path.

## How it works (short)

- **Model**: `attackpath_execution` (one row = one injector→endpoint hop carrying the run snapshot),
  `attackpath_finding` (one row = one endpoint/type/value), and the link table. Reference ids
  (`simulation_id`, asset/agent ids) are stored without hard FKs to product tables, so the POC is
  isolated and droppable.
- **Rebuild**: two flat JPQL reads (executions; findings joined to their producing execution) plus one
  in-memory pass into `{ nodes, edges, counters }` with deterministic ids. No recursion; the number of
  SQL statements is a constant two, independent of graph size.
- **Collapsed mode**: four `GROUP BY` aggregations (endpoint groups, grouped edges, per-type and
  per-endpoint-type distinct counts) that never materialize the per-row data; the front loads
  per-endpoint detail on click.
- **Tenant isolation**: entities are `TenantBase`, the tables are listed in
  `openaev.tenant.active-tables`, and each read endpoint declares a `TxCtx` so the statement inspector
  rewrites every query with `can_access_tenant(tenant_id)`. The seed is the one audited exception: it
  writes with batched raw JDBC (bypassing the inspector) and sets `tenant_id` explicitly on every row
  (ADR-002).

!!! note "To be completed"

    Architecture diagrams (mermaid) for the model, the rebuild, and tenant enforcement land with the
    rest of this guide.
