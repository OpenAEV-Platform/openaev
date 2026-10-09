# Payload approval — Product brief (EPIC "Approval Workflow for Exercise Launch", Option 2)

Owner: product owner (PO). Branch: `feature/approval-prototype` (proof of concept).
Sources: Notion EPIC "Approval Workflow for Exercise Launch", Option 2 tasks PMF-774, PMF-788, PMF-776.

## Goal

Approval applies to **Threat Arsenal payloads**. A payload created or edited by a user without `Approve Content` becomes **Pending**. Nothing can run with a payload that is not **Approved**.

## Rules

1. Status is computed on every payload create/edit from the acting user:
   user has `Approve Content` → **Approved** (auto-approved); otherwise → **Pending**.
   An auto-approval is recorded in the approval history as "auto-approved, approver = author", for auditability.
2. Only `Approve Content` holders can Approve or Reject a payload.
3. A Rejected payload stays blocked until it is edited and approved.
4. Existing payloads are **Approved** after the migration (no surprise Pending).
5. Enforcement is **server-side**: UI and API apply the same rules.
6. Launch is blocked on every path (manual, scheduled, bulk, API) if any inject uses a non-Approved payload, with a clear message listing the blocking payloads.
7. Blocked launch attempts are logged.
8. An edit of an approved payload (command, arguments, cleanup, platforms) by a user **without** `Approve Content` sends it back to **Pending**. An edit by a user holding `Approve Content` stays **Approved** (rule 1).
9. **Warning before impact** (the action stays possible after the warning):
   - Rejecting a payload that is used shows: "This payload is used in N atomic testings, M scenarios, K simulations. Rejecting it blocks their launch." (with the list or links when feasible).
   - A user **without** `Approve Content` who edits the executable content of an **approved** payload that is used sees: "Saving will send this payload back to Pending approval and block the launch of N … until it is approved again."
10. **Inject status display**: in the inject lists of scenarios, simulations and atomic testings, an inject that has **not run yet** and whose payload is not approved shows "Payload pending approval" or "Payload rejected" in its status column. It is a display **computed on read** from the payload approval status, never written into the stored inject execution status. Once executed, the normal execution status shows.
11. **Kept as is**: the chip next to the inject title; no new status or filter on atomic testings, scenarios or simulations (approval filtering stays in the Threat Arsenal only); launch buttons stay visible and enabled, and the server error is shown on click.

## Scope rule

| Threat Arsenal item | Approval? |
|---|---|
| Custom payloads created or edited by users (command, executable, file drop, DNS, network traffic) | Yes |
| Duplicates of a payload | Yes: the copy starts Pending, unless the user holds `Approve Content` (auto-Approved, rule 1) |
| Imported payloads (file import, external libraries, collectors) | Yes. File imports follow rule 1 (auto-Approved if the importer holds `Approve Content`, Pending otherwise). Collector payloads are always **Pending** by default |
| Built-in payload-less actions shipped by Filigran | No, pre-approved |
| IOC validation payloads from PR #8272 | Covered by the PR's own flow, never blocked by payload approval |

**Approval vs. grants.** The existing per-action Threat Arsenal grants (`GroupManageThreatArsenalGrants`: Access / Manage+Delete per action, per group) control **who can use** an action. Approval controls **whether its content is trusted** to run. These are two independent checks: a user granted an action still cannot run it while its payload is not Approved.

## Capabilities: payload approval vs. IOC validation approval (PR #8272)

| Capability | Covers | Kind of decision |
|---|---|---|
| **Launch** (existing `Launch assessment`, `Action.LAUNCH`) | Approving an **IOC validation** request (PR #8272, `LAUNCH` on `IOC_VALIDATION`) | Execution decision: "approve and launch now". PR #8272 adds no capability, and we do not change it. |
| **Approve content** (new, `APPROVE_THREAT_ARSENALS`, Threat Arsenal group) | Approving or rejecting **Threat Arsenal payloads** | Content decision: "is this payload trusted to exist and be used?" It is assigned to the people who approve payloads. |

Our approve/reject UI (status chip, Approve/Reject buttons, reject dialog) follows the same patterns as the IOC validation approval of PR #8272, so both feel like one family. It is reimplemented in our PRs: we import no code from PR #8272, and our PRs do not depend on it.

## Scope by iteration

| # | Iteration | Notion task |
|---|-----------|-------------|
| 1 | `Approve Content` capability in the Threat Arsenal group + `created_by` / `last_modified_by` on payloads + migration | Task 0 — PMF-774 |
| 2 | `approval_status` on payloads, Approve/Reject actions on payload page, Approval column + filter in payload list | Task 1 — PMF-788 |
| 3 | Only Approved payloads selectable in Atomic Tests / Scenarios / Simulations; launch blocked otherwise | Task 2 — PMF-776 |

## Out of scope

Approval of Atomic Tests, Scenarios or Simulations themselves (that is Option 1). Platform-wide on/off switch (placeholder only).

## Variants to compare

- Picker: **A** hide non-approved payloads / **B** show them greyed out with a "Pending approval" badge.
- Inject whose payload goes back to Pending: **A** block at launch only / **B** also show a warning badge in the timeline.
- Reject: **A** no reason / **B** mandatory short reason.

## Open questions the prototype should help answer

- Does payload-level approval satisfy the customer maker-checker requirement? What gap remains (exercise composition is not reviewed)?
- Scheduled scenario hits a Pending payload: skip the inject, block the whole run, or notify?
- Which existing roles receive `Approve Content` in the migration?
- Customer maker-checker requirement: should the checker also review the **exercise composition** (targets, arguments, schedule) before launch, not only payloads?

## Demo mock data

- Roles: **Author** (manage content, no `Approve Content`), **Approver** (`Approve Content`), **Launcher** (Launch Assessment only).
- 6 payloads: 3 Approved, 2 Pending, 1 Rejected (with reason).
- 1 scenario using one Approved and one Pending payload → launch blocked.
- 1 scheduled scenario using a Rejected payload → scheduled run blocked.

## Mockups

HTML mockups were used as the visual reference during the design (not published here); the prototype is built with existing OpenAEV components.

## Decisions log

| Date | Decision |
|---|---|
| 2026-10-07 | Rule 1 stands: create/edit by an `Approve Content` holder is auto-Approved, recorded as "auto-approved, approver = author". Rule 8 applies only to users without `Approve Content`. |
| 2026-10-07 | Collector payloads are Pending by default. |
| 2026-10-07 | Duplicates and file imports by an `Approve Content` holder are auto-Approved (rule 1, recorded as auto-approved); by anyone else, Pending. |
| 2026-10-07 | The work is split into one draft PR per Notion task (PMF-774, PMF-788, PMF-776), stacked. PR #8272 is not merged and is used only as a reference. See `PR-PLAN.md`. |
| 2026-10-07 | Task 2 additions (PO): warning before impact on reject and on an edit that sends an approved, used payload back to Pending (rule 9); "Payload pending approval" / "Payload rejected" shown in the status column of injects not run yet, computed on read, never stored (rule 10); chip kept, no new status or filter outside the Threat Arsenal, launch buttons stay enabled with the server error on click (rule 11). |
| 2026-10-07 | Task 2 additions design approved (PO): usage counts atomic testings, scenarios and simulations **still to run** (scheduled, running, paused); names and links only for users who can open atomic testings / scenarios / simulations, counts only for others. Notion: warning = **US2.4** (new, Task 2), status display = **US2.1 AC4**, launch buttons visible and enabled = **US2.2 AC2** (changed). |
| 2026-10-08 | Launch buttons disabled with a "Can't launch: …" tooltip (replaces "enabled, server error on click"); usage counts never depend on the viewer; picker filter counts follow the picker's scope. |
| 2026-10-08 | Planned simulation with a blocked payload goes back to **Draft** (not canceled); a running simulation is not stopped and its blocked injects end in **Error** (no new inject status). |
| 2026-10-08 | Persisted **paused** schedule for recurring scenarios and recurring atomic testings (migration: `scenario_recurrence_paused_at`, `inject_recurrence_paused_at`; no reason column, the reason is computed when the page loads). Re-enabled by saving or stopping the schedule, refused while blocked. |
| 2026-10-08 | Option A: a sensitive change (content, action, targets, documents, teams, players) pauses the schedule / unplans the simulation. Title, description, tags, delay are not sensitive. |
| 2026-10-08 | Atomic testings list shows the same status as the detail page (*Draft* + chip). |
| 2026-10-08 | Issue ↔ PR sidebar links: not possible for PRs into feature branches; the final `feature/approval-prototype → main` PR carries "Closes #8350, #8354, #8356". |
| 2026-10-08 | Task 3 (PMF-795, US3.1, issue #8376): **replaces Task 0 rule 3**. At upgrade, every tenant role with *Manage threat arsenal* (or *Delete*) also receives *Approve content* (+ *Access* if missing); the default *Manager* role of new tenants includes it too (AC9). Existing authors stay auto-approved; maker-checker is opt-in by removing *Approve content* from a role. Roles created or edited afterwards are never ticked automatically. |
| 2026-10-08 | **PO principle**: only **human-generated** content goes to Pending. **System-generated** content is **Approved**, recorded with origin **SYSTEM** in the approval history, unless a human later edits its executable content (then the normal rules apply). |
| 2026-10-08 | T1-1: the security-coverage file drop is system-generated → **Approved (SYSTEM)** (was Pending). Delivered in Task 4 (#8378, US4.1). |
| 2026-10-08 | Q-F: IOC validation payloads (#8272) are system-generated and **exempt**; no message to #8272's author. Task 2 keeps the generic `PayloadApprovalExemption`; the glue must also record them with origin SYSTEM so the exemption is visible. |
| 2026-10-08 | T2-2 (changed): automatic inject generation (TTP inject assistant, STIX security coverage, and the other automatic selection paths) picks **only approved payloads plus payload-less built-ins**, like the user pickers. Delivered in Task 4 (#8378, US4.2–US4.4). |
| 2026-10-08 | **Exercise composition review** (targets, arguments, schedule): **out of scope** of this POC. |
| 2026-10-08 | Collector payloads (Pending when new or changed, 2026-10-07) may conflict with the principle: under review (decided later the same day, see below). |
| 2026-10-08 | **Delivery change**: T1-1 and T2-2 are not follow-up PRs on #8354 / #8356; they are delivered in **Task 4** ("Edge cases: system-generated payloads and automatic selection", issue #8378, US4.1–US4.4 after US4.5 was dropped). |
| 2026-10-08 | **Collector payloads: decided, they stay Pending** (option a). Collectors import scripts written by third parties, which is exactly what maker-checker must review; "system-generated = Approved" only covers content the platform generates itself (e.g. the security-coverage file drop). Trusting specific collectors (option c) is a possible later improvement. |
| 2026-10-08 | Task 4 scope: **US4.5 (spreadsheet import) dropped**. Spreadsheet import stays as today, like JSON import: the inject is imported, shows the approval chip, and its launch is blocked (Task 2) until the payload is approved. Task 4 = US4.1–US4.4. |
| 2026-10-09 | **Scope change (staff feedback)**: **Task 5, payload versioning**: an approved payload edited by a non-approver keeps its approved version in use; the edit becomes a pending version, applied only when approved. Task 2's blocking, chips, paused schedules / back to Draft and edit warning will only apply to payloads that never had an approved version. Delivered by a new Task 5 PR, not by editing Task 2. **Task 6**: notifications to approvers when a payload or a new version goes Pending. |
| 2026-10-09 | **Open bug**: a user can still approve after *Approve content* is removed from their role, status **investigating** (first finding: the server refuses with 403; the UI keeps showing the button until reload). |
