# Action Selection

Before an Action can run inside a chained Logic graph, you first need to pick which Threat Arsenal action it
executes, then decide how its arguments get their values. This page covers both: browsing and filtering the Threat
Arsenal catalog, and linking an argument to a scope Variable or to another Action's output, in either global or
local scope.

## What is Action Selection?

Every **Action** node on the canvas wraps a single Threat Arsenal action (an injector contract) plus the configured
arguments it runs with. Action Selection is the step where you:

- Browse the Threat Arsenal catalog and filter it down to the action you need.
- Configure that action's arguments, optionally linking any of them to data instead of a static value.

## Why use it?

- **Find the right action fast**: the Threat Arsenal can hold hundreds of actions across injectors, platforms, and
  domains — filtering narrows that down to the handful relevant to your current step.
- **Reuse data across steps**: instead of hardcoding an argument (an IP, a username, a port), link it to a scope
  Variable or to the output of a previous Action, so the value is resolved dynamically when the chain runs.
- **Avoid cross-branch mistakes**: when several branches of the graph can produce the same field name, choosing
  local scope guarantees an Action only reuses the value produced by its own branch, not one from an unrelated
  branch that happened to run more recently.

## How do I do it?

### Browse and filter the Threat Arsenal

1. Click **Add component**, then choose **Action** ("Execute an injector contract with configured parameters").
2. The action list opens on the full Threat Arsenal catalog. Narrow it down with the available filters:
      - **Injector**: the tool or capability that executes the action (for example NetExec, Nmap, a Payload).
      - **Platform**: the target operating system the action runs against (Windows, Linux, macOS...).
      - **Domain**: the technical domain the action belongs to (network, endpoint, cloud...).
      - **Tags**: free-form labels attached to the action.
      - **Providing** (compatible output): filters down to actions that produce a specific output field. This is the
        same filter automatically applied when you click **Add Compatible Action** from an Event's condition
        builder — it is surfaced there as a removable chip instead of a silent filter, so you can see which
        field it is currently scoped to and clear it to go back to the full catalog.
3. Click an action's row to select it and advance to configuring its arguments, or check several rows and use the
   bulk-add action to drop them all onto the canvas at once (each still needs configuring afterwards).

### Configure the Action

1. Set the Action's **title**.
2. Fill in its arguments. Arguments are pre-filled with the injector contract's defaults, which remain active
   unless you change them.
3. Optionally **link** an argument instead of leaving it as a static value: linking binds the argument to an
   output type, and at execution time the platform reads whatever value(s) that output type currently holds in the
   run's state pool (populated as Actions execute) — see [Local and Global Variables](#local-and-global-variables)
   below.
4. Configure its Expectations if the action supports them.
5. Save. The Action appears as a node on the canvas.

## Local and Global Variables

Beyond the [scope Variables](scope-definition.md#variables) you define upfront, any Action argument can be linked to
one or more **output types** (for example `Port`, `Username`, `CVE`) instead of a static value — not to another
Action directly. Linking an argument to a type means: "use whichever value(s) of this type are currently available
in the run's state," regardless of which Action(s) produced them. Local and global scope Variables are populated
from both sources — the scope Variables you defined and the outputs Actions produce as they execute. When an
argument has both a defined form value and linked type(s), the defined value is always included with the linked
values in the generated execution combinations.

When linking an argument, toggle **Limit to Local Scope** to choose where that data is read from:

- **Global scope** (toggle off, the default): the value is read from the run's shared pool of outputs, accumulated
  across the *entire* chained run so far — any Action anywhere upstream can feed it, regardless of which branch
  produced it.
- **Local scope** (toggle on): the value is read only from the *current branch* and comes from what downstream
  **Event** conditions kept on that branch. In simple terms: this Action uses the variable value filtered by the
  downstream events.

!!! note

    Only the output values that actually satisfied the upstream [Event](logic-creation.md#add-an-event)'s
    conditions are written to that branch's local pool — an Action's raw output is filtered down to just the
    value(s) that matched the Event's condition before it becomes available for local-scope linking. This means
    the Event's condition on that field needs an **Expected value** (`Equals`, `Not equals`, `Contains`, or `Not
    contains`) for local scope to have a concrete value to propagate; a value-less operator like `Is null` or `Is
    not null` only confirms the field's presence and does not, by itself, hand a specific value down to the local
    pool.

For example, imagine a Scenario whose scope defines a Variable `Port` = `80`. Downstream, an **Event** triggers
only when `Port equals 445` AND `Host is not null`, and it is connected to an **Action** whose arguments use both
the `Port` and `Host` fields:

- If the Action links its `Port` argument with the toggle **on** (local scope), it can only read `445` — the value
  that actually satisfied the Event's condition on that branch. The scope Variable (`80`) and any other port
  number produced by unrelated Actions elsewhere in the graph are not part of this branch's local pool.
- If the Action links its `Port` argument with the toggle **off** (global scope), it reads from the entire shared
  pool instead: alongside `445`, this also includes the scope Variable `80` and any other port number any other
  Action anywhere in the run has produced, generating extra execution combinations that a straight local-scope
  link would not.

## What's next?

- [Logic Creation](logic-creation.md): build Events and connect them to the Actions selected here.
- [Scope Definition](scope-definition.md): define the scope Variables an Action's arguments can link to.
- [Attack Path Map](attack-path-map.md): review the executed graph, including which linked values Actions actually used.
