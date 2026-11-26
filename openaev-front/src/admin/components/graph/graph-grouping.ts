import { type GraphEdgeOutput, type GraphNodeOutput, type GraphNodeType } from './graph-model';

/**
 * Grouping of same-type neighbours, as OpenCTI's graphCollapse: when a node has more than
 * GROUP_THRESHOLD neighbours of one type that hang on it alone (no other link on screen), they are
 * drawn as one group node ("+14 Injects") linked to it. A double click, or Ungroup in the context
 * menu, shows them one by one again.
 */
export const GROUP_THRESHOLD = 5;

const GROUP_PREFIX = 'group:';

export const groupId = (anchor: string, type: GraphNodeType) => `${GROUP_PREFIX}${anchor}|${type}`;

export const isGroupId = (id: string) => id.startsWith(GROUP_PREFIX);

/** A group node of the view: the members it stands for, by node id. */
export interface GroupedNode extends GraphNodeOutput { node_group_members?: string[] }

export interface Grouped {
  nodes: GroupedNode[];
  edges: GraphEdgeOutput[];
}

/**
 * Collapses the leaves of each node by type. `ungrouped` lists the group ids the user opened;
 * `keep` lists the node ids that are never folded into a group (the focus, the selection, the
 * pinned nodes, a path).
 */
export const groupNeighbours = (
  nodes: GraphNodeOutput[],
  edges: GraphEdgeOutput[],
  ungrouped: string[] = [],
  keep: Set<string> = new Set(),
): Grouped => {
  const degree = new Map<string, Set<string>>();
  nodes.forEach(n => degree.set(n.node_id, new Set()));
  edges.forEach((e) => {
    degree.get(e.edge_source)?.add(e.edge_target);
    degree.get(e.edge_target)?.add(e.edge_source);
  });
  const byId = new Map(nodes.map(n => [n.node_id, n]));
  // Leaves of each anchor, by type: a node with exactly one neighbour on screen.
  const leaves = new Map<string, GraphNodeOutput[]>();
  nodes.forEach((n) => {
    const neighbours = degree.get(n.node_id) as Set<string>;
    if (neighbours.size !== 1 || keep.has(n.node_id)) {
      return;
    }
    const anchor = [...neighbours][0];
    // Two leaves on each other are a pair, not a fan: never folded.
    if ((degree.get(anchor)?.size ?? 0) <= 1) {
      return;
    }
    const key = groupId(anchor, n.node_type);
    leaves.set(key, [...(leaves.get(key) ?? []), n]);
  });
  const folded = new Map<string, string>();
  const groups: GroupedNode[] = [];
  leaves.forEach((members, key) => {
    if (members.length <= GROUP_THRESHOLD || ungrouped.includes(key)) {
      return;
    }
    members.forEach(m => folded.set(m.node_id, key));
    const first = members[0];
    groups.push({
      node_id: key,
      node_type: first.node_type,
      node_entity_id: '',
      node_label: String(members.length),
      node_failed_count: members.reduce((sum, m) => sum + m.node_failed_count, 0),
      node_finding_count: members.reduce((sum, m) => sum + m.node_finding_count, 0),
      node_result: members.some(m => m.node_result === 'FAILED' || m.node_failed_count > 0) ? 'FAILED' : null,
      node_group_members: members.map(m => m.node_id),
    });
  });
  if (groups.length === 0) {
    return {
      nodes,
      edges,
    };
  }
  const seen = new Set<string>();
  const groupedEdges: GraphEdgeOutput[] = [];
  edges.forEach((e) => {
    const source = folded.get(e.edge_source) ?? e.edge_source;
    const target = folded.get(e.edge_target) ?? e.edge_target;
    if (source === e.edge_source && target === e.edge_target) {
      groupedEdges.push(e);
      return;
    }
    const id = `${source}|${e.edge_type}|${target}`;
    if (seen.has(id)) {
      return;
    }
    seen.add(id);
    const group = byId.get(e.edge_source) ? folded.get(e.edge_source) ?? folded.get(e.edge_target) : undefined;
    groupedEdges.push({
      ...e,
      edge_id: id,
      edge_source: source,
      edge_target: target,
      edge_result: group && groups.find(g => g.node_id === group)?.node_result === 'FAILED' ? 'FAILED' : e.edge_result,
    });
  });
  return {
    nodes: [...nodes.filter(n => !folded.has(n.node_id)), ...groups],
    edges: groupedEdges,
  };
};

const TYPE_RANK: Record<GraphNodeType, number> = {
  FINDING: 0,
  SIMULATION: 1,
  INJECT: 2,
  ASSET: 3,
  ASSET_GROUP: 4,
  SCENARIO: 5,
  PERSON: 6,
  TEAM: 7,
  ORGANIZATION: 8,
};

/**
 * The neighbours worth showing first around an entity, for the compact mini graph: failed
 * expectations, then findings, then by type (findings and simulations before the rest), then label.
 */
export const rankNeighbours = (nodes: GraphNodeOutput[]): GraphNodeOutput[] => [...nodes].sort((a, b) => (
  b.node_failed_count - a.node_failed_count
  || b.node_finding_count - a.node_finding_count
  || TYPE_RANK[a.node_type] - TYPE_RANK[b.node_type]
  || a.node_label.localeCompare(b.node_label)));

/** How many neighbours the mini graph draws one by one; the rest are grouped by type. */
export const MINI_SHOWN = 6;

/**
 * The view of the mini graph card: the focus, its MINI_SHOWN most relevant neighbours, the rest of
 * its first ring grouped by type ("+14 Injects"), and the second hop already known below the two
 * most relevant neighbours (at most two each).
 */
export const miniView = (focus: string, nodes: GraphNodeOutput[], edges: GraphEdgeOutput[]): Grouped => {
  const byId = new Map(nodes.map(n => [n.node_id, n]));
  const first = new Set<string>();
  edges.forEach((e) => {
    if (e.edge_source === focus) first.add(e.edge_target);
    if (e.edge_target === focus) first.add(e.edge_source);
  });
  const ranked = rankNeighbours([...first].map(id => byId.get(id)).filter((n): n is GraphNodeOutput => Boolean(n)));
  const shown = ranked.slice(0, MINI_SHOWN);
  const rest = ranked.slice(MINI_SHOWN);
  const kept = new Set<string>([focus, ...shown.map(n => n.node_id)]);
  const second: GraphNodeOutput[] = [];
  shown.slice(0, 2).forEach((hop) => {
    const next = edges
      .filter(e => e.edge_source === hop.node_id || e.edge_target === hop.node_id)
      .map(e => (e.edge_source === hop.node_id ? e.edge_target : e.edge_source))
      .filter(id => !kept.has(id) && !first.has(id) && id !== focus)
      .map(id => byId.get(id))
      .filter((n): n is GraphNodeOutput => Boolean(n));
    rankNeighbours(next).slice(0, 2).forEach((n) => {
      kept.add(n.node_id);
      second.push(n);
    });
  });
  const groups = new Map<GraphNodeType, GraphNodeOutput[]>();
  rest.forEach(n => groups.set(n.node_type, [...(groups.get(n.node_type) ?? []), n]));
  const groupNodes: GroupedNode[] = [...groups.entries()].map(([type, members]) => ({
    node_id: groupId(focus, type),
    node_type: type,
    node_entity_id: '',
    node_label: String(members.length),
    node_failed_count: members.reduce((sum, m) => sum + m.node_failed_count, 0),
    node_finding_count: members.reduce((sum, m) => sum + m.node_finding_count, 0),
    node_result: members.some(m => m.node_result === 'FAILED' || m.node_failed_count > 0) ? 'FAILED' : null,
    node_group_members: members.map(m => m.node_id),
  }));
  const keptEdges = edges.filter(e => kept.has(e.edge_source) && kept.has(e.edge_target)
    && (e.edge_source === focus || e.edge_target === focus || second.some(n => n.node_id === e.edge_source || n.node_id === e.edge_target)));
  const groupEdges: GraphEdgeOutput[] = groupNodes.map(g => ({
    edge_id: `${focus}|${g.node_id}`,
    edge_type: edges.find(e => (e.edge_source === focus && g.node_group_members?.includes(e.edge_target))
      || (e.edge_target === focus && g.node_group_members?.includes(e.edge_source)))?.edge_type ?? 'LINKED_TO',
    edge_source: focus,
    edge_target: g.node_id,
    edge_result: g.node_result,
  }));
  return {
    nodes: [...[...kept].map(id => byId.get(id)).filter((n): n is GraphNodeOutput => Boolean(n)), ...groupNodes],
    edges: [...keptEdges, ...groupEdges],
  };
};
