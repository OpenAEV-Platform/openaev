import { type Edge, type Node } from '@xyflow/react';

import type { AttackPathDTO, AttackPathEdges, AttackPathNodeDTO } from '../../../../../utils/api-types';

// Attack-path execution-store POC (issue 6647). Pure mapping of the backend AttackPathDTO onto
// React Flow nodes and edges, with a manual column layout (no layout lib, mirroring AttackPath.tsx).
// Executions are carried on the edges (design O2), never as flow nodes, so the graph stays a handful
// of node kinds regardless of how many executions a simulation ran.

export const AP_FLOW_NODE_TYPE = {
  injector: 'apInjector',
  asset: 'apAsset',
  findingType: 'apFindingType',
  finding: 'apFinding',
  endpointCluster: 'apEndpointCluster',
  findingCluster: 'apFindingCluster',
} as const;

export const AP_FLOW_EDGE_TYPE = 'apGrouped';

// DTO node type -> React Flow node-type key. EXECUTION is intentionally absent: the feed lists
// executions, they are not nodes on the map.
const DTO_TYPE_TO_FLOW: Record<string, string> = {
  INJECTOR: AP_FLOW_NODE_TYPE.injector,
  ASSET: AP_FLOW_NODE_TYPE.asset,
  FINDING_TYPE: AP_FLOW_NODE_TYPE.findingType,
  FINDING: AP_FLOW_NODE_TYPE.finding,
};

const PADDING = 40;
// Each node kind is a left-to-right band; within a band nodes wrap into a grid (MAX_ROWS tall) so a
// large simulation's hundreds of endpoints read as a compact block, not a single 40,000px column.
// Spacing is sized for the circular nodes (endpoint ~96px, finding ~64px + a label under it).
const COL_W = 200;
const ROW_H = 132;
const MAX_ROWS = 20;
const BAND_GAP = 200;

// Left-to-right order of the type bands.
const BAND_ORDER = [
  AP_FLOW_NODE_TYPE.injector,
  AP_FLOW_NODE_TYPE.asset,
  AP_FLOW_NODE_TYPE.findingType,
  AP_FLOW_NODE_TYPE.finding,
];

export interface AttackPathFlowNodeData {
  label?: string;
  status?: string;
  ref?: string;
  typeFindings?: string;
  findingCounts?: Record<string, number>;
  hostname?: string;
  ip?: string;
  platform?: string;
  agents?: string[];
  dimmed?: boolean;
  // Aggregate cluster nodes: the endpoint count (endpoint cluster) or finding count (finding cluster).
  count?: number;
  // For an endpoint cluster: the injector it aggregates (used to expand it into real endpoints).
  injectorId?: string;
  // For a finding cluster: its stable key (per injector when collapsed, per endpoint when expanded)
  // and, when it hangs off one endpoint, that endpoint's ref so a click fetches only its findings.
  clusterId?: string;
  endpointRef?: string;
  // Endpoint cluster role: 'header' (the "+N" toggle) or 'overflow' (the "+rest" batch loader).
  clusterKind?: 'header' | 'overflow';
  // Header endpoint cluster: whether it is currently expanded (drives the collapse affordance).
  expanded?: boolean;
  [key: string]: unknown;
}

export interface AttackPathFlowEdgeData {
  count: number;
  edgeType?: string;
  // GREEN/ORANGE/RED (from the target endpoint for execution edges; RED for finding edges) so the
  // edge is coloured like the mockup; label shows the finding type on finding edges.
  status?: string;
  label?: string;
  dimmed?: boolean;
  [key: string]: unknown;
}

export type AttackPathFlowNode = Node<AttackPathFlowNodeData>;
export type AttackPathFlowEdge = Edge<AttackPathFlowEdgeData>;

const nodeData = (n: AttackPathNodeDTO): AttackPathFlowNodeData => ({
  label: n.label,
  status: n.status,
  ref: n.ref,
  typeFindings: n.typeFindings,
  findingCounts: n.findingCounts,
  hostname: n.hostname,
  ip: n.ip,
  platform: n.platform,
  agents: n.agents,
});

const EDGE_EXECUTIONS = 'EDGE_EXECUTIONS';
const EDGE_ENDPOINT_FINDINGS_TYPE = 'EDGE_ENDPOINT_FINDINGS_TYPE';

// Enrich an edge with a status colour and an optional label: execution edges (injector -> endpoint)
// take the target endpoint's status (a heatmap of the endpoint set); finding edges are red. Only the
// endpoint -> finding-type edge is labelled with the type (the finding-type node itself is an icon),
// so the type is named exactly once rather than repeated on the edge and the node.
const toEdge = (
  e: AttackPathEdges,
  nodeById: Map<string, AttackPathFlowNode>,
): AttackPathFlowEdge => {
  const target = nodeById.get(e.edgeTargetId ?? '');
  const isExecution = e.type === EDGE_EXECUTIONS;
  return {
    id: e.edgeId ?? `${e.edgeSourceId}-${e.edgeTargetId}`,
    source: e.edgeSourceId ?? '',
    target: e.edgeTargetId ?? '',
    type: AP_FLOW_EDGE_TYPE,
    data: {
      count: e.count ?? 1,
      edgeType: e.type,
      status: isExecution ? target?.data.status : 'RED',
      label: e.type === EDGE_ENDPOINT_FINDINGS_TYPE ? target?.data.typeFindings : undefined,
    },
  };
};

/**
 * Map an AttackPathDTO onto React Flow nodes and edges. Nodes are laid out in grid bands by kind;
 * edges keep only those whose endpoints are both present (so an edge into a not-yet-expanded
 * finding level is dropped rather than dangling).
 */
export const buildAttackPathFlow = (
  dto: AttackPathDTO,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const presentIds = new Set<string>();
  const byType: Record<string, AttackPathNodeDTO[]> = {};

  for (const n of dto.attackPathNodes ?? []) {
    const flowType = DTO_TYPE_TO_FLOW[n.type ?? ''];
    if (!flowType || !n.id) {
      continue;
    }
    (byType[flowType] ??= []).push(n);
    presentIds.add(n.id);
  }

  // Lay each kind out as its own band, left to right; within a band, wrap into a grid so a large
  // endpoint set is a compact block rather than one very tall column.
  const nodes: AttackPathFlowNode[] = [];
  let bandStartX = PADDING;
  for (const flowType of BAND_ORDER) {
    const list = byType[flowType] ?? [];
    if (list.length === 0) {
      continue;
    }
    const subColumns = Math.ceil(list.length / MAX_ROWS);
    list.forEach((n, i) => {
      nodes.push({
        id: n.id as string,
        type: flowType,
        position: {
          x: bandStartX + Math.floor(i / MAX_ROWS) * COL_W,
          y: PADDING + (i % MAX_ROWS) * ROW_H,
        },
        data: nodeData(n),
      });
    });
    bandStartX += subColumns * COL_W + BAND_GAP;
  }

  const nodeById = new Map(nodes.map(n => [n.id, n]));
  const edges = (dto.attackPathEdges ?? [])
    .filter(e => presentIds.has(e.edgeSourceId ?? '') && presentIds.has(e.edgeTargetId ?? ''))
    .map(e => toEdge(e, nodeById));

  return {
    nodes,
    edges,
  };
};

// Layout for the clustered (aggregate) default view.
const CLUSTER_EP_X = PADDING + 240;
const CLUSTER_EP_DETAIL_X = CLUSTER_EP_X + 170;
const CLUSTER_FINDING_X = CLUSTER_EP_DETAIL_X + 210;
const CLUSTER_FINDING_DETAIL_X = CLUSTER_FINDING_X + 210;
const CLUSTER_ROW_UNIT = 120;
const CLUSTER_EP_ROW_H = 124;
const CLUSTER_FINDING_ROW_H = 100;
const CLUSTER_FINDING_GAP = 52;
const CLUSTER_FINDING_DETAIL_ROW = 70;
const CLUSTER_BLOCK_GAP = 56;
const CLUSTER_INJECTOR_HALF_H = 34;
const CLUSTER_EP_HALF_H = 42;

// Progressive drill-down: an expanded endpoint cluster reveals endpoints in batches of this size,
// keeping a "+N" overflow cluster for the rest (itself expandable), so a huge injector stays legible.
export const ENDPOINT_BATCH_SIZE = 10;

// Same batching for revealing individual findings under a finding cluster.
export const FINDING_BATCH_SIZE = 10;

// Aggregate a set of prevention/detection statuses into one: all-same keeps that status, a mix (e.g.
// some prevented and some undetected) is ORANGE — the "partially handled" middle ground.
const aggregateStatus = (statuses: Array<string | undefined>): string | undefined => {
  const set = new Set(statuses.filter((s): s is string => s === 'GREEN' || s === 'ORANGE' || s === 'RED'));
  if (set.size === 0) {
    return undefined;
  }
  if (set.size === 1) {
    return [...set][0];
  }
  return 'ORANGE';
};

/**
 * Aggregate ("clustered") layout for the default view: each injector reaches one endpoint cluster
 * (a "+N" dot) which fans out to one cluster per finding type (icon + aggregated count). Everything
 * is derived from the collapsed graph (injector -> endpoint execution edges + per-endpoint
 * findingCounts) — no extra reads. Clicking an endpoint cluster expands it progressively: it keeps
 * the "+N" header and reveals a batch of real endpoints plus a "+rest" overflow cluster (itself
 * expandable). {@code endpointBatchByInjector} maps an injector id to how many of its endpoints are
 * currently revealed (0 / absent = collapsed).
 */
export interface FindingExpansion {
  // finding-cluster node ids currently expanded into individual findings
  expanded: Set<string>;
  // finding-cluster node id -> its deduplicated findings (fetched from the injector's endpoints)
  findingsByCluster: Map<string, AttackPathNodeDTO[]>;
  // finding-cluster node id -> how many of its findings are revealed (batched)
  batch: Map<string, number>;
}

// One finding-type cluster's pre-computed vertical layout within a finding column.
interface FindingColItem {
  type: string;
  count: number;
  fcId: string;
  typeStatus?: string;
  isExpanded: boolean;
  findings: AttackPathNodeDTO[];
  overflow: number;
  clusterY: number;
  findingYs: number[];
  overflowY: number;
}

// Pre-lay-out a finding column (one cluster per type, each optionally expanded into its findings)
// with a running cursor, so expanding one type pushes the next down instead of overlapping it.
// Shared by the per-injector aggregate column (collapsed) and the per-endpoint column (expanded).
const layoutFindingColumn = (
  types: Array<[string, number]>,
  statusForType: (type: string) => string | undefined,
  keyOf: (type: string) => string,
  findingExpansion?: FindingExpansion,
): {
  items: FindingColItem[];
  height: number;
} => {
  let fH = 0;
  const items = types.map(([type, count]) => {
    const fcId = keyOf(type);
    const typeStatus = statusForType(type);
    const isExpanded = findingExpansion?.expanded.has(fcId) ?? false;
    const list = isExpanded ? (findingExpansion?.findingsByCluster.get(fcId) ?? []) : [];
    const fShown = isExpanded ? Math.min(Math.max(findingExpansion?.batch.get(fcId) ?? 0, 0), list.length) : 0;
    const findings = list.slice(0, fShown);
    const overflow = list.length > fShown ? list.length - fShown : 0;
    const clusterY = fH;
    fH += CLUSTER_FINDING_ROW_H;
    const findingYs = findings.map((_, j) => clusterY + CLUSTER_FINDING_ROW_H + j * CLUSTER_FINDING_DETAIL_ROW);
    const overflowY = clusterY + CLUSTER_FINDING_ROW_H + findings.length * CLUSTER_FINDING_DETAIL_ROW;
    if (findings.length > 0 || overflow > 0) {
      fH = overflowY + (overflow > 0 ? CLUSTER_FINDING_DETAIL_ROW : 0);
    }
    fH += CLUSTER_FINDING_GAP;
    return {
      type,
      count,
      fcId,
      typeStatus,
      isExpanded,
      findings,
      overflow,
      clusterY,
      findingYs,
      overflowY,
    };
  });
  return {
    items,
    height: Math.max(0, fH - CLUSTER_FINDING_GAP),
  };
};

// Emit a finding column's nodes/edges: one finding-cluster node per type (its edge labelled
// "<type> found"), plus revealed individual findings and an overflow loader. {@code srcId} is the
// node the column hangs off (the endpoint cluster header in the collapsed view, or the endpoint node
// when endpoints are expanded). {@code endpointRef} is set for a per-endpoint column so a click
// fetches that one endpoint's findings; absent for the injector aggregate.
const pushFindingColumn = (
  nodes: AttackPathFlowNode[],
  edges: AttackPathFlowEdge[],
  items: FindingColItem[],
  srcId: string,
  topY: number,
  injId: string,
  endpointRef: string | undefined,
): void => {
  items.forEach((e) => {
    nodes.push({
      id: e.fcId,
      type: AP_FLOW_NODE_TYPE.findingCluster,
      position: {
        x: CLUSTER_FINDING_X,
        y: topY + e.clusterY,
      },
      data: {
        typeFindings: e.type,
        count: e.count,
        label: e.type,
        injectorId: injId,
        clusterId: e.fcId,
        endpointRef,
        clusterKind: 'header',
        expanded: e.isExpanded,
      },
    });
    edges.push({
      id: `${srcId}-${e.fcId}`,
      source: srcId,
      target: e.fcId,
      type: AP_FLOW_EDGE_TYPE,
      data: {
        count: e.count,
        status: e.typeStatus,
        label: `${e.type} found`,
      },
    });
    e.findings.forEach((f, j) => {
      const fid = f.id ?? `${e.fcId}-f${j}`;
      nodes.push({
        id: fid,
        type: AP_FLOW_NODE_TYPE.finding,
        position: {
          x: CLUSTER_FINDING_DETAIL_X,
          y: topY + e.findingYs[j],
        },
        data: {
          label: f.value ?? f.label,
          typeFindings: e.type,
        },
      });
      edges.push({
        id: `${e.fcId}-${fid}`,
        source: e.fcId,
        target: fid,
        type: AP_FLOW_EDGE_TYPE,
        data: {
          count: 1,
          status: e.typeStatus,
        },
      });
    });
    if (e.overflow > 0) {
      const moreId = `${e.fcId}-more`;
      nodes.push({
        id: moreId,
        type: AP_FLOW_NODE_TYPE.findingCluster,
        position: {
          x: CLUSTER_FINDING_DETAIL_X,
          y: topY + e.overflowY,
        },
        data: {
          typeFindings: e.type,
          count: e.overflow,
          label: e.type,
          injectorId: injId,
          clusterId: e.fcId,
          endpointRef,
          clusterKind: 'overflow',
        },
      });
      edges.push({
        id: `${e.fcId}-${moreId}`,
        source: e.fcId,
        target: moreId,
        type: AP_FLOW_EDGE_TYPE,
        data: {
          count: e.overflow,
          label: `+${e.overflow}`,
          status: e.typeStatus,
        },
      });
    }
  });
};

export const buildClusteredAttackPathFlow = (
  dto: AttackPathDTO,
  endpointBatchByInjector: Map<string, number>,
  findingExpansion?: FindingExpansion,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const dtoNodes = dto.attackPathNodes ?? [];
  const injectors = dtoNodes.filter(n => n.type === 'INJECTOR' && n.id);
  const assetById = new Map(
    dtoNodes.filter(n => n.type === 'ASSET' && n.id).map(n => [n.id as string, n]),
  );
  const execEdges = (dto.attackPathEdges ?? []).filter(e => e.type === 'EDGE_EXECUTIONS');

  // injector -> distinct reached endpoints.
  const reachedByInjector = new Map<string, string[]>();
  for (const e of execEdges) {
    if (e.edgeSourceId && e.edgeTargetId && assetById.has(e.edgeTargetId)) {
      const arr = reachedByInjector.get(e.edgeSourceId) ?? [];
      if (!arr.includes(e.edgeTargetId)) {
        arr.push(e.edgeTargetId);
      }
      reachedByInjector.set(e.edgeSourceId, arr);
    }
  }

  const nodes: AttackPathFlowNode[] = [];
  const edges: AttackPathFlowEdge[] = [];

  let cursorY = PADDING;
  injectors.forEach((inj) => {
    const injId = inj.id as string;
    const reached = reachedByInjector.get(injId) ?? [];
    const total = reached.length;
    const shown = Math.min(Math.max(endpointBatchByInjector.get(injId) ?? 0, 0), total);
    const expanded = shown > 0;

    // Aggregate finding-type counts across this injector's endpoints.
    const typeSum = new Map<string, number>();
    for (const assetId of reached) {
      const fc = assetById.get(assetId)?.findingCounts;
      if (fc) {
        for (const [k, v] of Object.entries(fc)) {
          typeSum.set(k, (typeSum.get(k) ?? 0) + (v ?? 0));
        }
      }
    }
    const types = [...typeSum.entries()].filter(([, v]) => v > 0);
    const reachedAssets = reached.map(id => assetById.get(id));
    const injectorStatus = aggregateStatus(reachedAssets.map(a => a?.status));

    // Collapsed view: one aggregate finding column per injector (hidden while endpoints are expanded).
    const agg = layoutFindingColumn(
      types,
      type => aggregateStatus(reachedAssets.filter(a => (a?.findingCounts?.[type] ?? 0) > 0).map(a => a?.status)),
      type => `cl-ft-${type}-${injId}`,
      findingExpansion,
    );
    const findingColHeight = expanded ? 0 : agg.height;

    // Expanded view: each revealed endpoint fans out to its OWN finding column, so its block is as
    // tall as its findings (or a single row, whichever is taller); a "+rest" overflow follows below.
    const shownAssetIds = reached.slice(0, shown);
    const endpointLayouts = expanded
      ? shownAssetIds.map((assetId) => {
          const asset = assetById.get(assetId);
          const epTypes = (Object.entries(asset?.findingCounts ?? {}).filter(([, v]) => (v ?? 0) > 0)) as Array<[string, number]>;
          const epCol = layoutFindingColumn(epTypes, () => asset?.status, type => `cl-ft-${type}-${assetId}`, findingExpansion);
          return {
            assetId,
            asset,
            epCol,
            blockH: Math.max(CLUSTER_EP_ROW_H, epCol.height),
          };
        })
      : [];
    const endpointColHeight = expanded
      ? endpointLayouts.reduce((s, e) => s + e.blockH, 0) + (total > shown ? CLUSTER_EP_ROW_H : 0)
      : 0;
    const blockH = Math.max(CLUSTER_ROW_UNIT, findingColHeight, endpointColHeight);
    const centerY = cursorY + blockH / 2;

    nodes.push({
      id: injId,
      type: AP_FLOW_NODE_TYPE.injector,
      position: {
        x: PADDING,
        y: centerY - CLUSTER_INJECTOR_HALF_H,
      },
      data: nodeData(inj),
    });

    const clusterId = `cl-ep-${injId}`;
    nodes.push({
      id: clusterId,
      type: AP_FLOW_NODE_TYPE.endpointCluster,
      position: {
        x: CLUSTER_EP_X,
        y: centerY - CLUSTER_EP_HALF_H,
      },
      data: {
        count: total,
        injectorId: injId,
        clusterKind: 'header',
        expanded,
        status: injectorStatus,
      },
    });
    edges.push({
      id: `${injId}-${clusterId}`,
      source: injId,
      target: clusterId,
      type: AP_FLOW_EDGE_TYPE,
      data: {
        count: total,
        label: `+${total}`,
        status: injectorStatus,
      },
    });

    // Revealed endpoints, each with its own finding column; a "+rest" overflow follows. When
    // collapsed, the injector's aggregate finding column is drawn instead.
    if (expanded) {
      let epY = cursorY + (blockH - endpointColHeight) / 2;
      endpointLayouts.forEach(({ assetId, asset, epCol, blockH: epBlockH }) => {
        if (!asset) {
          epY += epBlockH;
          return;
        }
        const epCenter = epY + epBlockH / 2;
        nodes.push({
          id: assetId,
          type: AP_FLOW_NODE_TYPE.asset,
          position: {
            x: CLUSTER_EP_DETAIL_X,
            y: epCenter - CLUSTER_EP_HALF_H,
          },
          data: nodeData(asset),
        });
        edges.push({
          id: `${clusterId}-${assetId}`,
          source: clusterId,
          target: assetId,
          type: AP_FLOW_EDGE_TYPE,
          data: {
            count: 1,
            status: asset.status,
          },
        });
        // The endpoint's own finding column, vertically centred against its block.
        pushFindingColumn(nodes, edges, epCol.items, assetId, epY + (epBlockH - epCol.height) / 2, injId, asset.ref ?? assetId);
        epY += epBlockH;
      });
      if (total > shown) {
        const overflowId = `cl-ep-more-${injId}`;
        nodes.push({
          id: overflowId,
          type: AP_FLOW_NODE_TYPE.endpointCluster,
          position: {
            x: CLUSTER_EP_DETAIL_X,
            y: epY + CLUSTER_EP_ROW_H / 2 - CLUSTER_EP_HALF_H,
          },
          data: {
            count: total - shown,
            injectorId: injId,
            clusterKind: 'overflow',
            status: injectorStatus,
          },
        });
        edges.push({
          id: `${clusterId}-${overflowId}`,
          source: clusterId,
          target: overflowId,
          type: AP_FLOW_EDGE_TYPE,
          data: {
            count: total - shown,
            label: `+${total - shown}`,
            status: injectorStatus,
          },
        });
      }
    } else {
      pushFindingColumn(nodes, edges, agg.items, clusterId, cursorY + (blockH - agg.height) / 2, injId, undefined);
    }

    cursorY += blockH + CLUSTER_BLOCK_GAP;
  });

  return {
    nodes,
    edges,
  };
};

// A single credential/finding selected in the drawer, for the focused "attack path to this finding"
// view (issue 6647): only the injector(s) that reached its endpoint, the endpoint, and the finding.
export interface PathFinding {
  endpointNodeId: string;
  endpointKey: string;
  type: string;
  // Value as returned by the backend (credential secrets already masked server-side).
  value: string;
  // Human-readable injector contract(s) used on injector -> endpoint edges in the focused path view.
  contractLabel?: string;
}

/**
 * Focused view for a finding picked in the drawer: rebuild the graph to show ONLY the attack path
 * that produced it — the injector(s) that reached its endpoint, the endpoint, and the finding — all
 * highlighted. Everything else is dropped so the whole path fits in one overview.
 */
export const buildFindingPathFlow = (
  dto: AttackPathDTO,
  finding: PathFinding,
  contractLabelByInjector?: Record<string, string>,
  findingExpansion?: FindingExpansion,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const nodes: AttackPathFlowNode[] = [];
  const edges: AttackPathFlowEdge[] = [];
  const dtoNodes = dto.attackPathNodes ?? [];
  const endpointId = finding.endpointNodeId;
  const endpoint = dtoNodes.find(n => n.id === endpointId);

  // The injector(s) that reached this endpoint (execution edges into it).
  const injectorIds = new Set<string>();
  for (const e of dto.attackPathEdges ?? []) {
    if (e.type === 'EDGE_EXECUTIONS' && e.edgeTargetId === endpointId && e.edgeSourceId) {
      injectorIds.add(e.edgeSourceId);
    }
  }
  const injectors = dtoNodes.filter(n => n.type === 'INJECTOR' && n.id && injectorIds.has(n.id as string));

  const endpointCounts = endpoint?.findingCounts ?? {};
  const selectedTypeCount = endpointCounts[finding.type] ?? 0;
  const sameTypeOthers = Math.max(selectedTypeCount - 1, 0);
  const otherTypesTotal = Object.entries(endpointCounts)
    .filter(([k]) => k !== finding.type)
    .reduce((sum, [, v]) => sum + (v ?? 0), 0);

  const rowH = CLUSTER_FINDING_ROW_H;
  const rightRows = 1 + (sameTypeOthers > 0 ? 1 : 0) + (otherTypesTotal > 0 ? 1 : 0);
  const rightH = Math.max(rowH, rightRows * rowH);
  const leftH = Math.max(CLUSTER_EP_ROW_H, injectors.length * CLUSTER_EP_ROW_H);
  const blockH = Math.max(leftH, rightH);
  const centerY = PADDING + blockH / 2;
  const rightTopY = centerY - rightH / 2;

  injectors.forEach((inj, i) => {
    const y = PADDING + i * CLUSTER_EP_ROW_H;
    nodes.push({
      id: inj.id as string,
      type: AP_FLOW_NODE_TYPE.injector,
      position: {
        x: PADDING,
        y: y + CLUSTER_EP_ROW_H / 2 - CLUSTER_INJECTOR_HALF_H,
      },
      data: nodeData(inj),
      selected: true,
    });
    edges.push({
      id: `${inj.id}-${endpointId}`,
      source: inj.id as string,
      target: endpointId,
      type: AP_FLOW_EDGE_TYPE,
      data: {
        count: 1,
        // Focused path is intentionally highlighted in blue.
        status: undefined,
        label: contractLabelByInjector?.[inj.id as string] || finding.contractLabel || inj.label,
      },
      selected: true,
    });
  });

  nodes.push({
    id: endpointId,
    type: AP_FLOW_NODE_TYPE.asset,
    position: {
      x: CLUSTER_EP_DETAIL_X,
      y: centerY - CLUSTER_EP_HALF_H,
    },
    data: endpoint
      ? nodeData(endpoint)
      : {
          label: finding.endpointKey,
          ref: finding.endpointKey,
        },
    selected: true,
  });

  const findingId = `path-finding|${finding.type}|${finding.value}`;
  nodes.push({
    id: findingId,
    type: AP_FLOW_NODE_TYPE.finding,
    position: {
      x: CLUSTER_FINDING_DETAIL_X,
      y: rightTopY,
    },
    data: {
      label: finding.value,
      typeFindings: finding.type,
    },
    selected: true,
  });
  edges.push({
    id: `${endpointId}-${findingId}`,
    source: endpointId,
    target: findingId,
    type: AP_FLOW_EDGE_TYPE,
    data: {
      count: 1,
      // Focused path is intentionally highlighted in blue.
      status: undefined,
      label: `${finding.type} found`,
    },
    selected: true,
  });

  // Contextual clusters (the endpoint's other findings) sit to the right of the focused finding. Each
  // is expandable in place: on expand it fans out its (fetched, batched) individual findings so the
  // user can keep exploring the focused path without leaving it. Returns the vertical space consumed.
  const CTX_CLUSTER_X = CLUSTER_FINDING_DETAIL_X + 170;
  const CTX_CHILD_X = CTX_CLUSTER_X + 210;
  const CTX_ROW_H = CLUSTER_FINDING_DETAIL_ROW;
  const emitCtxCluster = (
    id: string,
    typeFindings: string,
    count: number,
    edgeLabel: string,
    top: number,
  ): number => {
    const isExpanded = findingExpansion?.expanded.has(id) ?? false;
    const list = isExpanded ? (findingExpansion?.findingsByCluster.get(id) ?? []) : [];
    const shown = isExpanded
      ? Math.min(Math.max(findingExpansion?.batch.get(id) ?? 0, 0), list.length)
      : 0;
    const children = list.slice(0, shown);
    const overflow = list.length - children.length;
    nodes.push({
      id,
      type: AP_FLOW_NODE_TYPE.findingCluster,
      position: {
        x: CTX_CLUSTER_X,
        y: top,
      },
      data: {
        typeFindings,
        count,
        label: typeFindings,
        clusterId: id,
        endpointRef: finding.endpointKey,
        clusterKind: 'header',
        expanded: isExpanded,
      },
      selected: true,
    });
    edges.push({
      id: `${endpointId}-${id}`,
      source: endpointId,
      target: id,
      type: AP_FLOW_EDGE_TYPE,
      data: {
        count,
        status: undefined,
        label: edgeLabel,
      },
      selected: true,
    });
    children.forEach((f, j) => {
      const fid = f.id ?? `${id}-f${j}`;
      nodes.push({
        id: fid,
        type: AP_FLOW_NODE_TYPE.finding,
        position: {
          x: CTX_CHILD_X,
          y: top + CLUSTER_FINDING_ROW_H + j * CTX_ROW_H,
        },
        data: {
          label: f.value ?? f.label,
          typeFindings: f.typeFindings,
        },
      });
      edges.push({
        id: `${id}-${fid}`,
        source: id,
        target: fid,
        type: AP_FLOW_EDGE_TYPE,
        data: {
          count: 1,
          status: undefined,
        },
      });
    });
    if (overflow > 0) {
      const moreId = `${id}-more`;
      nodes.push({
        id: moreId,
        type: AP_FLOW_NODE_TYPE.findingCluster,
        position: {
          x: CTX_CHILD_X,
          y: top + CLUSTER_FINDING_ROW_H + children.length * CTX_ROW_H,
        },
        data: {
          typeFindings,
          count: overflow,
          label: typeFindings,
          clusterId: id,
          endpointRef: finding.endpointKey,
          clusterKind: 'overflow',
        },
      });
      edges.push({
        id: `${id}-${moreId}`,
        source: id,
        target: moreId,
        type: AP_FLOW_EDGE_TYPE,
        data: {
          count: overflow,
          label: `+${overflow}`,
          status: undefined,
        },
      });
    }
    const childrenH = (children.length + (overflow > 0 ? 1 : 0)) * CTX_ROW_H;
    return Math.max(rowH, CLUSTER_FINDING_ROW_H + childrenH);
  };

  let clusterY = rightTopY + rowH;
  if (sameTypeOthers > 0) {
    const id = `path-cl-same|${finding.type}|${finding.endpointKey}`;
    clusterY += emitCtxCluster(id, finding.type, sameTypeOthers, `+${sameTypeOthers}`, clusterY);
  }
  if (otherTypesTotal > 0) {
    const id = `path-cl-other|${finding.type}|${finding.endpointKey}`;
    emitCtxCluster(id, 'other', otherTypesTotal, `${otherTypesTotal}+`, clusterY);
  }

  return {
    nodes,
    edges,
  };
};

export type AttackPathFindingFilter = 'endpoints' | 'files' | 'credentials' | 'users' | 'cves';

// Finding types whose value is a captured secret; masked by default in the UI (spec §14). Revealing
// them is an explicit, permission-gated action handled by the Result/Terminal increment.
export const SENSITIVE_FINDING_TYPES = new Set(['credentials', 'password_policy', 'sid']);

// Mask a finding value for display (rendered as text by the callers — never as HTML). Credentials
// keep the username visible but mask the secret ("user:pass" -> "user : ••••••"); other secret types
// (sid, password_policy) are fully masked; everything else is shown as-is.
export const maskFindingValue = (typeFindings?: string, value?: string): string => {
  if (!value) {
    return '';
  }
  if (typeFindings === 'credentials') {
    const sep = value.search(/[:\s]/);
    // Only reveal the username half when there is a real "username<sep>secret" split; a value with
    // no separator (or one starting with it) is treated as a bare secret and fully masked, so we
    // never render a captured secret in the clear.
    return sep > 0 ? `${value.slice(0, sep)} : ••••••` : '••••••••';
  }
  if (SENSITIVE_FINDING_TYPES.has(typeFindings ?? '')) {
    return '••••••••';
  }
  return value;
};

// Card filter -> the ContractOutputType finding-type values it focuses (issue 6647). "files" maps to
// `share` as a temporary stand-in until a native file finding type exists; "users" also includes
// admin usernames per product decision.
export const FILTER_TO_FINDING_TYPES: Record<Exclude<AttackPathFindingFilter, 'endpoints'>, string[]> = {
  files: ['share'],
  credentials: ['credentials'],
  users: ['username', 'admin_username'],
  cves: ['cve'],
};

const DIMMED_OPACITY = 0.18;

/**
 * Focus the graph on a set of finding types (or "endpoints") by dimming everything not on a path to
 * them — without re-laying-out, so node positions stay stable. "endpoints" lights the injector ->
 * endpoint backbone; a type list lights matching finding / finding-type / finding-cluster nodes, the
 * endpoints that carry those types (via findingCounts in collapsed mode), and every upstream node.
 */
export const applyFindingFilter = (
  nodes: AttackPathFlowNode[],
  edges: AttackPathFlowEdge[],
  focus: readonly string[] | 'endpoints' | null,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  if (!focus || (Array.isArray(focus) && focus.length === 0)) {
    return {
      nodes,
      edges,
    };
  }
  const matched = new Set<string>();

  if (focus === 'endpoints') {
    for (const n of nodes) {
      if (n.type === AP_FLOW_NODE_TYPE.asset
        || n.type === AP_FLOW_NODE_TYPE.injector
        || n.type === AP_FLOW_NODE_TYPE.endpointCluster) {
        matched.add(n.id);
      }
    }
  } else {
    const types = new Set(focus);
    for (const n of nodes) {
      const d = n.data;
      const isFindingMatch = (n.type === AP_FLOW_NODE_TYPE.finding
        || n.type === AP_FLOW_NODE_TYPE.findingType
        || n.type === AP_FLOW_NODE_TYPE.findingCluster)
      && !!d.typeFindings && types.has(d.typeFindings);
      const isAssetMatch = n.type === AP_FLOW_NODE_TYPE.asset
        && !!d.findingCounts
        && Object.entries(d.findingCounts).some(([k, v]) => types.has(k) && (v ?? 0) > 0);
      if (isFindingMatch || isAssetMatch) {
        matched.add(n.id);
      }
    }
    // Walk upstream (finding[-cluster] -> endpoint[-cluster] -> injector) twice so the whole path to
    // the finding stays lit.
    for (let pass = 0; pass < 2; pass += 1) {
      for (const e of edges) {
        if (e.target && e.source && matched.has(e.target) && !matched.has(e.source)) {
          matched.add(e.source);
        }
      }
    }
  }

  const dimmedNodes = nodes.map(n => (matched.has(n.id)
    ? {
        ...n,
        data: {
          ...n.data,
          dimmed: false,
        },
        style: {
          ...n.style,
          opacity: 1,
        },
      }
    : {
        ...n,
        data: {
          ...n.data,
          dimmed: true,
        },
        style: {
          ...n.style,
          opacity: DIMMED_OPACITY,
        },
      }));
  const dimmedEdges = edges.map((e) => {
    const lit = !!e.source && !!e.target && matched.has(e.source) && matched.has(e.target);
    return {
      ...e,
      data: {
        ...(e.data as AttackPathFlowEdgeData),
        dimmed: !lit,
      },
    };
  });
  return {
    nodes: dimmedNodes,
    edges: dimmedEdges,
  };
};
