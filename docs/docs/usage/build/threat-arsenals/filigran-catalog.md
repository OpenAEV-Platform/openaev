# Filigran catalog and versions

The **Filigran catalog** lists the Actions that Filigran publishes for OpenAEV. You browse it in the **Available** tab of
the **Threat Arsenal**, install the Actions you need, and find them in the **Active** tab.

Each Action from the catalog has fixed versions: a published version never changes. When Filigran improves an Action, it
publishes a new version next to the previous ones, and you choose when your Injects move to it.

## Why use the Filigran catalog?

- Start with tested Actions instead of writing every command yourself.
- Know exactly what runs: an Inject keeps the version it was built with until you move it.
- Adapt a Filigran Action to your environment and still receive the next Filigran versions.
- Stop an unsafe Action everywhere at once by revoking it.

## Install Actions

1. Go to **Threat Arsenal > Available**.
2. Click an Action to see its details, its versions and what it needs to run.
3. Click **Install** on the card or in the details.
4. Click **Open** to find the installed Action in the **Active** tab.

To install every Action of the catalog at once, click **Install all**.

The **Available** tab also lists the contracts of Integrations that you have not deployed yet. To use one, deploy its
Integration from **Integrations**.

### Install new actions automatically

The **Install new actions automatically** switch, in the **Available** tab, controls what happens when Filigran publishes
a release:

| Situation                                   | Switch off                 | Switch on                        |
|---------------------------------------------|----------------------------|----------------------------------|
| New version of an Action you installed      | Installed                  | Installed                        |
| Action you have never installed             | Stays in **Available**     | Installed at its latest version  |
| Action you uninstalled                      | Stays in **Available**     | Stays in **Available**           |
| Version that Filigran revoked               | Not installed              | Not installed                    |

Installing a new version never changes your Injects: they keep their version until you move them. See
[Move Injects to another version](#move-injects-to-another-version).

Filigran versions are verified by Filigran, so they arrive **approved**: no approver needs to review them
(see [Approval of payloads](threat-arsenals.md#approval-of-payloads)). A customized copy is your own content:
its versions follow the approval rules of the Threat Arsenal.

You need the permission to manage the tenant settings to change this switch.

### Content you had before the catalog

When a Filigran release arrives, OpenAEV links the content you imported earlier to its version in the catalog, without
any notification:

- an Action identical to a released version becomes that version;
- a Scenario with the same name, the same Injects and the same Actions as a released version becomes that version, in
  the **Active** tab. Its targets and your changes are kept.

Content that does not match any released version stays a local copy.

## Versions of an Action

Click an Action in the **Active** tab. Its details show three tabs:

| Tab          | What it shows                                                                                       |
|--------------|-----------------------------------------------------------------------------------------------------|
| **Overview** | The version picked in the version list, by default the latest one you can use.                      |
| **Versions** | Every version, installed or not, with the Injects, Atomic Tests and chained steps that use it.      |
| **History**  | Who installed, updated, customized or revoked each version, and when, with the changes field by field. |

### Move Injects to another version

1. Open the **Versions** tab of the Action.
2. Expand the version the Injects use today.
3. Select the Injects to move, then pick the target version.
4. Click **Move**. If the target version is not installed yet, OpenAEV installs it first.

## Run a test

Click **Run a test** on a card or at the top of the Action details. You can create a new Scenario with the Action, add it
to an existing Scenario, or run it at once as an Atomic Test. See
[Run a test from bulk selection](threat-arsenals.md#run-a-test-from-bulk-selection).

## Customize a Filigran Action

A Filigran Action cannot be edited, but you can make your own copy of it and still receive Filigran updates.

1. Open the menu of the Action and click **Customize**.
2. The edit form opens. Fields you cannot change are read only; next to a changed field, **Revert to Filigran value**
   restores the original value.
3. Click **Save**. Your copy gets a new version each time its content changes. Changing only its tags keeps the version.

### Update a customized copy

When Filigran publishes a new version of the Action your copy is based on, the copy shows
**Filigran vX is available**.

1. Open the copy with **Edit customization**, then click **Update**.
2. If Filigran changed a field that you also changed, choose which value to keep for each conflict.
3. Read **What's new** and **Your changes, kept**, then confirm.

Every field you did not change takes the new Filigran value.

## Revoke a version

Revoke a version when it is no longer safe to run, for example after you find a flaw in its command.

1. Open the menu of the Action and click **Revoke**.
2. Pick the version and give a reason.

A revoked version no longer runs in your tenant:

- the Inject list marks its Injects with **Payload revoked**;
- a Scenario or Simulation that uses it shows **Revoked action** in its configuration checks, with a link to its Injects;
- the Simulation still launches, but its Injects on the revoked version fail with an error that gives the reason of the revocation.

To allow the version again, open the menu and click **Lift revocation**. **Revoke** and **Lift revocation** appear only
when a version can take them, and only for users whose role has the **Revoke threat arsenal** capability.

Filigran can also revoke a version in a release. The version then shows **Revoked** with the reason, and it no longer runs
in your tenant.

!!! note "Customized copies"

    A customized copy based on a revoked Filigran version is revoked too, since its command may carry the same flaw. To
    run it anyway, click **Lift revocation** and explain why your copy is safe.

## Get notified

Nobody is notified by default. To follow these events, create a trigger in **Notification center > Triggers**, or click
the bell at the top of a Scenario to follow that Scenario only.

| Resource         | Event                            | Sent when                                                                    |
|------------------|----------------------------------|------------------------------------------------------------------------------|
| Scenario         | **Payload revoked**              | A version that its Injects use is revoked                                    |
| Scenario         | **New action version available** | A newer version exists for an Action its Injects use, Filigran or local     |
| Payload          | **Revoked**                      | You, a colleague or Filigran revokes a version                               |
| Payload          | **Revocation lifted**            | A revocation is lifted                                                       |
| Payload          | **Installed automatically**      | A Filigran release installs a version by itself                              |
| Payload          | **Filigran update available**    | Filigran publishes a new version of the Action a customized copy is based on |
| Filigran catalog | **Release received**             | A Filigran release arrives: new Scenarios, updates of installed Scenarios, new Actions |

The first release an instance receives fills its catalog without any notification. Linking a local Action to the
identical Filigran version does not notify either. A recipient is only notified about Scenarios with the **Access
assessment** capability, and about Actions and the Filigran catalog with the **Access threat arsenal** capability.

When a Filigran release is partly refused, for example because an archive does not match its manifest, platform
administrators receive a **Catalog sync blocked** alert without any trigger.

## Uninstall an Action

Open the menu of the Action and click **Uninstall**. The Action goes back to the **Available** tab, and its new versions
no longer install themselves. You cannot uninstall an Action that a Scenario, an Atomic Test or a chained step still
uses: move or delete them first.

## What's next?

- [Actions](threat-arsenals.md) -- Create, update and run your own Actions
- [Action properties](action-properties.md) -- Reference of every field of the Action form
- [Injects](../../run-and-evaluate/injects/inject-overview.md) -- Use Actions in Injects
- [Atomic testing](../../run-and-evaluate/atomic-testing/atomic-testing.md) -- Run individual Actions as Atomic Tests
