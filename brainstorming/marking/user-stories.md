> # 🛑 THIS FILE IS NOT THE SOURCE OF TRUTH 🛑
>
> ## The source of truth for these user stories is **Notion**.
>
> **EPIC — Marking based access control:**
> <https://app.notion.com/p/2c58fce17f2a803081dcf80b5a591db9>
>
> | Task | Notion ID | Title |
> |---|---|---|
> | Task 1 | **590** | Create & Manage marking definitions |
> | Task 2 | **591** | Assign markings to users |
> | Task 3 | **592** | Assign markings to assets |
>
> ⚠️ **What this file actually is:** a point-in-time *export* of the above, kept in the repository
> so the technical design documents next to it can quote acceptance criteria without sending the
> reader to another tool. It is a convenience copy and nothing more.
>
> ⚠️ **It is already known to be stale.** Task 2 appears **twice** below (lines ~317 and ~523), and
> the two copies are **not identical** — the second carries an "⚠️ Important Flags" section the
> first lacks. That is the drift you get from a manual copy, and it is exactly why this banner
> exists.
>
> ⚠️ **Do not edit acceptance criteria here.** Edits made in this file are invisible to PM and
> stakeholders, will not be reviewed, and will be silently overwritten by the next export. **Change
> Notion, then re-export.**
>
> ✅ If a statement here disagrees with Notion, **Notion wins** — treat the difference as a bug in
> this file, not as a decision.

---

# Task 1 — Create & Manage

## Properties

- **Task ID:** 590
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Verticals:** #3 Easy-to-Use & consistent platform
- **EPIC:** https://app.notion.com/p/2c58fce17f2a803081dcf80b5a591db9
- **Sub-tasks:**
    - US1 — Assign "Manage Marking Definitions" Capability to a Role
    - US2 — Navigate to Marking Definition
    - US3 — Create a Marking Definition
    - US4 — Edit a Marking Definition
    - US5 — Delete a Marking Definition
    - US6 — Default TLP Markings are Pre-loaded on Platform Initialization ( nice to have )

## AI Summary

- 📌 Develop a marking-definition feature (TLP only) to let admins grant “Manage marking definitions” capability and users create, edit, or delete TLP markings.
- 🚀 Provide default TLP markings (CLEAR, GREEN, AMBER, AMBER+STRICT, RED) and integrate them into the RBAC system for future role, group, and asset assignments.

---

# 🎯 Business Context (👥 PM + Stakeholders)

## Use Case & Business Goals

OpenAEV is introducing a marking-based access control layer to improve segregation of duties and sensitive data isolation across the platform. Task 1 focuses on the foundation: enabling authorized users to create and manage marking definitions (e.g. TLP:RED, TLP:GREEN, PAP:AMBER), mirroring the proven model from OpenCTI. These markings will later be assigned to roles, groups, and assets in subsequent tasks.

## 👤 Users & Personas

- **User with "Manage marking definitions" capability** ⇒ creates, edits, deletes marking definitions
- **Administrator** ⇒ assigns the "Manage marking definitions" capability to roles

# 🧠 WHAT DO WE WANT

## 🧭 User Flow (mapped to user stories)

### Flow A — Admin grants “Markings management” to users (RBAC)

1. Admin opens **Roles & Permissions** and selects a role to edit. *(US1)*
2. Admin enables **Manage Marking Definitions** for that role and saves. *(US1)*
3. Admin assigns that role to the target users (or ensures they already have it). *(US1 — scope reminder)*
4. User logs in / refreshes permissions and can access **Marking Definitions**. *(US2)*

### Flow B — Create a marking definition (severity + color)

1. User with the capability opens the menu and navigates to **Marking Definitions**. *(US2)*
2. User clicks **Create marking**. *(US3)*
3. User fills in:
    - **Name / label** (e.g., TLP:GREEN)
    - **Severity / level** (e.g., Low/Medium/High or TLP/PAP level as defined)
    - **Color** (used consistently across UI)
4. User saves and sees the new marking in the list and in its details. *(US3)*

### Flow C — Maintain markings over time

1. User opens an existing marking definition from the list. *(US2)*
2. User edits name / severity / color and saves. *(US4)*
3. If a marking should be removed, user deletes it (with any confirmation/warnings). *(US5)*

## 🧩 Design Decision — Marking Types for OpenAEV

### Context

As part of the marking definitions feature, we evaluated whether OpenAEV should support both **TLP (Traffic Light Protocol)** and **PAP (Permissible Actions Protocol)** as marking types, in line with what ANSSI [https://www.cert.ssi.gouv.fr/csirt/politique-partage/](https://www.cert.ssi.gouv.fr/csirt/politique-partage/) reference.

### Decision

**OpenAEV will support TLP markings only** for this scope.

### Rationale

- **TLP** governs visibility — who can see and access an object (asset, simulation). This is the missing layer that markings introduce and is not covered by any existing mechanism.
- **PAP** governs permissible actions — what a user is allowed to do with an object once they have access. In OpenAEV, this is **already covered** by the existing capabilities model.
- Introducing PAP markings on top of this would be **redundant** and would create conflicting access control logic. so we stick to TLP.

### Default TLP Markings

On platform initialization, the following **5 default TLP markings** will be pre-loaded, consistent with OpenCTI and the TLP v2.0 standard:

| NAME | ORDER |
|---|---:|
| TLP:CLEAR | 1 |
| TLP:GREEN | 2 |
| TLP:AMBER | 3 |
| TLP:AMBER+STRICT | 4 |
| TLP:RED | 5 |

These are the same default markings used in OpenCTI, ensuring consistency across the Filigran platform ecosystem.

## 📜 User Stories

### User stories pages

- US1 — Assign "Manage Marking Definitions" Capability to a Role
- US2 — Navigate to Marking Definition
- US3 — Create a Marking Definition
- US4 — Edit a Marking Definition
- US5 — Delete a Marking Definition
- US6 — Default TLP Markings are Pre-loaded on Platform Initialization ( nice to have )

# Sub-task: US1 — Assign "Manage Marking Definitions" Capability to a Role

## Properties

- **Task ID:** 596
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 🎯 Assign “Manage marking definitions” and “Assign marking” capabilities to roles for independent access control.
- 🔧 Ensure capability cascades (Access → Manage/Assign → Delete) and respects Bypass overrides.

## User story

**As an** administrator,  
**I want** to assign the **"Manage marking definitions"** and/or **"Assign marking"** capabilities to a role,  
**So that** users with that role can access and manage marking definitions, and/or assign/remove markings on groups and assets, independently of each other.

## Acceptance criteria

- **AC1** — Given I am editing a role in Settings → Security → Roles, When I view the capability list, Then **"Marking"** appears as a new top-level capability group, containing two independently assignable chains — **"Marking definitions"** (Access → Manage → Delete) and **"Assign marking"** (Access → Assign → Delete) — neither nested under "Manage credentials" or any other existing category.

- **AC2** — Given I enable **"Manage marking definitions"** for a role and save, When a user assigned to that role refreshes their session (re-login or permission refresh), Then they gain access to the **Marking Definitions** entry under Settings → Security.

- **AC2b** — Given I enable **"Assign marking"** for a role and save, When a user refreshes their session, Then they can assign/remove markings on Groups and Assets (Manage Markings action becomes available), independently of whether "Manage marking definitions" is also granted.

- **AC3** — Given a user's role has neither "Manage marking definitions" nor "Assign marking" enabled, When they navigate to Settings → Security, Then the **Marking Definitions** entry is **hidden** from the menu (not merely disabled), and marking-assignment actions on Groups/Assets are hidden as well.

- **AC4** — Given a user's role has the **Bypass** capability enabled, When they navigate to Settings → Security or to a Group/Asset, Then they can access Marking Definitions and assign/remove markings regardless of whether "Manage marking definitions" or "Assign marking" is explicitly granted.

- **AC5** — Given each capability sits in a strict L1→L2→L3 chain (Access → Manage/Assign → Delete), When an admin enables **Manage marking definitions** or **Delete marking definitions**, Then **Access marking definitions** is automatically enabled as its parent — and symmetrically, enabling **Assign marking** or **Delete marking assignment** auto-enables **Access marking assignment**. *(Confirmed 2026-08-11: cascade behavior verified in the mock-up; both chains behave identically to existing capability categories.)*

## Low-fidelity mockup

---

# Sub-task: US2 — Navigate to Marking Definition

## Properties

- **Task ID:** 599
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 Navigate to **Settings > Security > Marking Definitions** to manage all marking definitions in one place.
- ✅ Users with the “Manage marking definitions” capability see the list, can create, search, and filter markings; others receive an access-denied response.

## User story

> As a user with the "Manage marking definitions" capability, I want to navigate to Settings > Security > Marking Definitions so that I can manage all markings in one dedicated place.

## Acceptance criteria

- **AC1** — Given I am logged in as a user with the "Manage marking definitions" capability, When I navigate to Settings > Security, Then I see a "Marking Definitions" entry in the left navigation menu.
- **AC2** — Given I click on "Marking Definitions", When the page loads, Then I see a list of existing markings with columns: Type, Definition, Color, Order, Creation date.
- **AC3** — Given I am on the Marking Definitions page, When the page loads, Then a "Create Marking Definition" button is visible.
- **AC4** — Given I am logged in as a user without the "Manage marking definitions" capability, When I try to access Settings > Security > Marking Definitions, Then the page is not accessible or I see an access denied message.
- **AC5** — Given I am on the Marking Definitions page, When I use the search field, Then I can search existing marking definitions by Type, Definition, Color, Order, and Creation date.
- **AC6** — Given I am on the Marking Definitions page, When I apply filters, Then I can filter the list by Type, Definition, Color, Order, and Creation date.

---

# Sub-task: US3 — Create a Marking Definition

## Properties

- **Task ID:** 595
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage

## AI Summary

- 📌 Create a new marking definition via a modal with required fields (Type, Definition, Color, Order).
- ✅ Validate required inputs and save the marking, making it instantly visible in the list.

## User story

> As a user with the "Manage marking definitions" capability, I want to create a new marking definition so that I can classify assets and payloads with the appropriate sensitivity level.

## Acceptance criteria

- **AC1** — Given I am on the Marking Definitions page, When I click "Create Marking Definition", Then a creation modal opens with the following fields mirroring OpenCTI's model:
    - Type (required, e.g. TLP / PAP / custom)
    - Definition (required, e.g. TLP:RED)
    - Color (color picker, e.g. #cc0000)
    - Order (required numeric, e.g. TLP:CLEAR=1, TLP:GREEN=2, TLP:AMBER=3, TLP:RED=4)

- **AC2** — Given the creation modal is open, When I fill in Type, Definition, Color and Order and click "Create", Then the new marking is saved and immediately visible in the list.

- **AC3** — Given the creation modal is open, When I submit the form without filling in Type, Definition or Order, Then a validation error is shown on the missing required fields and the form cannot be submitted.

---

# Sub-task: US4 — Edit a Marking Definition

## Properties

- **Task ID:** 597
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage

## AI Summary

- ✏️ Edit existing marking definitions directly from the list (via the action menu).
- 📄 Pre-filled edit form with current values, allowing quick updates and immediate save reflection.

## User story

> As a user with the "Manage marking definitions" capability, I want to edit an existing marking definition so that I can update its details if needed.

## Acceptance criteria

- **AC1** — Given I am on the Marking Definitions page, When I click the action menu (⋮) on a marking row, Then I see an "Edit" option.
- **AC2** — Given I click "Edit" on a marking, When the edit form opens, Then all existing values are pre-filled.
- **AC3** — Given I update one or more fields and click "Save", When the save is confirmed, Then the changes are reflected immediately in the list.

---

# Sub-task: US5 — Delete a Marking Definition

## Properties

- **Task ID:** 598
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage

## AI Summary

- 🗂️ Delete unwanted marking definitions to keep the list clean.
- ✅ Confirm deletion and warn if the marking is in use.

## User story

> As a user with the "Manage marking definitions" capability, I want to delete a marking definition that is no longer relevant so that I can keep the list clean.

## Acceptance criteria

- **AC1** — Given I am on the Marking Definitions page, When I click the action menu (⋮) on a marking row, Then I see a "Delete" option.
- **AC2** — Given I click "Delete" on a marking, When the confirmation dialog appears, Then I must confirm before the deletion is executed.
- **AC3** — Given the marking is currently assigned to an asset, payload, or group, When I attempt to delete it, Then the system warns me that this marking is in use (block vs. warn — to be decided).

---

# Sub-task: US6 — Default TLP Markings are Pre-loaded on Platform Initialization ( nice to have )

## Properties

- **Task ID:** 628
- **Status:**
- **Status 1:** Not started
- **Parent-task:** Task 1 — Create & Manage

## AI Summary

- 📥 Auto-load the five standard TLP markings (CLEAR, GREEN, AMBER, AMBER+STRICT, RED) during platform initialization.
- 🛠️ Enables administrators and users to apply TLP classifications instantly without manual setup.

## User story

> As a platform administrator, I want the standard TLP marking definitions to be automatically available when the platform is initialized, so that users can immediately apply markings without requiring manual setup.

## Acceptance criteria

- **AC1 — Pre-loaded markings** — Given the platform has just been initialized, When I navigate to Settings > Marking Definitions, Then the following 5 TLP markings are already present and visible:

| Name | Type | Order |
|---|---|---:|
| TLP:CLEAR | TLP | 1 |
| TLP:GREEN | TLP | 2 |
| TLP:AMBER | TLP | 3 |
| TLP:AMBER+STRICT | TLP | 4 |
| TLP:RED | TLP | 5 |

# Task 2 — Assign Markings to users

## Properties

- **Task ID:** 591
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Verticals:** #3 Easy-to-Use & consistent platform
- **EPIC:** https://app.notion.com/p/2c58fce17f2a803081dcf80b5a591db9
- **Sub-tasks:**
    - US1 — View markings assigned to a group
    - US2 — Assign a marking to a group
    - US3 — Remove a marking from a group
    - US4 — Access control based on group marking .
    - US5 — Highest marking applies when user belongs to multiple groups

## AI Summary

- 📌 Assign marking definitions to groups in OpenAEV, letting users inherit the highest marking from their groups.
- 🛠️ Admins can view, add, edit, or remove group markings and manage group membership to control object access.


# 🎯 Business Context (👥 PM + Stakeholders)

## Use Case & Business Goals

Task 2 focuses on assigning marking definitions to groups in OpenAEV, following the same model as OpenCTI. A group can be assigned one or more markings — members of that group will only see and interact with objects whose marking level matches or is below their group's assigned markings. When a user belongs to multiple groups, the highest marking applies.

## 👤 Users & Personas

- **Administrator** ⇒ assigns markings to groups, manages group membership
- **User with the right capability** ⇒ views and interacts with objects based on their group's marking level

# 🧠 WHAT DO WE WANT (Business Refinement)

## 🧭 User Flow (mapped to user stories)

### Preconditions (dependency on Task 1)

- A marking definition exists (created/managed in Task 1).
- The admin has granted the appropriate permissions so the Administrator can manage group markings.

### Flow A — View current group markings

1. Administrator opens a **Group** and navigates to its **Markings** section. *(US1)*
2. Administrator sees the list of markings currently assigned to the group. *(US1)*

### Flow B — Assign markings to a group

1. Administrator opens a group and clicks **Edit** (or **Manage markings**). *(US2)*
2. Administrator selects one or more markings and saves. *(US2)*
3. Administrator sees the updated markings displayed on the group. *(US1)*

### Flow C — Remove a marking from a group

1. Administrator opens the group’s markings and removes a marking, then saves. *(US3)*
2. The marking is no longer listed on the group. *(US1)*

### Flow D — Add users to groups (so they inherit markings)

1. Administrator opens a group and goes to **Members**.
2. Administrator adds/removes users in the group and saves.
3. Users’ effective marking level updates (highest marking across their groups). *(US5)*
4. Access is enforced based on group markings. *(US4)*

## 📜 User Stories

### User stories pages

- US1 — View markings assigned to a group
- US2 — Assign a marking to a group
- US3 — Remove a marking from a group
- US4 — Access control based on group marking .
- US5 — Highest marking applies when user belongs to multiple groups


# Sub-task: US1 — View markings assigned to a group

## Properties

- **Task ID:** 602
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📋 View the list of markings assigned to a group on the group detail page.
- ✅ Shows each marking’s name and color; displays an empty state if no markings are assigned.

## User story

> *As a user with the right capability, I want to view the list of markings assigned to a group, so that I can understand what marking levels are accessible to members of that group.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I open a group, Then I see a "Markings" section listing all markings currently assigned to that group.
- **AC2** — Given no markings are assigned to the group, When I open the Markings section, Then I see an empty state.
- **AC3** — Given markings are assigned, When I view the Markings section, Then each marking is displayed with its name and color.

---

# Sub-task: US2 — Assign a marking to a group

## Properties

- **Task ID:** 601
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📋 **User story:** Enable users with proper permissions to assign a marking to a group, allowing group members to access objects tagged with that marking.
- ✅ **Acceptance criteria:** Add a marking via a selector on the group detail page, ensure selected markings are saved, prevent already-assigned markings from appearing again, and display the new marking in the Markings list.

## User story

> *As a user with the right capability, I want to assign a marking to a group, so that members of that group can access objects with that marking.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I click to add a marking, Then a selector opens showing available markings.
- **AC2** — Given I select a marking from the list, When I confirm, Then the marking is added to the group.
- **AC3** — Given a marking is already assigned to the group, When I open the selector, Then that marking does not appear as an option.
- **AC4** — Given the assignment is saved, When I view the Markings section, Then the new marking appears in the list.

---

# Sub-task: US3 — Remove a marking from a group

## Properties

- **Task ID:** 604
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 User story: Enable users with proper rights to remove a marking from a group, restricting marking levels for group members.
- ✅ Acceptance criteria: Show remove action per marking, confirm before deletion, and ensure the marking disappears after confirmation.

## User story

> *As a user with the right capability, I want to remove a marking from a group, so that I can restrict what marking levels are accessible to members of that group.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I view the Markings section, Then I see a remove action next to each assigned marking.
- **AC2** — Given I click remove on a marking, When the action is triggered, Then a confirmation is shown before deletion.
- **AC3** — Given I confirm the removal, When it is saved, Then the marking no longer appears in the group's Markings section.

---

# Sub-task: US4 — Access control based on group marking .

## Properties

- **Task ID:** 603
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 🎯 Develop group-based access control so users only see groups with markings matching or below their own.
- ✅ Ensure the feature restricts visibility according to assigned group markings, enhancing security and consistency.

## User story

> *As a user belonging to a single group, I want my access to groups, to be restricted to the markings assigned to my group, so that I only see what I am allowed to access.*

## Acceptance criteria

- **AC1** — Given I browse groups, When access is evaluated, Then I can only see groups whose marking matches or is below my group's assigned markings.

---

# Sub-task: US5 — Highest marking applies when user belongs to multiple groups

## Properties

- **Task ID:** 605
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 Highest marking determines user access when belonging to multiple groups.
- 🔄 Access updates automatically when groups are added or removed.

## User story

> *As a user belonging to multiple groups, I want my access level to reflect the highest marking across all my groups, so that I am not unnecessarily restricted.*

## Acceptance criteria

- **AC1** — Given I browse groups, When access is evaluated, Then I can only see groups up to the highest marking level across all my groups.
- **AC2** — Given I am removed from a group, When access is recalculated, Then my access reflects only my remaining groups' markings.
# Task 2 — Assign Markings to users

## Properties

- **Task ID:** 591
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Verticals:** #3 Easy-to-Use & consistent platform
- **EPIC:** https://app.notion.com/p/2c58fce17f2a803081dcf80b5a591db9
- **Sub-tasks:**
    - US1 — View markings assigned to a group
    - US2 — Assign a marking to a group
    - US3 — Remove a marking from a group
    - US4 — Access control based on group marking .
    - US5 — Highest marking applies when user belongs to multiple groups

## AI Summary

- 📌 Assign marking definitions to groups in OpenAEV, letting users inherit the highest marking from their groups.
- 🛠️ Admins can view, add, edit, or remove group markings and manage group membership to control object access.

---

# 🎯 Business Context (👥 PM + Stakeholders)

## Use Case & Business Goals

Task 2 focuses on assigning marking definitions to groups in OpenAEV, following the same model as OpenCTI. A group can be assigned one or more markings — members of that group will only see and interact with objects whose marking level matches or is below their group's assigned markings. When a user belongs to multiple groups, the highest marking applies.

## 👤 Users & Personas

- **Administrator** ⇒ assigns markings to groups, manages group membership
- **User with the right capability** ⇒ views and interacts with objects based on their group's marking level

# ⚠️ Important Flags

| Flag | Value |
|---|---|
| Has **Breaking changes** |  |
| Has **Data Model updates** |  |
| Has **RBAC changes** |  |
| Requires **Feature Flag** |  |
| Targets master (minor release asap) |  |
| Has impact on Import/Export |  |

# 🤝 Decisions Log

| Date | Decision | Validated by Product? | Validated by Technical? | Link to related Meeting |
|---|---|---|---|---|
| --- |  |  |  |  |
|  | Markings are assigned at the Group level (not Role level), following OpenCTI's architecture to keep a consistent mental model for end users |  |  |  |

---

# 🧠 WHAT DO WE WANT (Business Refinement)

## 🧭 User Flow (mapped to user stories)

### Preconditions (dependency on Task 1)

- A marking definition exists (created/managed in Task 1).
- The admin has granted the appropriate permissions so the Administrator can manage group markings.

### Flow A — View current group markings

1. Administrator opens a **Group** and navigates to its **Markings** section. *(US1)*
2. Administrator sees the list of markings currently assigned to the group. *(US1)*

### Flow B — Assign markings to a group

1. Administrator opens a group and clicks **Edit** (or **Manage markings**). *(US2)*
2. Administrator selects one or more markings and saves. *(US2)*
3. Administrator sees the updated markings displayed on the group. *(US1)*

### Flow C — Remove a marking from a group

1. Administrator opens the group’s markings and removes a marking, then saves. *(US3)*
2. The marking is no longer listed on the group. *(US1)*

### Flow D — Add users to groups (so they inherit markings)

1. Administrator opens a group and goes to **Members**.
2. Administrator adds/removes users in the group and saves.
3. Users’ effective marking level updates (highest marking across their groups). *(US5)*
4. Access is enforced based on group markings. *(US4)*

## 📜 User Stories

### User stories pages

- US1 — View markings assigned to a group
- US2 — Assign a marking to a group
- US3 — Remove a marking from a group
- US4 — Access control based on group marking .
- US5 — Highest marking applies when user belongs to multiple groups

# Sub-task: US1 — View markings assigned to a group

## Properties

- **Task ID:** 602
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📋 View the list of markings assigned to a group on the group detail page.
- ✅ Shows each marking’s name and color; displays an empty state if no markings are assigned.

## User story

> *As a user with the right capability, I want to view the list of markings assigned to a group, so that I can understand what marking levels are accessible to members of that group.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I open a group, Then I see a "Markings" section listing all markings currently assigned to that group.
- **AC2** — Given no markings are assigned to the group, When I open the Markings section, Then I see an empty state.
- **AC3** — Given markings are assigned, When I view the Markings section, Then each marking is displayed with its name and color.

---

# Sub-task: US2 — Assign a marking to a group

## Properties

- **Task ID:** 601
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📋 **User story:** Enable users with proper permissions to assign a marking to a group, allowing group members to access objects tagged with that marking.
- ✅ **Acceptance criteria:** Add a marking via a selector on the group detail page, ensure selected markings are saved, prevent already-assigned markings from appearing again, and display the new marking in the Markings list.

## User story

> *As a user with the right capability, I want to assign a marking to a group, so that members of that group can access objects with that marking.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I click to add a marking, Then a selector opens showing available markings.
- **AC2** — Given I select a marking from the list, When I confirm, Then the marking is added to the group.
- **AC3** — Given a marking is already assigned to the group, When I open the selector, Then that marking does not appear as an option.
- **AC4** — Given the assignment is saved, When I view the Markings section, Then the new marking appears in the list.

---

# Sub-task: US3 — Remove a marking from a group

## Properties

- **Task ID:** 604
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 User story: Enable users with proper rights to remove a marking from a group, restricting marking levels for group members.
- ✅ Acceptance criteria: Show remove action per marking, confirm before deletion, and ensure the marking disappears after confirmation.

## User story

> *As a user with the right capability, I want to remove a marking from a group, so that I can restrict what marking levels are accessible to members of that group.*

## Acceptance criteria

- **AC1** — Given I am on the group detail page, When I view the Markings section, Then I see a remove action next to each assigned marking.
- **AC2** — Given I click remove on a marking, When the action is triggered, Then a confirmation is shown before deletion.
- **AC3** — Given I confirm the removal, When it is saved, Then the marking no longer appears in the group's Markings section.

---

# Sub-task: US4 — Access control based on group marking .

## Properties

- **Task ID:** 603
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 🎯 Develop group-based access control so users only see groups with markings matching or below their own.
- ✅ Ensure the feature restricts visibility according to assigned group markings, enhancing security and consistency.

## User story

> *As a user belonging to a single group, I want my access to groups , to be restricted to the markings assigned to my group, so that I only see what I am allowed to access.*

## Acceptance criteria

- **AC1** — Given I browse groups, When access is evaluated, Then I can only see groups whose marking matches or is below my group's assigned markings.

---

# Sub-task: US5 — Highest marking applies when user belongs to multiple groups

## Properties

- **Task ID:** 605
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 2 — Assign Markings to users
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 Highest marking determines user access when belonging to multiple groups.
- 🔄 Access updates automatically when groups are added or removed.

## User story

> *As a user belonging to multiple groups, I want my access level to reflect the highest marking across all my groups, so that I am not unnecessarily restricted.*

## Acceptance criteria

- **AC1** — Given I browse groups, When access is evaluated, Then I can only see groups up to the highest marking level across all my groups.
- **AC2** — Given I am removed from a group, When access is recalculated, Then my access reflects only my remaining groups' markings.

# Task 3 — Assign Markings to Assets

## Properties

- **Task ID:** 592
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Verticals:** #3 Easy-to-Use & consistent platform
- **EPIC:** https://app.notion.com/p/2c58fce17f2a803081dcf80b5a591db9
- **Sub-tasks:**
  - US1 — Assign a marking to an Asset Group
  - US2 — Assign a marking to an Endpoint
  - US3 — Assign a marking to a Security Platform
  - US4 — Assign a marking to a Credential
  - US5 — Only users with the matching marking can see assets

## AI Summary

- 🎯 Assign markings to assets (Asset Groups, Endpoints, Security Platforms) in OpenAEV, mirroring OpenCTI’s model.
- 🔐 Users with proper capability can set, update, or remove markings, controlling asset visibility based on group-marking alignment.
- 📊 Ensure feature flags, breaking-change awareness, and telemetry for usage tracking; keep this field updatable.

---

# 🎯 Business Context (👥 PM + Stakeholders)

## Use Case & Business Goals

Task 3 focuses on assigning marking definitions to **assets** in OpenAEV, following the same model as OpenCTI. Assets are divided into three types: **Asset Groups**, **Endpoints**, and **Security Platforms**. Users with the right capability can assign a marking to any of these asset types via the existing update flow.

Once markings are set on assets:

- Only users whose **group markings match (or are above / include)** the asset’s marking can see it
- Assets with **no marking** remain visible to everyone (until defined otherwise)

## 👤 Users & Personas

- **Administrator** ⇒ manages group membership and group markings (Task 2 dependency)
- **User with the right capability** ⇒ assigns / updates / removes markings on assets
- **Standard user** ⇒ can only view assets within their authorized marking scope

# ⚠️ Important Flags

| Flag | Value |
|---|---|
| Has **Breaking changes** | yes |
| Has **Data Model updates** |  |
| Has **RBAC changes** |  |
| Requires **Feature Flag** |  |
| Targets master (minor release asap) |  |
| Has impact on Import/Export |  |

# 🤝 Decisions Log

| Date | Decision | Validated by Product? | Validated by Technical? | Link to related Meeting |
|---|---|---|---|---|
| --- |  |  |  |  |

---

# 🧠 WHAT DO WE WANT (Business Refinement)

## 🧭 User Flow (mapped to user stories)

### Preconditions

- Marking definitions exist (created/managed in **Task 1**).
- The user has the capability to update the relevant asset type (Asset Group / Endpoint / Security Platform / Credential).

### Flow A — Assign a marking to an asset (Asset Group / Endpoint / Security Platform / Credential)

1. User opens the asset detail page and clicks **Update**. *(US1/US2/US3/US4 depending on asset type)*
2. User selects a **Marking** value and saves. *(US1/US2/US3/US4)*
3. The asset displays the selected marking; user can later change or remove it via the same update flow. *(US1/US2/US3/US4)*

### Flow B — Visibility & access control for marked assets (dependency only for US5)

**Dependency:** requires **Task 2** (group markings) so the platform can compare the asset marking with the user’s effective group markings.

1. User navigates tries to access a specific asset.
2. If the user’s group markings match/cover the asset’s marking, the asset is visible.
3. If not, the asset is hidden / access is denied.
4. Assets with **no marking** remain visible to everyone.

## 📜 User Stories

### User stories pages

- US1 — Assign a marking to an Asset Group
- US2 — Assign a marking to an Endpoint
- US3 — Assign a marking to a Security Platform
- US4 — Assign a marking to a Credential
- US5 — Only users with the matching marking can see assets

# Sub-task: US1 — Assign a marking to an Asset Group

## Properties

- **Task ID:** 606
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 3 — Assign Markings to Assets
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 User story: Assign a marking to an asset group to restrict access based on matching markings.
- ✅ Acceptance criteria: Ability to select, save, view, change, or remove the marking on the Asset Group detail page.

## User story

> *As a user with the right capability, I want to assign a marking to an asset group, so that access to that asset group is restricted to users with the matching marking.*

## Acceptance criteria

- **AC1** — Given I am on the Asset Group detail page, When I click Update, Then I see a marking field where I can select a marking.
- **AC2** — Given I select a marking and save, When I view the Asset Group, Then the assigned marking is displayed.
- **AC3** — Given a marking is already assigned, When I click Update, Then I can change or remove the existing marking.

---

# Sub-task: US2 — Assign a marking to an Endpoint

## Properties

- **Task ID:** 607
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 3 — Assign Markings to Assets
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 Assign a marking to an endpoint to restrict access based on user capabilities.
- 🛠️ Users can add, change, or remove the marking via the Endpoint detail page’s Update function.

## User story

> *As a user with the right capability, I want to assign a marking to an endpoint, so that access to that endpoint is restricted to users with the matching marking.*

## Acceptance criteria

- **AC1** — Given I am on the Endpoint detail page, When I click Update, Then I see a marking field where I can select a marking.
- **AC2** — Given I select a marking and save, When I view the Endpoint, Then the assigned marking is displayed.
- **AC3** — Given a marking is already assigned, When I click Update, Then I can change or remove the existing marking.

---

# Sub-task: US3 — Assign a marking to a Security Platform

## Properties

- **Task ID:** 609
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 3 — Assign Markings to Assets
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 🛡️ Assign a marking to a security platform to restrict access based on matching markings.
- 🔧 Users can add, change, or remove the marking directly from the platform’s detail page.

## User story

> *As a user with the right capability, I want to assign a marking to a security platform, so that access to that security platform is restricted to users with the matching marking.*

## Acceptance criteria

- **AC1** — Given I am on the Security Platform detail page, When I click Update, Then I see a marking field where I can select a marking.
- **AC2** — Given I select a marking and save, When I view the Security Platform, Then the assigned marking is displayed.
- **AC3** — Given a marking is already assigned, When I click Update, Then I can change or remove the existing marking.

---

# Sub-task: US4 — Assign a marking to a Credential

## Properties

- **Task ID:** 620
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 3 — Assign Markings to Assets
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 📌 Assign a marking to a credential to control access based on matching markings.
- ✏️ Users can add, change, or remove the marking via the Credential detail page’s **Marking** field.

## User story

> *As a user with the right capability, I want to assign a marking to a credential, so that access to that credential is restricted to users with the matching marking.*

## Acceptance criteria

- **AC1** — Given I am on the Credential detail page, When I click Update (or Edit), Then I see a **Marking** field where I can select a marking.
- **AC2** — Given I select a marking and save, When I view the Credential, Then the assigned marking is displayed.
- **AC3** — Given a marking is already assigned, When I click Update (or Edit), Then I can change or remove the existing marking.

---

# Sub-task: US5 — Only users with the matching marking can see assets

## Properties

- **Task ID:** 608
- **Status:** Business Refinement needed
- **Status 1:** Not started
- **Parent-task:** Task 3 — Assign Markings to Assets
- **Verticals:** #3 Easy-to-Use & consistent platform

## AI Summary

- 🔐 Enable users to view only assets whose marking matches or is lower than their group's assigned marking.
- 🚫 Deny access to assets with higher markings and allow unrestricted view of unmarked assets.

## User story

> *As a user, I want to only see assets whose marking matches or is below my group's assigned marking, so that I cannot access assets I am not allowed to see.*

## Acceptance criteria

- **AC1** — Given an asset has a marking assigned, When I browse assets, Then I only see assets whose marking matches or is below my group's marking.
- **AC2** — Given an asset has a marking I do not have access to, When I try to access it, Then access is denied.
- **AC3** — Given an asset has no marking assigned, When I browse assets, Then it is visible to all users.