# Users

You can manage users in **Settings > Security > Users**. If you are using Single Sign-On (SSO), user accounts in OpenAEV are automatically created upon login.

![User list](assets/user-list.png)

To create a user, click on the `+` button:

![Create user](assets/user-creation.png)
![Create user](assets/user-creation-input.png)

To update a user, click on the ellipsis menu:

![User manage](assets/user-update.png)

Here, you can modify parameters such as the organization, phone number, password, and even your GPG public key:

![User manage](assets/user-update-input.png)
![User manage](assets/user-update-pwd.png)

To delete a user:

![User manage](assets/user-delete.png)

The platform administrator account cannot be deleted, and neither can your own account. In both
cases the delete action stays visible but disabled, and hovering it explains why. Administrator
accounts are also flagged in the list so the restriction is visible without opening the menu.


# User permissions

## What is RBAC

Role-Based Access Control (RBAC) is the way OpenAEV manages who can do what inside the platform.
Each user belongs to a group, and this group has one or more roles that define its **capabilities**.

Capabilities determine what features a user can access.
If a user does not have the right capability, the option will simply not be available to them.

In addition to general capabilities, OpenAEV also supports **grants**. Grants are more precise: they allow access to a specific resource, such as one particular Simulation, without giving the user access to all Simulations.

!!! warning "Default read access"

    Some elements in OpenAEV are always visible to all users, regardless of their assigned capabilities or grants.

    By default, the following features are open for everyone:

      - **Teams**
      - **Players**
      - **Taxonomies** (in the Settings)

    Users can view these elements without needing any specific capability, but additional rights are required if they want to manage them.

## How to create a role

To create a new role in OpenAEV:

1. Go to **Settings > Security > Roles**.
2. Click on **Create role**. Enter a **name** and an optional **description** for the role.
3. Select the **capabilities** that should be included in this role.
4. Save the role.

### Capabilities

Capabilities in OpenAEV are organized hierarchically. A parent capability (e.g. `Access assessment`) must be granted before its children (e.g. `Manage assessment`, `Delete assessment`) can be assigned. Indentation below reflects this hierarchy.

Below is a full list of capabilities in OpenAEV:

| Capability | Description                                                                                                                               |
|:-----------|:------------------------------------------------------------------------------------------------------------------------------------------|
| `Bypass (user has all rights)` | Grants unconditional access to all platform features, bypassing every individual capability check and any data segregation enforcement.   |
| **Assessments: Scenarios, Simulations and Atomic Tests** |                                                                                                                                           |
| `Access assessment` | Read-only access to assessments, including Scenarios, Simulations and Atomic Tests.                                                       |
| &nbsp;&nbsp;`Manage assessment` | Create and update assessments (Scenarios, Simulations, Atomic Tests). Requires *Access assessment*.                                       |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete assessment` | Permanently delete assessments. Requires *Manage assessment*.                                                                             |
| &nbsp;&nbsp;`Launch assessment` | Execute / run an assessment against defined targets. Requires *Access assessment*.                                                        |
| **Targets** |                                                                                                                                           |
| `Access Teams & Players` | Read-only access to Teams and Player definitions used as assessment targets.                                                              |
| &nbsp;&nbsp;`Manage Teams & Players` | Create and update Teams and Players. Requires *Access Teams & Players*.                                                                   |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete Teams & Players` | Permanently delete Teams and Players. Requires *Manage Teams & Players*.                                                                  |
| `Access Assets` | Read-only access to Asset inventory (hosts, endpoints, and other infrastructure targets).                                                 |
| &nbsp;&nbsp;`Manage Assets` | Create and update Assets in the inventory. Requires *Access Assets*.                                                                      |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete Assets` | Permanently delete Assets from the inventory. Requires *Manage Assets*.                                                                   |
| `Access security platforms` | Read-only access to integrated security platform configurations (e.g. SIEM, EDR, firewall connectors).                                    |
| &nbsp;&nbsp;`Manage security platforms` | Create and update security platform integrations. Requires *Access security platforms*.                                                   |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete security platforms` | Permanently delete security platform integrations. Requires *Manage security platforms*.                                                  |
| **Threat arsenal actions** |                                                                                                                                           |
| `Access threat arsenal actions` | Read-only access to the threat arsenal action library (attack scripts, tools, and techniques used in Simulations).                        |
| &nbsp;&nbsp;`Manage threat arsenal actions` | Create and update threat arsenal actions in the library. Requires *Access threat arsenal actions*.                                        |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete threat arsenal actions` | Permanently delete threat arsenal actions from the library. Requires *Manage threat arsenal actions*.                                     |
|  **Reporting** |                                                                                                                                           |
| `Access reporting` | Read-only access to tenant reporting and generated reports.                                                                               |
| &nbsp;&nbsp;`Manage reporting` | Create, update, and configure reporting content. Requires *Access reporting*.                                                             |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete reporting` | Permanently delete reporting content. Requires *Manage reporting*.                                                                        |
**Dashboards** |                                                                                                                                           |
| `Access Dashboards` | Read-only access to platform Dashboards and their visualizations.                                                                         |
| &nbsp;&nbsp;`Manage Dashboards` | Create, update, and configure Dashboards. Requires *Access Dashboards*.                                                                   |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete Dashboards` | Permanently delete Dashboards. Requires *Manage Dashboards*.                                                                              |
| **Findings** |                                                                                                                                           |
| `Access Findings` | Read-only access to assessment Findings and results generated from Simulations and Atomic Tests.                                          |
| **Content** |                                                                                                                                           |
| `Access documents` | Read-only access to documents stored in the platform (reports, attachments, playbooks).                                                   |
| &nbsp;&nbsp;`Manage documents` | Upload, create, and update documents. Requires *Access documents*.                                                                        |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete documents` | Permanently delete documents. Requires *Manage documents*.                                                                                |
| `Access channels` | Read-only access to communication channels used to deliver exercise Injects to Players.                                                   |
| &nbsp;&nbsp;`Manage channels` | Create and update channels. Requires *Access channels*.                                                                                   |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete channels` | Permanently delete channels. Requires *Manage channels*.                                                                                  |
| `Access phishing` | Read-only access to phishing Landing Pages and Email Templates.                                                                           |
| &nbsp;&nbsp;`Manage phishing` | Create, update, and duplicate phishing Landing Pages and Email Templates. Requires *Access phishing*.                                     |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete phishing` | Permanently delete phishing Landing Pages and Email Templates. Requires *Manage phishing*.                                                |
| `Access challenges` | Read-only access to challenges (CTF-style tasks or objectives assigned to Players during exercises).                                      |
| &nbsp;&nbsp;`Manage challenges` | Create and update challenges. Requires *Access challenges*.                                                                               |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete challenges` | Permanently delete challenges. Requires *Manage challenges*.                                                                              |
| `Access lessons learned` | Read-only access to lessons learned records captured after assessments or exercises.                                                      |
| &nbsp;&nbsp;`Manage lessons learned` | Create and update lessons learned entries. Requires *Access lessons learned*.                                                             |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete lessons learned` | Permanently delete lessons learned entries. Requires *Manage lessons learned*.                                                            |
 **Tenant Settings** |                                                                                                                                           |
| `Access tenant settings` | Read-only access to tenant-level configuration and administration settings.                                                               |
| &nbsp;&nbsp;`Manage tenant settings` | Create, update, and configure tenant-level settings. Requires *Access tenant settings*.                                                   |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete tenant settings` | Permanently delete tenant-level settings. Requires *Manage tenant settings*.                                                              |
| **Tags** |                                                                                                                                           |
| `Access tags` | Read-only access to the tags page.                                                                                                        |
| &nbsp;&nbsp;`Manage tags` | Create, update, and configure tags. Requires *Access tags*.                                                                               |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete tags` | Permanently delete tags. Requires *Manage tags*.                                                                                          |
| **Platform settings** |                                                                                                                                           |
| `Access platform settings` | Read-only access to platform-wide configuration and administration settings.                                                              |
| &nbsp;&nbsp;`Manage platform settings` | Modify platform-wide settings including security configuration, integrations, and system parameters. Requires *Access platform settings*. |
| **Tenant settings** |                                                                                                                                           |
| `Access tenant settings` | Read-only access to the tenant administration surface: tags, tag rules, attack patterns, organizations, collectors, injectors, notifiers. |
| &nbsp;&nbsp;`Manage tenant settings` | Create and update them. Requires *Access tenant settings*.                                                                                |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete tenant settings` | Permanently delete them. Requires *Manage tenant settings*.                                                                               |
| **Security** |                                                                                                                                           |
| `Access tenant users, groups and roles` | Read-only access to the tenant's users, groups and roles.                                                                                 |
| &nbsp;&nbsp;`Manage tenant users, groups and roles` | Create and update the tenant's users, groups and roles. Requires *Access tenant users, groups and roles*.                                 |
| &nbsp;&nbsp;&nbsp;&nbsp;`Delete tenant users, groups and roles` | Permanently delete them. Requires *Manage tenant users, groups and roles*.                                                                |




!!! note "Hierarchical permissions"

    Permissions are organized hierarchically by indentation: selecting a permission further to the right (e.g., Delete) will automatically enable the less-indented ones that precede it (e.g., Manage and Access).


!!! tip "Bypass"

    If you want a user to automatically have all capabilities without restriction, you can enable the **Bypass** capability in their role.


!!! tip "Assessments"

    This capability combines Scenarios, Simulations and Atomic Tests.

Once the role is created, it can be assigned to a **group**. All users in that group will automatically inherit the role's permissions.


## Delegating capabilities

A user can only grant what they hold themselves. This prevents privilege escalation: no one can widen their own reach, or someone else's, beyond their own capabilities. The rule is enforced by the API, and the interface shows it before anything is submitted.

Users with the `Bypass (user has all rights)` capability hold everything, so they never see these restrictions.

### In a role

When creating or updating a role, capabilities you do not hold are shown in grey with a padlock, and their checkbox is disabled. A capability group whose entire content is locked is greyed as a whole.

![Locked capabilities in a role](assets/capability-lock-role.png)

A locked capability that the role **already carries** stays removable: you can narrow an existing role even where you could not have created it. What you cannot do is add such a capability back. If a restricted capability is still selected when you save, the form refuses and lists the capabilities to remove.

!!! warning "Narrowing is possible, widening is not"

    Removing a capability you do not hold is allowed, and it is a one-way door: once removed and saved, you will not be able to put it back.

### In a group's roles

The same rule applies when attaching roles to a group. A role carrying at least one capability you do not hold is locked in the picker, and the **Update** button stays disabled while such a role is selected.

![Locked roles in a group](assets/capability-lock-group-roles.png)

Here too, a restricted role already attached to the group can be detached, but not re-attached.

### In a group's members

Group membership is governed by the capabilities the group's own roles carry. If those roles include capabilities you do not hold, adding or removing a member would indirectly grant or revoke them, so the whole member list is frozen and a message names the missing capabilities.

![Locked group membership](assets/capability-lock-group-users.png)

!!! tip "Getting access"

    These restrictions follow your own capabilities, not your seniority. To manage a role or a group you are locked out of, ask an administrator to grant you the missing capabilities listed in the message.



## Example: creating a crisis content creator role

> Role: Crisis content creator

**Context:** This user is in charge of designing crisis management content. Their role is to create **Scenarios** that can later be reused by other Teams to run Simulations.
For example, they might build an **"Earthquake Crisis Scenario"**.

**Capabilities:**

- **Platform settings**: Manage groups to assign them grants
- **Assessments**: Create Scenario

With this role, the user can design new Scenarios, and configure everything needed to prepare Simulations.
For instance, they may create a **"Earthquake Crisis Template"**, which becomes the foundation for future Simulations.

![Create role](assets/create-role.png)
![Assign capabilities](assets/assign-capabilities.png)

Then, the user will be able to create a Scenario, launch it and grant their Team on this Simulation.

## Grants

### How to grant a Simulation to a user

Beyond global **capabilities** defined in roles, OpenAEV also allows assigning more precise **grants**. Grants define permissions on specific resources (for example, one Simulation), and they are always managed at the **group** level.

**To grant a Simulation to a user:**

1. Go to **Settings > Security > Groups**.
2. Click on **Manage grants** in the group options.
3. A drawer will open with the available resources:
    - Simulations
    - Scenarios
    - Organizations
    - Atomic Tests
    - Threat arsenal actions
4. Select the specific items you want the group to access and assign the appropriate grant level.

   ![Manage grants](assets/manage-grants.png)

### Types of grants

There are three levels of granularity:

| Grant   | Rights included                       |
|---------|---------------------------------------|
| Access  | View only                             |
| Manage  | Access + edit and delete              |
| Launch  | Manage + ability to launch tests      |

### Example: local coordinator


> Role: Local coordinator


**Context:** This user is not a global content creator. Instead, they are trained locally to run a specific Simulation designed by the content creator.
They do not need all capabilities -- only access to the resources explicitly granted to them.

**Grants assigned through their group:**

- **Simulation** -- *Launch* on the Simulation based on the "Earthquake Crisis"

**Concrete workflow:**

- The **Content Creator** travels to the **French Embassy** and trains a local coordinator.
- This coordinator is granted launch to the Simulation created from the *Earthquake Crisis Scenario*.
- The coordinator can now run and manage this Simulation, but cannot see or modify other Simulations or Scenarios.
- Later, the same process is repeated at the **UK Embassy**, where another coordinator is granted launch only to the local Simulation derived from the same Scenario.

### Special cases

!!! tip "Simulations, Scenarios, and Atomic Tests"

    A user can access these either through specific **grants**, or globally if the group has the **ASSESSMENT** capability (which overrides individual grants).

!!! tip "Threat arsenal actions"

    Access is given either through specific **grants**, or globally if the group has the **PAYLOAD** capability.


## Capability dependencies

In some cases, performing an action in OpenAEV requires more than one capability.
If a required capability is missing, the action will be blocked and a warning message will explain which capability is missing.

### Example

- In **Scenarios**, when creating an article, the user also needs the capability to **access Channels**.
- If the user does not have this capability, the article cannot be created.
- A warning will be displayed, indicating that the necessary capability is missing.

  ![Missing capability](assets/warning-missing-capabilities.png)

This mechanism ensures consistency across the platform: actions that depend on other features cannot be performed without the proper access.
