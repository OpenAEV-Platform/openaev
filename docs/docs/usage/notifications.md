# Notifications

Notifications alert you when important events occur on the platform. You configure them as **triggers** (what to watch) and **notifiers** (how you are told).

The older Scenario score-degradation email is still available: it is now a live trigger on the Scenario resource with the Score degradation event.

## Why use Notifications?

- React as soon as a Finding, Simulation, or other resource is created, updated, or deleted.
- Receive a periodic digest instead of a notification for every event.
- Keep the Scenario score-degradation alert, and attach it to UI, email, or webhook notifiers.

## Triggers

Open **Profile > Triggers**. Create a **live trigger** or a **regular digest**.

### Live triggers

A live trigger fires one notification for each matching event, as it happens.

1. Click **Create Live trigger**.
2. Name the trigger.
3. Choose the **resource type** to watch.
4. Select one or more **event types**.
5. Optionally add **filters** so only matching entities notify you. Leave the filters empty to match every entity of that type.
6. Choose one or more **notifiers**.
7. Save.

### Digests

A digest collects matching events and sends them together on a schedule.

1. Click **Create Regular digest**.
2. Name the digest.
3. Choose the aggregation **period**: hour, day, week, or month. Weekly and monthly digests also take a weekday and time.
4. Attach the **live triggers** whose events should be included.
5. Choose one or more **notifiers**.
6. Save.

### Resource types

A trigger watches one of these resource types:

| Resource | Notes |
|---|---|
| Scenario | Includes the Score degradation event (see below) |
| Simulation | |
| Inject | |
| Finding | Asset filters cover every asset category except security platforms |
| Asset | Hosts, cloud resources, web applications, AI targets, and the other inventory categories |
| Asset group | |
| Team | |
| Player | |
| Payload | |
| Vulnerability | |
| Security platform | |
| Document | |
| Challenge | |

### Event types

| Event | When it fires |
|---|---|
| Creation | A new entity of the selected type is created |
| Modification | An existing entity is updated |
| Deletion | An entity is deleted |
| Score degradation | Only for **Scenario**. A Simulation score drops below the previous run of the same Scenario (the former notification-rule trigger) |

### Filters

Filters restrict a live trigger to entities you care about. Empty filters match everything of that type.

For Findings (and other asset-scoped criteria), the asset picker lists the **unified inventory**: hosts, containers, cloud resources, web applications, network and mobile devices, identities, SaaS and AI targets, and generic assets. **Security platforms are excluded** from that picker.

## Notifiers

Notifiers are the delivery channels a trigger can use.

| Channel | Where it is configured |
|---|---|
| UI | Built in. Notifications appear in the in-app center |
| Email | **Settings > Customization > Notifiers**. Optional subject and template |
| Webhook | **Settings > Customization > Notifiers**. HTTP URL (http/https), verb, optional template and headers |

You can create email and webhook notifiers there, then select them on a trigger. The UI notifier does not need to be created.

## In-app notification center

Open **Profile > Notifications**, or the bell in the top navigation bar. Notifications appear as unread items and can be:

- Opened (live notifications jump to the entity when it still exists; digests open a grouped detail)
- Marked as read individually or in bulk
- Deleted in bulk
- Searched and filtered

## What's next?

- [Scenario](build/scenario/scenario.md) -- Create and manage Scenarios
- [Simulation](evaluate/simulation/simulation.md) -- Run Simulations and track results
- [Findings](evaluate/findings/findings.md) -- Review extracted security insights
- [Assets](build/assets.md) -- Manage the asset inventory
