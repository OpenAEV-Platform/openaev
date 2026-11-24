---
preview_feature: EXPLORATION_GRAPH
---

# Exploration graph

The exploration graph shows how your Assets, Asset groups, Findings, Injects, Simulations, Scenarios and People
connect, so you can follow an exposure from the Simulation that revealed it to the Assets, identities and Findings
behind it.

!!! note "Preview feature"

    The exploration graph is a preview feature, off by default. While it is off, the **Graph** menu entry, the
    **Graph** tabs, the **Explore in graph** buttons and the Asset links described on this page are hidden.

## Why use it?

- **See the chain, not a list**: an Asset with failed expectations, the Injects that failed on it, its Asset groups,
  its Findings and the Person it belongs to, on one screen.
- **Start from what matters**: the landing view opens on the most critical exposure of your estate, not on an empty
  canvas.
- **Pivot freely**: expand any node by relation or by entity type, find the shortest path between two nodes, and
  share the exact view with a link.

## Open the graph

Open **Graph** in the left menu. You need the right to access Assets to see it.

The landing page offers two views:

| View | What it shows |
|:-----|:--------------|
| **Top exposure story** | The most critical chain of the estate: the attack path with the most failed steps, else the Asset with the most failed expectations, with its Findings, Asset groups and identity |
| **Estate overview** | Asset groups sized by their Asset count and coloured by failure rate, with the Simulations that targeted them. Double-click a group to unfold its Assets |

The **Starting points** list next to the canvas lets you start from an exposed Asset, a failing Asset group, the latest
Simulation, a Finding, an attack path or an exposed Person.

You can also open the graph focused on one object: the **Explore in graph** button sits in the header of an Asset, an
Asset group, a Finding, a Person, a Team, an Organization, a Scenario, a Simulation and an Attack path.

## Explore

- **Expand a node**: double-click it to load its direct neighbours, or use its menu to **Expand by relation** or
  **Expand by entity type**. When a node has more neighbours than shown, **Load more** brings the next ones.
- **Find a path**: open a node's menu, choose **Find a path from here**, then select the destination node. The graph
  highlights the shortest path, at most six hops.
- **Focus mode** dims every node outside the selection and its neighbours; **Highlight failed paths** keeps the eye on
  the failed expectations.
- **Select several nodes** with the lasso, then select their neighbours, hide them or remove them from the view.
- **Layouts**: force, hierarchical (Scenarios on top, Findings at the bottom) and radial (rings around the selected
  node).
- **Pin** a node to keep it in place while the layout runs again.
- **Copy a link to this view** to share the exact focus and layout with a colleague.

Each node and edge is coloured by the worst expectation result behind it: green for success, orange for pending, red
for failed.

## Relations

| Relation | Meaning |
|:---------|:--------|
| **Member of** | The Asset belongs to the Asset group, statically or through the group's filter |
| **Found on** | The Finding was observed on the Asset |
| **Produced** | The Inject produced the Finding |
| **Targets** | The Inject targeted the Asset or Asset group, with its expectation result |
| **Part of** | The Inject belongs to the Simulation |
| **Run of** | The Simulation is a run of the Scenario |
| **Identity of** | The Asset is the identity of the Person |
| **Works for**, **In team**, **Team of** | The Person belongs to the Organization or the Team, the Team to the Organization |
| **Linked to** | An Asset link, see [Assets](../build/assets.md#asset-links) |
| **Attack step** | A step of an attack path between two Assets |

## Access

The graph never shows more than you can read elsewhere. Simulations, Scenarios and Injects appear only when you can
read them, Findings only when you can read Findings, and People, Organizations and Teams only with the right to read
them. Everything else is left out of the graph with its edges.

## OpenCTI and OpenCRQ

When your Tenant is connected to OpenCTI or OpenCRQ (see
[Security Coverage enrichment](../../integrations/xtm-suite/xtm-suite-connector.md#opencti-and-opencrq-pivots)), a
node also known in the connected product carries its mark, and its menu offers **Open in OpenCTI** or
**Open in OpenCRQ**.

## What's next?

- [Assets](../build/assets.md) -- Link Assets to each other
- [Findings](findings/findings.md) -- Understand the Findings the graph draws
- [Attack Path Map](../attack-chaining/attack-path-map.md) -- Read the attack path of one chained Simulation
- [REST API](../../reference/apis/rest-api.md#exploration-graph) -- Read graph slices programmatically
