# Spec: Chain Highlight View

## Context

In the chaining scenario logic graph, users build attack chains as a tree of **Actions** and **Events** connected via `DEPEND_ON` edges. When a chain grows complex (multiple branches, nested events), it becomes hard to understand the **impact** of a single action — i.e., which events and downstream actions would be triggered if that action executes successfully.

## Goal

Provide a visual **"impact highlight"** mode: when the user clicks on an action node, the graph highlights every event and action that could potentially be triggered downstream through that action's outputs.

## User Flow

1. User clicks on an **action node** in the ReactFlow graph
2. The clicked action gets a **highlighted border** (e.g., thick blue border)
3. All **downstream nodes** reachable from that action via `DEPEND_ON` edges are highlighted:
   - Direct child events (events that DEPEND_ON this action) → highlighted in **warning/amber**
   - Grandchild actions (actions that DEPEND_ON those events) → highlighted in **primary/blue**
   - And so on recursively down the chain
4. All **edges** on the highlighted path get a **thicker, colored stroke**
5. All **non-highlighted nodes** are visually **dimmed** (reduced opacity)
6. Clicking on the **canvas background** or pressing **Escape** exits highlight mode and restores normal view
7. Clicking on a **different action** switches the highlight to that action's chain

## Visual Design

### Highlighted state
```
┌─────────────────────────────────┐
│ [Clicked Action]                │  ← thick blue border (3px), full opacity
│ Nmap SYN Scan                   │
│ 🔵 Action  T1046                │
│ portscan  port                  │
└────────────┬────────────────────┘
             │  ← thick colored edge (3px, blue)
             ▼
┌─────────────────────────────────┐
│ [Child Event]                   │  ← amber glow border, full opacity
│ ⚡ Port found                   │
│ port EQ open                    │
└────────────┬────────────────────┘
             │  ← thick colored edge
             ▼
┌─────────────────────────────────┐
│ [Grandchild Action]             │  ← blue glow border, full opacity
│ NetExec SMB                     │
│ 🔵 Action                       │
│ credentials  share              │
└─────────────────────────────────┘
```

### Dimmed state (non-highlighted nodes)
- Node opacity: `0.3`
- Edge opacity: `0.15`
- Non-interactive (clicks still work to switch highlight target)

## Technical Implementation

### 1. State management

Add to `LogicFlow.tsx`:
```typescript
const [highlightedActionId, setHighlightedActionId] = useState<string | null>(null);
```

### 2. Downstream traversal (BFS)

Add to `logicUtils.ts`:
```typescript
/**
 * Get all downstream step IDs reachable from a given step via DEPEND_ON edges.
 * Returns a Set of step IDs (excluding the source step itself).
 */
export const getDownstreamStepIds = (
  steps: WorkflowStep[],
  sourceStepId: string,
): Set<string> => {
  const downstream = new Set<string>();
  const queue = [sourceStepId];

  while (queue.length > 0) {
    const currentId = queue.shift()!;
    // Find all steps that have a DEPEND_ON condition pointing to currentId
    for (const step of steps) {
      if (downstream.has(step.step_id)) continue;
      const dependsOnCurrent = step.step_conditions.some(
        c => c.condition_type === 'DEPEND_ON' && c.step_from_id === currentId,
      );
      if (dependsOnCurrent) {
        downstream.add(step.step_id);
        queue.push(step.step_id);
      }
    }
  }

  return downstream;
};
```

### 3. Node styling

In `buildNodesAndEdges`, pass `highlightState` to each node's data:
- `'source'` — the clicked action (thick blue border)
- `'highlighted'` — downstream node (glow effect)
- `'dimmed'` — not in the chain (low opacity)
- `null` — normal mode (no highlight active)

### 4. Edge styling

When highlight is active, update edge styles:
- Edges on the highlighted path: `strokeWidth: 3`, `stroke: theme.palette.primary.main`
- Other edges: `opacity: 0.15`

### 5. Click handlers

- **NodeAction**: `onClick` → calls `onHighlightAction(step.step_id)`
- **Canvas background**: `onPaneClick` → clears highlight (`setHighlightedActionId(null)`)
- **Keyboard**: `onKeyDown` Escape → clears highlight

### 6. Node component changes

In `NodeAction.tsx` and `NodeEvent.tsx`, read `highlightState` from data and apply styles:

```typescript
const getBorderStyle = (highlightState: string | null) => {
  switch (highlightState) {
    case 'source':
      return { border: '3px solid', borderColor: 'primary.main', boxShadow: 4 };
    case 'highlighted':
      return { border: '2px solid', borderColor: 'warning.main', boxShadow: 2 };
    case 'dimmed':
      return { opacity: 0.3 };
    default:
      return {};
  }
};
```

## Edge cases

- **Root action with no downstream**: highlight only the clicked action, dim everything else
- **Multiple root actions**: each can be clicked independently
- **Circular references**: the BFS uses a `Set` to prevent infinite loops (shouldn't happen with DEPEND_ON but defensive coding)
- **Single node graph**: clicking highlights it, nothing else to dim

## Future enhancements

- **Reverse highlight**: click an action to see what upstream actions/events lead TO it
- **Path count badge**: show how many downstream steps each action can trigger
- **Animated edges**: pulse animation along highlighted edges to show flow direction
- **Field-level highlight**: show which specific output fields connect to which event conditions
