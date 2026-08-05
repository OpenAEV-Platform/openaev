# Logic Creation

The **Logic** tab is where you build the simulation engine that drives a chained Scenario or Simulation: **Actions** that execute,
and **Events** that decide what runs next. This page details how to build and validate that graph.

## What is the Logic graph?

The Logic graph is made of two building blocks, laid out on a visual canvas:

- **Action**: executes a Threat Arsenal action with the parameters you configure. Represented as a square
  node.
- **Event**: a set of conditions or conditions groups, combined with AND/OR, evaluated against the data produced by Actions or Variables. When its
  conditions are satisfied, it triggers the Actions connected to it.

The chain is formed only in one direction: an Event triggers the Action(s) it is linked to. There is no link the
other way — an Action never connects directly to an Event. Instead, when an Action finishes, its outputs are
recorded, and every Event evaluates its conditions against the recorded data; if an Event's conditions are
satisfied, it triggers the Action(s) it is linked to. Nothing runs until its upstream Event's conditions are met.

![A chained Logic graph: Actions and Events across Privilege Escalation, Credential Access, Discovery, and Lateral Movement phases](assets/logic-creation-canvas-full.png)

## Why use it?

- **Fully customize your run's behavior**: the Logic graph is your own Chaining Engine definition for this
  Scenario or Simulation — however you define it is exactly how the run will behave, letting you customize it to
  your own attack narrative.
- **Branch on real outcomes**: trigger the next step only when the previous one actually produced the expected
  result, instead of always running every Inject.
- **Compose complex attack trees**: combine multiple conditions (AND/OR, nested groups) to model precise decision
  points.
- **Reuse data across steps**: link an Action's argument to the output of a previous Action or to a
  [scope Variable](scope-definition.md#variables), instead of hardcoding values.
- **Catch mistakes early**: the canvas warns you inline when an Event references data that no Action currently
  produces.

## How do I do it?

### Add an Action

1. Click **Add component**, then choose **Action** ("Execute an injector contract with configured parameters").
2. Select a Threat Arsenal action from the list (filterable by injector, platform, domain, tags, or by the output
   type it produces).
3. Configure the Action:
      - Set its **title**.
      - Fill in its arguments. Any argument can be **linked** to a previous Action's output or to a scope Variable
        instead of a static value; linked values can read from the **global** state (shared across the whole run) or
        the **local** state (scoped to the current branch).
      - Configure its Expectations if the action supports them.
4. Save. The Action appears as a node on the canvas.

### Add an Event

1. Click **Add component**, then choose **Event** ("Define conditions to trigger the next actions").
2. Name the Event and, optionally, describe it.
3. Build one or more **Condition groups**. A group holds a list of individual conditions plus, optionally, nested
   sub-groups, and always has its own **AND** / **OR** operator:
      - **AND** requires every condition (and every nested sub-group) in that group to be true.
      - **OR** requires at least one of them to be true.
      - Nesting a sub-group inside a group lets you mix logic — for example, an outer `OR` between two inner `AND`
        groups models "(A and B) or (C and D)". Each condition inside a group sets:
          - **Field to Check**: the output field to inspect. The list of available fields is not a fixed set — it is
            built dynamically from the Threat Arsenal's argument types (execution status, Expectation results, and
            any data field an Action can produce, such as a discovered port, username, or CVE). Each option shows a
            tooltip listing which Action(s) already on the canvas actually produce that field, to help you pick a
            field that is provisioned.
          - **Operator**: `Equals`, `Not equals`, `Is null`, `Is not null`, `Greater than`, `Greater than or equals`,
            `Less than`, `Less than or equals`, `Contains`, `Not contains`. Despite the name, `Contains`/`Not
            contains` performs a set-style match against the field's value rather than a literal list-membership
            check — use it to test whether a field matches one of several expected values.
          - **Expected value** (not required for `Is null` / `Is not null`), with an optional case-sensitive toggle,
            available only for `Equals`, `Not equals`, `Contains`, and `Not contains`.
4. Save, then connect the Event to the Action(s) it should trigger.

!!! note

    If the value you need isn't in the **Field to Check** list, use the **Action Output** field type instead: it
    covers the raw CLI output of any Action on the canvas. Combined with the `Contains` operator, this lets you
    check whether a specific keyword or value shows up anywhere in an Action's output, even if that output isn't
    exposed as a dedicated field.

### Local and global Variables

Beyond the [scope Variables](scope-definition.md#variables) you define upfront, any Action argument can be linked to
data produced by other Actions' outputs during the run, instead of a static value. Local and global scope Variables
are populated from both sources — the scope Variables you defined and the outputs Actions produce as they execute.
When linking an argument, toggle **Limit to Local Scope** to choose where that data is read from:

- **Global scope** (toggle off, the default): the value is read from the run's shared pool of outputs, accumulated
  across the *entire* chained run so far — any Action anywhere upstream can feed it, regardless of which branch
  produced it.
- **Local scope** (toggle on): the value is read only from the *current branch* of the graph — the Action(s) that
  directly precede this one along the path that led to it. Use this when the same field name can be produced by
  several different Actions on different branches, and you need the value from *this* branch specifically, not
  whichever branch happened to run last.

For example, imagine two independent Actions, **Action A** (`NetExec SMB - User Listing`) and
**Action B** (`NetExec SMB - User Listing`), Action A into the same downstream **Action C**
(`NetExec SMB - Password Spray`) that authenticates against the domain controller using the harvested `username`
field:

- If Action C links its `username` argument with the toggle **off** (global scope), it reads whatever `username` was
  most recently written to the shared pool — which could come from either A or B, so Action C might try to
  authenticate with a username enumerated from the wrong endpoint.
- If Action C links it with the toggle **on** (local scope) and is only connected downstream of Action A, it reads
  the `username` produced specifically by Action A on that branch, guaranteeing consistency between the endpoint it
  targets and the credential it uses.

### Validate your graph

You can define an Event whose condition references a field that no Action currently on the canvas actually
produces — for example, an Event checking a `Port` field when no Action on the graph reports a port. In that case,
the platform can't guarantee the Event will ever trigger, so it shows a warning banner listing each affected Event,
the missing field, and an **Add Compatible Action** shortcut that lets you add an Action known to produce that
field directly from the banner.

![Warning banner listing Events that reference fields not yet provisioned by any Action, each with an Add Compatible Action shortcut](assets/logic-creation-warning-banner.png)

### Reading the links between components

Once your graph has Actions and Events connected, click an Event to inspect it: the canvas draws two different kinds
of links to help you trace how data flows into it.

- **Solid links**: the real, saved connections — always an Event triggering the Action(s) it is linked to. This is
  the only kind of link the Chaining Engine actually executes; there is never a saved link running from an Action to
  an Event.
- **Dotted (orange) links**: informational only, never saved, and never a real connection. When an Event is
  selected, the canvas draws a dotted link from every Action that currently *produces* one of the fields used in
  that Event's conditions, even if that Action is not connected to the Event at all. This is just a visual hint —
  "this Action's output could satisfy this Event's condition" — to help you see, at a glance, which Actions already
  on the canvas could feed data into the selected Event's conditions; the same relationship is surfaced by the
  per-field tooltips in the condition builder.

Selecting an Event also dims every Action and Event outside its data-flow path, so the provider Actions, the Event
itself, and the Action(s) it triggers stand out clearly against the rest of the graph.

![An Event selected on the canvas: its data-flow path (numbered dotted links, the triggering Event, and the Action(s) it triggers) stands out while the rest of the graph is dimmed](assets/logic-creation-event-links-dimmed.png)

## Example: chaining a credential discovery attack

Here is a real Logic graph, built with the steps above, that chains a port scan into a credential discovery attempt,
then reuses the harvested credentials for lateral validation:

![Credential discovery chaining example](assets/logic-creation-real-scenario-example.png)

1. **Recon**: `Nmap TCP Connect Scan` probes the target Asset.
2. **Event "Port Validation"**: triggers once the scan reports a non-null port, confirming a service is reachable.
3. **Credential Discovery**: `NetExec FTP Anonymous Get File` attempts to retrieve a file over an anonymous FTP
   session, which may expose credentials.
4. **Event "Credential Validation"**: triggers once that step's output actually contains a credential.
5. **Lateral Validation**: `NetExec SMB Share Listing (auto creds)` reuses that credential to list SMB shares on
   the target, validating whether it grants further access.

If Step 1 finds no open port, or the credential discovery step doesn't return a credential, the downstream Action
is never triggered — each Action only runs once the previous stage's output actually satisfies the Event's
condition.

## What's next?

- [Attack Path Map](attack-path-map.md): see how the chain propagated across your Assets, and review the run's
  results, once it executes.
- [Scope Definition](scope-definition.md): restrict targets and define reusable Variables.
- [Attack Chaining overview](overview.md): back to the feature hub.
