# Filigran Scenarios

The **Filigran catalog** also lists the Scenarios that Filigran publishes for OpenAEV. You browse them in the
**Available** tab of **Scenarios**, install the ones you need, and find them in the **Active** tab.

An installed Scenario stays one Scenario: when Filigran publishes a new version, you update it in place and keep your
targets, teams, schedule and tags.

## Install a Scenario

1. Go to **Scenarios > Available**.
2. Click **Install** on the card.
3. Pick the targets of each Inject, or leave them empty: they are asked again at launch.
4. For a chained Scenario, set its scope: allowed and forbidden targets, timeout and rate limit.

The Actions the Scenario uses are installed with it, at the versions it names. A Scenario that runs a revoked version, or
that needs an Integration you have not deployed, cannot be installed.

To install every Scenario of the catalog at once, click **Install all**. Chained Scenarios and Scenarios that need an
Integration you have not deployed are skipped: install them one by one.

### Install new scenarios automatically

The **Install new scenarios automatically** switch, in the **Available** tab, installs every new Scenario that a Filigran
release brings, with the same rule as **Install all**. It is off by default. A Scenario you uninstalled is never
installed again by itself.

## Update a Scenario

When Filigran publishes a new version, open the Scenario, go to its **Versions** tab and click **Update**.

The dialog shows what Filigran changed and which of your changes are kept. When you and Filigran changed the same part,
choose which version to keep for each conflict, then confirm.

Before every update, OpenAEV keeps a revision of the Scenario. To go back, click **Roll back to this revision** in the
**Versions** tab. A rollback restores the content only: targets, teams, schedule and tags do not move.

Some updates are not possible yet:

- a chained Scenario cannot be updated or rolled back;
- a version that changes articles, objectives or lessons, or an Inject that uses a challenge, an article or a document,
  cannot be applied.

## Scenarios you had before the catalog

At each Filigran release, an imported Scenario that matches a Filigran Scenario (same name, same Injects and same
Actions) is linked to its version. It then shows in the **Active** tab with its version and receives updates.

## Uninstall a Scenario

Open the menu of the card and click **Uninstall**. The Scenario is hidden and can no longer be launched, its schedule
stops, and its past Simulations are kept. The Actions it used stay installed.

## What's next?

- [Filigran catalog and versions](../threat-arsenals/filigran-catalog.md) -- Install and version Filigran Actions
- [Scenario](scenario.md) -- Build and run your own Scenarios
