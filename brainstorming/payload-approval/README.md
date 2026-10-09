# Payload approval POC: design and delivery notes

These are the design and delivery notes of the payload approval proof of concept: EPIC "Approval Workflow for Exercise Launch", option 2, payload approval in the Threat Arsenal, for a customer maker-checker requirement. They record the decisions, rules, user stories, diagrams, test results and test steps of each task.

| Task | Issue | PR |
|---|---|---|
| Task 0: *Approve content* capability + "last modified by" | [#8350](https://github.com/OpenAEV-Platform/openaev/issues/8350) | [#8353](https://github.com/OpenAEV-Platform/openaev/pull/8353) |
| Task 1: approval status, approve / reject, history, Approval column and filter | [#8354](https://github.com/OpenAEV-Platform/openaev/issues/8354) | [#8355](https://github.com/OpenAEV-Platform/openaev/pull/8355) |
| Task 2: only approved payloads selectable and launchable, executor check, warnings, paused schedules | [#8356](https://github.com/OpenAEV-Platform/openaev/issues/8356) | [#8357](https://github.com/OpenAEV-Platform/openaev/pull/8357) |
| Task 3: existing threat arsenal managers receive *Approve content* at upgrade | [#8376](https://github.com/OpenAEV-Platform/openaev/issues/8376) | [#8377](https://github.com/OpenAEV-Platform/openaev/pull/8377) |
| Task 4: system-generated payloads approved; automatic selection only picks approved payloads | [#8378](https://github.com/OpenAEV-Platform/openaev/issues/8378) | [#8379](https://github.com/OpenAEV-Platform/openaev/pull/8379) |

## Status (2026-10-09)

| Task | Status |
|---|---|
| **Task 0** (PMF-774) | Merged (squash) into `feature/approval-prototype` as `8f284c15d` |
| **Task 1** (PMF-788) | Merged (squash) into `feature/approval-prototype` as `f34533971`; this commit also carries Task 2 |
| **Task 2** (PMF-776) | Merged (squash) into the Task 1 branch as `ee7b4ae1f`, then into `feature/approval-prototype` with #8355 |
| **Task 3** (PMF-795, US3.1) | Merged (squash) into `feature/approval-prototype` as `49c5a2316` |
| **Task 4** (US4.1–US4.4) | Merged (squash) into `feature/approval-prototype` as `e6e301579` |

- **`feature/approval-prototype` = `e6e301579`**: `main` (`f1a2d2d3c`) + Tasks 0 to 4.
- **Final PR**: [#8366](https://github.com/OpenAEV-Platform/openaev/pull/8366) `feature/approval-prototype → main`, **draft, not to be merged now**. It closes #8350, #8354, #8356, #8376 and #8378.
- **Staging**: https://feat-8366-approval-p.oaev.staging.filigran.io, redeployed on every commit to `feature/approval-prototype` while the deploy box of #8366 is ticked.
- **Conflicts with `main`**: #8366 shows conflicts. Two i18n files conflict (`fr.json`, `i18n-loanwords.json`, from #8334), and `main` brings the Spring Boot 4 / Jackson 3 upgrade (#7749), which will need porting work. The staging deploy is not affected: it builds the branch as is.
- **Delivery change (2026-10-08)**: the system-generated payload (T1-1) and automatic selection (T2-2) decisions were delivered in Task 4, not as follow-ups on #8354 / #8356. US4.5 (spreadsheet import) was dropped: spreadsheet and JSON imports are unchanged.
- **Collector payloads: decided, they stay Pending**: collectors import scripts written by third parties. Trusting specific collectors is a possible later improvement.
- **Exercise composition review**: out of scope of this proof of concept.
- **Merge note**: #8355 first showed a conflict after #8353 was squash-merged. The squash had the same content as the original Task 0 commit still on the Task 1 branch. It was fixed with a merge commit on the Task 1 branch that left the content unchanged (no force push).

### Next steps (staff feedback, 2026-10-09)

- **Task 5, payload versioning**: an approved payload edited by a non-approver keeps its approved version in use; the edit becomes a pending version, applied only when approved. This changes Task 2: blocking, chips, paused schedules / back to Draft and the edit warning will only apply to payloads that never had an approved version. It is delivered by a new Task 5 PR, not by editing Task 2.
- **Task 6, notifications**: approvers are notified (in-app, optional email) when a payload or a new version goes Pending; authors are told the outcome.
- **Open bug, investigating**: after *Approve content* is removed from a user's role, the *Approve* button is still shown. First finding: the server refuses the approval (403) and the payload stays Pending; the UI loads capabilities once at app start and keeps showing the button until reload.

### Issue ↔ PR links

| Issue | PR | Shown in the issue's "Development" sidebar |
|---|---|---|
| #8350 | #8353 + final #8366 | #8366 (via "Closes"); #8353 as a timeline cross-reference |
| #8354 | #8355 + final #8366 | #8366; #8355 as cross-reference |
| #8356 | #8357 + final #8366 | #8366; #8357 as cross-reference |
| #8376 | #8377 + final #8366 | #8366; #8377 as cross-reference |
| #8378 | #8379 + final #8366 | #8366; #8379 as cross-reference |

GitHub fills the sidebar from a closing keyword only when the PR targets the default branch (`main`). That is why #8366 shows in the sidebar and the task PRs do not.

## Staging environment (PR #8366)

| What | Status |
|---|---|
| URL | https://feat-8366-approval-p.oaev.staging.filigran.io |
| Admin login | Created at startup from the `openaev.admin.*` settings, set by the staging deployment configuration (outside this repository). Ask the staging owners. |
| Test accounts | With the `test-feature-branch` profile, the data pack `V20260805_Observer_and_manager_users` creates an observer and a manager test account. Their credentials come from the deployment configuration. |
| Enterprise licence | Read from `OPENAEV_APPLICATION_LICENSE` by the `ci` profile, set by the deployment configuration. Check *Settings → Enterprise Edition*. |
| Duplicate roles (3 Admin, 2 Manager, 2 Observer) | **Already there before this work, not caused by these tasks.** The `test-feature-branch` data pack `V20260805_Observer_and_manager_users` always creates Admin / Manager / Observer roles (description = name) next to the ones from the `V4_29` migration (no description) and the bootstrap Admin ("Full administrative access to the tenant."). All files involved are identical on `main`. A possible fix, against `main`, is to reuse existing roles by name. |
| Suggested test data | Roles Author / Approver / Launcher, one user per tester, actions A / B, a recurring atomic testing, a recurring scenario, a planned simulation. |

## Task 2 work log

| Round | Date | What | State |
|---|---|---|---|
| Build + US2.4 | 2026-10-07 | Only approved payloads selectable, launch blocked on every path, executor check, inject indicators (chip + status column), warning before impact (reject dialog, edit warning), scheduled simulation canceled when blocked | Committed `70f2b5625`, PR #8357 |
| Fix: edit dialog | 2026-10-07 | The 409 "approval impact" answer was shown as an error toast and rethrown (uncaught error, dialog never opened): fixed; Confirm resends without the check | Committed `b71c82267` |
| Polish | 2026-10-07/08 | Compact "Pending" / "Rejected" chips (full text in tooltip), status column placement; warning dialogs with the standard Alert + grouped, scrollable, capped lists (20 names per type, exact counts); ICU plurals in 9 languages; "Access denied" toasts removed (collectors, security platforms, home dashboard skipped without the capability); approval status wins over "Verified" on cards and drawer; action picker opens with no filter, assets picker's platform filter is a removable default | Committed `b71c82267` |
| Follow-up | 2026-10-08 | (1) Picker facet counts use the picker's "approved only" scope; (2) one usage rule for both dialogs, counts never depend on the viewer; (3) Launch / Launch now / Relaunch now / Start now disabled with "Can't launch: …" tooltip, no confirm dialog, `*_launch_blocked_by` on the three GETs; (4) planned simulations back to Draft, running simulations keep running (blocked injects end in Error), atomic testing header shows Draft and recurring atomic testings are skipped, recurring scenarios create no new simulation while blocked | Committed `b71c82267` |
| Final | 2026-10-08 | Atomic testings list shows *Draft* + chip like the detail page (browser-test fix); persisted pause of recurring scenarios **and** atomic testings (migration `V6_20261008120000000__Add_recurrence_paused_at`, two columns, no reason column); option A: sensitive change pauses schedules / unplans simulations; *Paused* chip with reason; docs for threat arsenal, atomic testing, scenario, simulation. Fix found by the regression: an inject edit on a planned simulation skipped the Enterprise executor licence check | Committed `b71c82267`, pushed |

### Decisions (2026-10-08)
1. Persisted paused schedule: `scenarios.scenario_recurrence_paused_at` **and** `injects.inject_recurrence_paused_at` (the second follows from decision 6). **No reason column**: the reason is computed when the page loads.
2. Item 5: option A (pause on change). Sensitive: inject content, action, targeted assets / asset groups / teams / *all teams*, documents, scenario or simulation teams and players. Not sensitive: title, description, tags, delay. Manual launch after a change stays possible.
3. PR ↔ issue links: final-PR "Closes" alternative (see "Issue ↔ PR links").
4. Blocked injects in a running simulation keep the Error status; no new inject status.
5. Atomic testings list shows the same status as the detail page (*Draft* + chip). There is no status filter on that list.
6. Recurring atomic testings: same persisted pause as scenarios (paused until re-enabled on purpose; re-enabling refused while blocked).

### Tests (2026-10-08, final)
- Backend: full regression of the touched areas (approval, atomic testing, scenario, simulation, inject, threat arsenal, payload, injector contract, executor, chaining, execution jobs, dashboards, collectors, architecture): **2102 tests**, 4 skipped (already disabled). One real failure, fixed: editing an inject of a planned simulation moved it to Draft before the Enterprise executor licence check, which was then skipped. The affected classes were rerun: **171 tests, 0 failures**. One flaky after-class error (`SimulationInjectApiTest`) passed on retry and in the rerun. Spotless clean.
- Frontend: 65 test files, **506 tests** pass; lint, type check and i18n checker clean.

## Files

| File | What it is |
|------|------------|
| `BRIEF.md` | Product brief: goal, rules, scope rule, capability split (Launch vs Approve content), decisions log, open questions |
| `PR-PLAN.md` | Delivery plan: status log, one section per PR, open questions |
| `NOTION-task0.md` | Task 0 notes (Notion PMF-774) |
| `NOTION-task1.md` | Task 1 notes (Notion PMF-788, US1.3 = PMF-791) |
| `NOTION-task2.md` | Task 2 notes (Notion PMF-776, US2.1 = PMF-785, US2.4 new) |
| `NOTION-task3.md` | Task 3 notes (Notion PMF-795, US3.1) |
| `NOTION-task4.md` | Task 4 notes (Notion "Option 2 — Task 4", US4.1–US4.4) |

The Mermaid diagrams in these files render on GitHub; to reuse them in Notion, paste each block into a code block with the language set to *Mermaid*.
