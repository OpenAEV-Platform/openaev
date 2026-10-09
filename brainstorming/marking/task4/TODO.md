## TODO discussed Corinne / Damien 
- [x] OpenCTI recurring scenarios -> launch all assets + set user service account OpenCTI in the scenario? -> Damien
    - We need to add all the markings to the OpenCTI service account in addition to adding the OpenCTI user to the scheduled scenario
    - See resolveLaunchedByClearance =>chunk4

- [x] Tests chained simulation =>chunk
- [x] Tests with OpenAEV agent and external executors:
    - Atomic testing -> OK
    - Simulation manual and scheduled -> OK
    - Scenario manual and scheduled -> OK
    - Inject expectations -> same global score is seen even if we don't see the assets linked, different global score if manual asset

- [x] StreamApi and solutions A/B to check -> Corinne, created chunk2

- [x] Clean the tech design -> Corinne

- [x] POC "asset not targetted" VS "no agent found" -> Corinne, chunk3
    - Damien's proposal -> in ExecutionExecutorService, at the end, check the assets list with and without markings

- [ ] Do a brainstorm/choose the solution with Laurent

- [ ] Little UX bugs with the new solution?
    - [x] A lot of "Access denied" when I am not an admin user
    - [ ] Execution details are weird for Injects when you launch it with an admin and you look it with a user "Distributing inject to 2 agent(s) across 2 endpoint(s)"  -> check with PO before doing it?
    - [x] Header overview number of assets for simulation KO when logged as a user while the simulation's list is OK
    - [ ] Global inject status can be weird if you don't see all the assets -> check with PO before doing it?
    - [ ] Platforms in scenario's list are KO for a user -> platforms queries never read assets but injectors contracts -> check with PO before doing it?
    - [x] Bugs with inject expectations (see above) => Damien to retest

- [ ] Last spike to POC (dashboards) => Corinne

## TODO extracted from tech-design, suggested by AI

- [ ] Possible defect: a user who belong to a group with ByPass checked and a marking TLP:GREEN, could see TLP:RED
    **Quoting AI:**
    Why it matters here: `resolveLaunchedByClearance` (`InjectService.java:481`) uses `isAdminOrBypass()` as
    its only bypass test. A launcher whose group holds a `BYPASS` role would resolve through their group
    marking grants at dispatch instead of getting full clearance — a narrower result (fail-closed), but
    not the documented one. `HttpMarkingScopeSupplier` would still treat the same user as bypass, because
    the expanded set contains `AGENT_RUNTIME_ACCESS`, so reads and dispatch could disagree for that user.
    Not verified: no test exercises `isAdminOrBypass()` with a `BYPASS` role (the only test mention is a
    comment in `InjectServiceTest`), and the method is used widely, so there may be a path that makes it
    work. A unit test with a user in a group carrying a `BYPASS` role would settle it; if confirmed, the
    fix is to use `hasBypassIn(...)` / `hasTenantBypass()` or to test the role capabilities directly.

- [ ] At execution time assetGroups are not resolved per user clearance.
  **Quoting AI:**
  * .assets(...) is filtered by the launcher's clearance: it comes from getAssetsToExecute(), with resolveAllAssetsToExecute() as the fallback.
  * .assetGroups(...) is still unfiltered, and the code comment says it's deliberate until US1 is decided.
  * The payload only carries each group's id and name (AssetGroupSimple), not its members. The external injector resolves the members itself, later, so the launcher's clearance never applies to them.
  * NOTE: asset_groups_assets is now filtered, but by the clearance of whoever reads it. When the injector resolves a group's members, it reads them under its own account's clearance, not the launcher's:
  
- [ ] Agentless traces of a RED asset — a real gap, worth doing

What it is. External injectors that don't run through an agent (for example network scanners targeting agentless endpoints) report their results in an execution callback. That callback carries contextIdentifiers, the ids of the targets the result is about: assets, teams or players. The trace is saved with no agent and those ids in execution_context_identifiers (InjectStatusService.java:237).

Why it leaks. The filter added yesterday follows trace → agent → asset. These traces have no agent, so they count as "global" and stay visible.

In the UI, the trace only shows when you click the RED target, which the GREEN user can't see, so there is no leak there.
Through the API, GET /api/injects/execution-traces?targetId=<RED id> returns them (findByInjectIdAndAssetId matches on the array).
So do the endpoints that return the raw Inject, which embed status_traces. Those carry the RED asset id and the injector's output to anyone who can read the inject.

Fix. The trace needs a second condition: hidden if any asset named in the array is restricted.

NOT EXISTS (SELECT 1 FROM assets a
WHERE a.asset_id = ANY(t.execution_context_identifiers)
AND NOT is_marking_set_allowed(a.marking_ids))

Team and player ids in the array match no asset, so they don't affect the result. The engine work is that a table can only be filtered one way today, and execution_traces already follows the agent. So MarkedTable would need a fourth filter type ("an array of asset ids"), and a table would need to accept several filters ANDed together. That's a small-to-medium change in MarkedTable and MarkingDimension, plus tests.

- [ ] The asset-group expectation row — the verdict still includes RED

What it is. When an inject targets an asset group, one group expectation row is stored: asset_group_id is set and asset_id is NULL. Its verdict is rolled up from every member's result, using the group's validation mode ("all assets" or "at least one").

Why it leaks. The new filter only hides rows with an asset_id. The RED member's own row is hidden, but the group row is not, and its verdict was computed with RED included:

"all assets" mode: the group shows Failed because of RED alone, while every visible member succeeded.
"at least one" mode: the group shows Success thanks to RED alone.

The global score also uses this row (InjectExpectationRepository.java:390-397), so it's skewed too. This doesn't reveal RED's identity, but it reveals RED's outcome, and the GREEN user can't make sense of it.

Options:
* Recompute the group verdict at read time from the members the viewer can see. This is correct, but it changes the scoring code and costs more.
* Hide the group row from a viewer who can't see every member. This is simple, but it moves this one case toward Option 2.
* Accept it and document it, since it's an aggregate.
This needs a product decision before any code. It's the "stored aggregates" con of Option 1.

- [x] asset_agent_jobs — defence in depth only, low priority -> NO ACTION

What it is. The queue of commands waiting for an agent. Only implants read it, through POST /api/endpoints/jobs and /jobs/{externalReference} (EndpointApi.java:122-139). Those require the JOB capability, which only agents get (AGENT_RUNTIME_ACCESS), and agents have full clearance.

Why it's low priority. No end-user endpoint or screen reads it, so no GREEN user can reach a RED agent's job today. Adding linkedTo("asset_agent_jobs", "asset_agent_agent", "agents", "agent_id") would only protect against a future endpoint exposing jobs. It's one line, but it adds the trace → agent → asset sub-select cost on a table agents poll constantly. I'd leave it out unless a user-facing read appears.

- [ ] UX/UI changed to add the user "launchedBy"
With more marked entities, partial runs become the norm rather than the exception. A viewer with higher
clearance, e.g. an admin opening a Scenario, a Simulation or an Atomic Testing, must be able to tell that a
run covered only part of the targets, and why. Otherwise scores and findings get misread as "everything
was tested".

  - **Show who launched the run, and on whose clearance.** Display `launched_by` (and `scheduled_by` for
  recurring runs) on Simulations and Atomic Testing, e.g. "Launched by *jdoe*", "Scheduled by *jdoe*".
  - **Show marking chips on every asset list.** Target lists, result tables, findings and asset-group
  members should all carry them, so a viewer immediately sees which targets are marked and at which level.
  - **Give each skipped target an explicit status for viewers who can see it.** For example "Not executed:
  outside the launcher's clearance", instead of the target looking simply absent or pending.
    - **How to record it.** Write a trace or status row per skipped asset at dispatch time.
    - **Why it does not leak.** That row is linked to the restricted asset, so it is itself a derived row
      filtered by the same C-2 mechanism. A `TLP:GREEN` viewer never sees it, and a `TLP:RED` viewer
      does.
  - **Show a run-level indicator, computed per viewer.** For example "Partial run: 2 of 5 targets were not
  executed". Show it **only** to viewers who can see at least one skipped target. A viewer with the same
  clearance as the launcher sees no indicator, which preserves "a restricted asset does not exist".
  - **Label scores clearly.** State that a score covers the targets that actually ran. Read-time scores
  already reflect what the viewer can see once `injects_expectations` is filtered (§3.2).

