# Users

You can manage users in `Settings > Security > Users`. If you are using Single Sign-On (SSO), user accounts in OpenAEV are automatically created upon login.

![User list](assets/user-list.png)

To create a user, just click on the `+` button :

![Create user](assets/user-creation.png)
![Create user](assets/user-creation-input.png)

To update a user, click on the ellipsis menu (⋮) : 

![User manage](assets/user-update.png)

Here, you can modify parameters such as the organization, phone number, password, and even your GPG public key : 

![User manage](assets/user-update-input.png)
![User manage](assets/user-update-pwd.png)

To delete a user : 

![User manage](assets/user-delete.png)


# User permissions

## What is RBAC

Role-Based Access Control (RBAC) is the way OpenAEV manages who can do what inside the platform.  
Each user belongs to a group, and this group has one or more roles that define its **capabilities**.

Capabilities determine what features a user can access.  
If a user does not have the right capability, the option will simply not be available to them.

In addition to general capabilities, OpenAEV also supports **grants**. Grants are more precise: they allow access to a specific resource, such as one particular simulation, without giving the user access to all simulations.

!!! warning "Default read access"

    Some elements in OpenAEV are always visible to all users, regardless of their assigned capabilities or grants.
    
    By default, the following features are open for everyone:
      
      - **Teams**
      - **Players**
      - **Taxonomies** (in the Settings)
    
    Users can view these elements without needing any specific capability, but additional rights are required if they want to manage them.

--- 

## How to create a role

To create a new role in OpenAEV:

1. Go to **Settings → Security → Roles**.
2. Click on **Create role**. Enter a **name** and an optional **description** for the role
3. Select the **capabilities** that should be included in this role, such as:
    - Access assets
    - Manage dashboards
    - Delete documents
    - ...
4. Save the role.

!!! info "Hierarchical permissions"

    Permissions are organized hierarchically by indentation: selecting a permission further to the right (e.g., Delete) will automatically enable the less-indented ones that precede it (e.g., Manage and Access).


!!! tip "Bypass"

    If you want a user to automatically have all capabilities without restriction, you can enable the **Bypass** capability in their role.


!!! tip "Assessments"

    This capability combines scenarios, simulations and atomic testings. 

Once the role is created, it can be assigned to a **group**. All users in that group will automatically inherit the role’s permissions.

### Example : Crisis content creator

> Role : Crisis content creator

**Context:** This user is in charge of designing crisis management content. Their role is to create **scenarios** that can later be reused by other teams to run simulations.  
For example, they might build an **“Earthquake Crisis Scenario”**.

**Capabilities:**

- **Platform settings** : Manage groups to assign them grants
- **Assessments** : Create scenario

With this role, the user can design new scenarios, and configure everything needed to prepare simulations.  
For instance, they may create a **“Earthquake Crisis Template”**, which becomes the foundation for future simulations.

![Create role](assets/create-role.png)
![Assign capabilities](assets/assign-capabilities.png)

Then, the user will be able to create scenario, launch it and grant their team on this simulation.

---

## Grants

### How to grant a simulation to a user

Beyond global **capabilities** defined in roles, OpenAEV also allows assigning more precise **grants**. Grants define permissions on specific resources (for example, one simulation), and they are always managed at the **group** level.

**To grant a simulation to a user:**

1. Go to **Settings → Security → Groups**.
2. Click on **Manage grants** in the group options.
3. A drawer will open with the available resources:
    - Simulations
    - Scenarios
    - Organizations
    - Atomic testings
    - Payloads
4. Select the specific items you want the group to access and assign the appropriate grant level.

   ![Manage grants](assets/manage-grants.png)

---

### Types of Grants

There are three levels of granularity:

| Grant   | Rights included                           |
|---------|-------------------------------------------|
| Access  | View only                                 |
| Manage  | View, edit, delete                        |
| Launch  | Manage rights + ability to launch tests   |

---

### Example : Local coordinator


> Role : Local coordinator


**Context:** This user is not a global content creator. Instead, they are trained locally to run a specific simulation designed by the content creator.  
They do not need all capabilities — only access to the resources explicitly granted to them.

**Grants assigned through their group:**

- **Simulation** → *Launch* on the simulation based on the “Earthquake Crisis”

**Concrete workflow:**

- The **Content Creator** travels to the **French Embassy** and trains a local coordinator.
- This coordinator is granted launch to the simulation created from the *Earthquake Crisis Scenario*.
- The coordinator can now run and manage this simulation, but cannot see or modify other simulations or scenarios.
- Later, the same process is repeated at the **UK Embassy**, where another coordinator is granted launch only to the local simulation derived from the same scenario.

---

### Special Cases

!!! tip "Simulations, Scenarios, and Atomic Testing"

    A user can access these either through specific **grants**, or globally if the group has the **ASSESSMENT** capability (which overrides individual grants).

!!! tip "Payloads"

    Access is given either through specific **grants**, or globally if the group has the **PAYLOAD** capability.

    
---
## Capability Dependencies

In some cases, performing an action in OpenAEV requires more than one capability.  
If a required capability is missing, the action will be blocked and a warning message will explain which capability is missing.

### Example

- In **Scenarios**, when creating an article, the user also needs the capability to **access Channels**.
- If the user does not have this capability, the article cannot be created.
- A warning will be displayed, indicating that the necessary capability is missing.

  ![Missing capability](assets/warning-missing-capabilities.png)

This mechanism ensures consistency across the platform: actions that depend on other features cannot be performed without the proper access.

