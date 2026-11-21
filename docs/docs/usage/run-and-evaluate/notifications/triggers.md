# Triggers

The **Triggers** tab of the Notification Center defines what you get notified about, and how. This page details how
to create, configure, and manage Triggers, including the quick-subscribe shortcut available on a Scenario's page.

## What Is a Trigger?

A Trigger is a personal rule owned by the user who created it: it watches for events on a Resource type and sends
matching Notifications through one or more Notifiers. Every Trigger is one of two types:

- **Live**: fires immediately for every matching event on the Resource type you selected, optionally narrowed down
  by Filters.
- **Digest**: does not watch a Resource type directly. Instead, it composes one or more of your existing Live
  Triggers and rolls up everything they would have fired into a single periodic summary: hourly, daily, weekly (on
  a chosen day), or monthly (on a chosen day of month, at a chosen time in UTC).

Both types share the same two remaining settings:

- **Notifiers**: where the Notification is sent (the built-in in-app Notifier, an Email Notifier, or a Webhook
  Notifier). Notifiers are configured by an administrator in **Settings > Customization > Notifiers**; only
  Notifiers that already exist there appear in this list.
- **Enabled**: a Trigger can be disabled without deleting it, to pause it temporarily.

![Notification Center Triggers tab: four Live Triggers, New critical finding discovered (Finding, Create), Simulation status changes (Simulation, Update), Scenario detection score dropped (Scenario, Score degradation), New endpoint agent enrolled (Agent, Create), and one Digest Trigger, Daily SOC digest (08:00 UTC), composing 4 of them on a Day period, all shown as Enabled](assets/triggers-list.png)

## Why Use It?

- **Watch exactly what matters to you**: pick a Resource type, narrow it down with Filters, and choose only the
  Event types you care about.
- **Avoid alert fatigue**: compose several Live Triggers into a single Digest so you get one periodic summary
  instead of many individual Notifications.
- **Reuse existing Notifiers**: send to Email or Webhook without having to configure delivery details on every
  Trigger yourself.
- **Subscribe in a single click**: use the quick-subscribe bell on a Scenario's own page when you only need an
  all-events subscription on that exact Scenario.

## How Do I Do It?

### Create a Live Trigger

1. Open the **Notification Center** (bell icon in the top navigation bar) and select the **Triggers** tab.
2. Click **Create Live trigger**.
3. Name the Trigger.
4. Select a **Resource type**: Scenario, Simulation, Inject, Finding, Asset, Asset group, Team, Player, Payload,
   Vulnerability, Security platform, Document, or Challenge.
5. Select one or more **Event types**: **Create**, **Update**, **Delete**. For the Scenario Resource type, a
   fourth option, **Score degradation**, is also available: it fires when a Simulation's score drops below the
   score of the previous run for the same Scenario. For the Simulation Resource type, two more options are
   available: **Exposure result ready** fires once when a Simulation finishes, with a message that gives
   the number of exposed Assets and the path of its exposure result (see
   [Exposure result](#exposure-result) below); **Critical asset reached** fires once per Asset when the
   attack path of the finished Simulation reaches an Asset whose criticality is High or Very high.
6. Optionally, add **Filters** to only match entities that satisfy specific conditions (for example, a Simulation
   filtered to a specific Tag). Leave the filters empty to match every entity of the selected Resource type.
7. Select one or more **Notifiers**.
8. Confirm.

!!! note "Filters on sensitive Finding values"

    On the Finding Resource type, a filter on the value of a sensitive Finding, such as a credential,
    matches its masked form only: `admin` matches `admin:mo******`, while the hidden part of the
    password matches nothing. See
    [Searching sensitive Findings](../findings/findings.md#searching-sensitive-findings).

![Create a live trigger drawer: Name set to Scenario Score Degradation, Resource type set to Scenario, Create, Update, and Score degradation checked (Delete unchecked), empty Filters, and the Notifiers dropdown open with Soc slack channel (#soc-alerts) selected alongside User interface and Default mailer](assets/create-live-trigger-form.png)

### Create a Digest Trigger

1. From the **Triggers** tab of the Notification Center, click **Create Regular digest**.
2. Name the Trigger.
3. Select the **Composed triggers**: one or more of your existing Live Triggers to roll up.
4. Select a **Period**: **Hour**, **Day**, **Week**, or **Month**. Week and Month additionally ask for a day (day
   of the week, or day of the month), and every period except Hour asks for a time, in UTC.
5. Select one or more **Notifiers**.
6. Confirm.

![Create a regular digest drawer: Name set to Daily Digest for Scenarios, Composed triggers set to Scenario detection score dropped, Period set to Day, Time 09:00 UTC, and the Notifiers dropdown open with Default mailer selected alongside User interface and SOC Slack channel (#soc-alerts)](assets/create-digest-trigger-form.png)

!!! note

    A Digest can only compose Live Triggers. Digests cannot be nested inside one another.

### Manage a Trigger

From the **Triggers** list, use the row's action menu to:

- **Update**: reopen the same form used at creation, prefilled with the current values.
- **Enable** / **Disable**: pause or resume a Trigger without deleting it.
- **Delete**: permanently remove the Trigger.

### Quick-Subscribe From a Scenario's Page

A Scenario's page shows a subscribe bell in its header, next to the Scenario's name.

1. Open the Scenario you want to watch.
2. Click the bell icon in its header. This immediately creates a Live Trigger scoped to that exact Scenario, watching
   every Event type (Create, Update, Delete, and Score degradation), sent to the built-in in-app Notifier. The bell
   turns green to confirm the subscription.
3. Click the green bell again to reopen the same Trigger in an edit drawer, for example, to add an Email
   Notifier, or to unsubscribe with the **Delete** button.

!!! tip

    Use the quick-subscribe bell when you only need an all-events, in-app subscription on one specific Scenario.
    Use the **Triggers** page for any other Resource type, for Filters, for specific Event types, or for a Digest.

## Example: Watching a Simulation's Score and Getting a Weekly Summary

Imagine you run weekly Simulations against the same Scenario and want an immediate alert if the score regresses,
plus a weekly summary of every change across your Scenarios and Simulations.

1. **Live trigger: score regressions**: create a Live Trigger named `Score regressions`, Resource type
   **Scenario**, Event type **Score degradation** only, no Filters, Notifier set to **Email**. You now get an
   Email the moment any Simulation run scores lower than the previous run of the same Scenario.
2. **Live trigger: simulation activity**: create a second Live Trigger named `Simulation activity`, Resource type
   **Simulation**, Event types **Create** and **Update**, Notifier set to the built-in in-app Notifier.
3. **Digest: weekly summary**: create a Digest Trigger named `Weekly summary`, composing both `Score regressions`
   and `Simulation activity`, Period **Week**, day **Monday**, time `09:00`, Notifier set to **Email**.

You now get an immediate Email whenever a score regresses, plus a single Monday morning Email summarizing every
Simulation created, updated, or scored lower that week, instead of one Email per individual event.

## Exposure result

Every finished Simulation has an exposure result, read with
`GET /api/exercises/{simulationId}/exposure-result` (the tenant route `/api/tenants/{tenantId}/exercises/...`
works too) by any user who can read the Simulation. It is a versioned contract (`schema_version`, currently
`"1"`): a field is only ever added within a version.

- `subjects`: one entry per Vulnerability (`type: vulnerability`, CVE as `external_id`) or ATT&CK technique
  (`type: technique`) the Injects tested, or the Inject itself when its contract names neither. Each lists the
  Assets tested with a `verdict`: `exposed` (vulnerable, or the technique was neither prevented nor detected),
  `detected`, `prevented`, `not_exposed` or `not_tested` (no result yet), a `confidence` (`high` when every
  Expectation has a result, `medium` when some do, `low` when none do) and the `conditions` checked (one per
  Expectation: its type, expected score, score and status).
- `reached_assets`: Assets of the Simulation's attack path that no Inject targeted directly, with their
  criticality; `critical` is true for High and Very high.
- `summary`: distinct Assets per verdict (an Asset counts once, under its most severe verdict) and the number of
  critical Assets reached.
- `coverage_external_id`: the external id of the security coverage the Simulation was built from, when there is
  one.

## What's Next?

- [Notification Center](notification-center.md) -- Review, filter, and manage the Notifications a Trigger produces
- [Notifications & Triggers overview](overview.md) -- Back to the feature hub
