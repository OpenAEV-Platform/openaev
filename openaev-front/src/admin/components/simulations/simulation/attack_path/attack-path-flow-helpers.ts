import { type Edge, type Node } from '@xyflow/react';

import type { AttackPathAttackPatternDTO, AttackPathDTO, AttackPathEdges, AttackPathNodeDTO } from '../../../../../utils/api-types';
import { AP_ENDPOINT_SIZE, AP_FINDING_SIZE, AP_INJECTOR_SIZE } from './nodes/node-sizes';

// Attack-path execution-store POC (issue 6647). Pure mapping of the backend AttackPathDTO onto
// React Flow nodes and edges, with a manual column layout (no layout lib, mirroring AttackPath.tsx).
// Executions are carried on the edges (design O2), never as flow nodes, so the graph stays a handful
// of node kinds regardless of how many executions a simulation ran.

// Minimal shape of the i18n formatter's `t`, threaded into the layout builders so the edge labels they
// generate ("<type> found", "N open ports", "Triggered <event>"…) render in the session language.
export type ApTranslate = (key: string, params?: Record<string, string>) => string;

export const AP_FLOW_NODE_TYPE = {
  injector: 'apInjector',
  asset: 'apAsset',
  findingType: 'apFindingType',
  finding: 'apFinding',
  endpointCluster: 'apEndpointCluster',
  findingCluster: 'apFindingCluster',
} as const;

export const AP_FLOW_EDGE_TYPE = 'apGrouped';

// Kill-chain causal edge kind (issue 6647): additive edges drawn from a producing finding (or a
// depended-on action) to the execution/injector node that consumes it. Registered in edges/index.ts.
export const AP_FLOW_CAUSAL_EDGE_TYPE = 'apCausal';

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
  // For an injector/execution node: the id of the step template it ran. Carried so the kill-chain
  // causal builder can look up execution metadata (dependsOn / consumedFindingKeys) per node. Mirrors
  // AttackPathNodeDTO.stepTemplateId; absent on nodes that have no step template (e.g. findings).
  stepTemplateId?: string;
  // For an injector node: the real injector type/slug from the backend (AttackPathNodeDTO.injectorType),
  // used to resolve the catalog icon without guessing from the label. Absent on synthetic seed injectors.
  injectorType?: string;
  // For an injector node: the ATT&CK techniques the backend resolved from its contract
  // (AttackPathNodeDTO.attackPatterns), surfaced on the node so the analyst sees them without a click.
  attackPatterns?: AttackPathAttackPatternDTO[];
  // For a finding node: the id of the endpoint (ASSET) node it was discovered on, so a direct click
  // on the finding can open its details panel by focusing that endpoint's path.
  assetNodeId?: string;
  // For an endpoint (ASSET) node: its 1-based rank among the top chokepoints (most findings), used to
  // badge the most-exposed endpoints. Absent when the endpoint is not a top chokepoint.
  chokepointRank?: number;
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
  // Kill-chain causal edges only (type AP_FLOW_CAUSAL_EDGE_TYPE): 'finding' => a produced finding
  // value feeds the consuming execution (solid); 'depend' => pure dependsOn sequencing (dashed).
  causalKind?: 'finding' | 'depend';
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
  stepTemplateId: n.stepTemplateId,
  injectorType: n.injectorType,
  attackPatterns: n.attackPatterns,
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
// Row height for an expanded individual finding: must leave room for the value label rendered ABOVE the
// finding node (else a stacked finding's label overlaps the node above it, e.g. long UNC share paths).
const CLUSTER_FINDING_DETAIL_ROW = 96;
const CLUSTER_INJECTOR_HALF_H = 36;
const CLUSTER_EP_HALF_H = 42;
// Vertical spacing between the stacked injectors on the left band of the deduped view.
const CLUSTER_INJECTOR_ROW_H = 120;

// Deduped clustered view: endpoints are shared across injectors (an action reaches many endpoints and
// an endpoint is reached by many actions), so there is ONE shared endpoint hub instead of one per
// injector. This is its stable id and the key used for its expand/collapse batch and its global
// finding fetch (see AP_ALL_ENDPOINTS consumers in SimulationAttackPath).
export const AP_ALL_ENDPOINTS = '__all_endpoints__';
export const AP_SHARED_EP_CLUSTER_ID = 'cl-ep-all';

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
  // The endpoint (ASSET) node id this column hangs off, when known (per-endpoint column). Used as a
  // fallback origin for findings whose own assetNodeId is absent.
  endpointNodeId: string | undefined,
  t: ApTranslate,
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
        status: e.typeStatus,
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
        label: t('{type} found', { type: e.type }),
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
          assetNodeId: f.assetNodeId ?? endpointNodeId,
          status: e.typeStatus,
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
  // Endpoint expand/collapse batch. In the deduped view there is a single shared hub, so this maps
  // the AP_ALL_ENDPOINTS key to how many endpoints are revealed (0 / absent = collapsed).
  endpointBatch: Map<string, number>,
  t: ApTranslate,
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

  // Deduped topology: which injectors reached each distinct endpoint (an endpoint is reached by many
  // actions), in first-seen order, and each injector's reached-endpoint count (an action reaches many
  // endpoints). Endpoints are SHARED across injectors — never repeated once per injector.
  const injectorsByEndpoint = new Map<string, string[]>();
  const reachedOrder: string[] = [];
  for (const e of execEdges) {
    const src = e.edgeSourceId;
    const tgt = e.edgeTargetId;
    if (!src || !tgt || !assetById.has(tgt)) {
      continue;
    }
    const injs = injectorsByEndpoint.get(tgt) ?? [];
    if (!injs.includes(src)) {
      injs.push(src);
    }
    injectorsByEndpoint.set(tgt, injs);
    if (!reachedOrder.includes(tgt)) {
      reachedOrder.push(tgt);
    }
  }

  // Reveal the most-exposed endpoints first (most findings = highest chokepoint score), so expanding
  // the shared hub surfaces the chokepoints (and their badges) up front rather than burying them.
  const endpointFindingTotal = (assetId: string): number =>
    Object.values(assetById.get(assetId)?.findingCounts ?? {}).reduce((s, v) => s + (v ?? 0), 0);
  // Deterministic order: by finding total desc, then a stable tie-breaker on the asset id. Without the
  // tie-breaker, endpoints with equal totals (common — every endpoint is 0 early in a run) keep DTO edge
  // insertion order, which can differ between live-refresh polls and makes the endpoints visibly swap.
  reachedOrder.sort((a, b) => endpointFindingTotal(b) - endpointFindingTotal(a) || a.localeCompare(b));

  const nodes: AttackPathFlowNode[] = [];
  const edges: AttackPathFlowEdge[] = [];

  const total = reachedOrder.length;
  const shown = Math.min(Math.max(endpointBatch.get(AP_ALL_ENDPOINTS) ?? 0, 0), total);
  const expanded = shown > 0;

  const reachedAssets = reachedOrder.map(id => assetById.get(id));
  const overallStatus = aggregateStatus(reachedAssets.map(a => a?.status));

  // Global finding-type aggregate, deduped across the distinct endpoints (each endpoint counted once).
  const typeSum = new Map<string, number>();
  for (const asset of reachedAssets) {
    for (const [k, v] of Object.entries(asset?.findingCounts ?? {})) {
      typeSum.set(k, (typeSum.get(k) ?? 0) + (v ?? 0));
    }
  }
  const types = [...typeSum.entries()].filter(([, v]) => v > 0);

  // Left band: all injectors stacked. The shared endpoint hub and the finding column are centred
  // against whichever side (injectors vs endpoints/findings) is taller.
  const injectorsH = Math.max(CLUSTER_EP_ROW_H, injectors.length * CLUSTER_INJECTOR_ROW_H);

  // Collapsed: one GLOBAL finding column hanging off the shared hub (finding clusters keyed by type
  // only, no injector — they aggregate every reached endpoint).
  const agg = layoutFindingColumn(
    types,
    type => aggregateStatus(reachedAssets.filter(a => (a?.findingCounts?.[type] ?? 0) > 0).map(a => a?.status)),
    type => `cl-ft-${type}`,
    findingExpansion,
  );

  // Expanded: each revealed (deduped) endpoint fans out to its OWN finding column.
  const shownAssetIds = reachedOrder.slice(0, shown);
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

  const blockH = Math.max(CLUSTER_ROW_UNIT, injectorsH, expanded ? endpointColHeight : agg.height);
  const centerY = PADDING + blockH / 2;

  // Injectors, stacked and vertically centred against the whole block.
  const injTop = centerY - injectorsH / 2;
  injectors.forEach((inj, i) => {
    nodes.push({
      id: inj.id as string,
      type: AP_FLOW_NODE_TYPE.injector,
      position: {
        x: PADDING,
        y: injTop + i * CLUSTER_INJECTOR_ROW_H + CLUSTER_INJECTOR_ROW_H / 2 - CLUSTER_INJECTOR_HALF_H,
      },
      data: nodeData(inj),
    });
  });

  // The single SHARED endpoint hub (dedup): every injector points at it and it carries the total
  // distinct endpoint count. Its expand/collapse batch is keyed by AP_ALL_ENDPOINTS. The converging
  // injector edges make it a natural chokepoint anchor.
  const clusterId = AP_SHARED_EP_CLUSTER_ID;
  nodes.push({
    id: clusterId,
    type: AP_FLOW_NODE_TYPE.endpointCluster,
    position: {
      x: CLUSTER_EP_X,
      y: centerY - CLUSTER_EP_HALF_H,
    },
    data: {
      count: total,
      injectorId: AP_ALL_ENDPOINTS,
      clusterKind: 'header',
      expanded,
      status: overallStatus,
    },
  });
  injectors.forEach((inj) => {
    const injId = inj.id as string;
    const injStatus = aggregateStatus(
      reachedOrder
        .filter(ep => (injectorsByEndpoint.get(ep) ?? []).includes(injId))
        .map(ep => assetById.get(ep)?.status),
    );
    edges.push({
      id: `${injId}-${clusterId}`,
      source: injId,
      target: clusterId,
      type: AP_FLOW_EDGE_TYPE,
      // No count badge here: the reached-endpoint count is already shown on the endpoint-cluster hub, so
      // repeating it on the injector edge is redundant. (A distinct-contract count would need the backend
      // to expose per-injector contract info on the collapsed graph — not available client-side.)
      data: {
        count: 1,
        status: injStatus,
      },
    });
  });

  // Revealed endpoints (deduped), each with its own finding column; a "+rest" overflow follows. When
  // collapsed, the global aggregate finding column hangs off the shared hub instead.
  if (expanded) {
    let epY = centerY - endpointColHeight / 2;
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
      pushFindingColumn(nodes, edges, epCol.items, assetId, epY + (epBlockH - epCol.height) / 2, AP_ALL_ENDPOINTS, asset.ref ?? assetId, assetId, t);
      epY += epBlockH;
    });
    if (total > shown) {
      const overflowId = 'cl-ep-more-all';
      nodes.push({
        id: overflowId,
        type: AP_FLOW_NODE_TYPE.endpointCluster,
        position: {
          x: CLUSTER_EP_DETAIL_X,
          y: epY + CLUSTER_EP_ROW_H / 2 - CLUSTER_EP_HALF_H,
        },
        data: {
          count: total - shown,
          injectorId: AP_ALL_ENDPOINTS,
          clusterKind: 'overflow',
          status: overallStatus,
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
          status: overallStatus,
        },
      });
    }
  } else {
    pushFindingColumn(nodes, edges, agg.items, clusterId, centerY - agg.height / 2, AP_ALL_ENDPOINTS, undefined, undefined, t);
  }

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
// A node's internal id (e.g. "NODE_ENDPOINT|<uuid>") must never surface as a user-facing label. When a
// node can't be resolved to a real name, strip the "NODE_*|" prefix so the worst case is a bare ref,
// not the internal id the analyst has no use for.
export const friendlyNodeId = (raw?: string): string => (raw ?? '').replace(/^NODE_[A-Z_]+\|/, '');

// Human-readable plural noun for a finding type, used to give contextual cluster edges a label with
// meaning (e.g. "6 credentials" instead of a bare "6+"). Mixed clusters fall back to "findings".
export const findingCategoryNoun = (typeFindings?: string): string => {
  switch (typeFindings) {
    case 'credentials':
      return 'credentials';
    case 'username':
    case 'admin_username':
      return 'users';
    case 'cve':
      return 'CVEs';
    case 'port':
      return 'open ports';
    case 'hash':
      return 'hashes';
    case 'file':
      return 'files';
    case 'password_policy':
      return 'password policies';
    case 'sid':
      return 'SIDs';
    default:
      // Data-driven: an unmapped type (a finding type added later, e.g. from a new event field) still
      // reads as itself, humanised (snake_case -> words) — never a generic "findings" that hides it.
      return typeFindings ? typeFindings.replace(/_/g, ' ') : 'findings';
  }
};

export const buildFindingPathFlow = (
  dto: AttackPathDTO,
  finding: PathFinding,
  t: ApTranslate,
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
  // The focused endpoint's verdict, applied to its finding clusters/edges so they carry the
  // prevention/detection colour (green/orange/red) — blue is reserved for the actively selected path.
  const endpointStatus = endpoint?.status;
  // Always lay the endpoint out as one cluster per finding type (whether we arrived here from a
  // chokepoint/endpoint click or from a specific finding): a specific finding is highlighted inside
  // its own type cluster rather than pulled out as a separate node, so there is a single cluster per
  // type and no confusing "other" bucket.
  const focusTypes = (Object.entries(endpointCounts).filter(([, v]) => (v ?? 0) > 0)) as Array<[string, number]>;

  const rowH = CLUSTER_FINDING_ROW_H;
  const rightRows = Math.max(1, focusTypes.length);
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
        status: endpointStatus,
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
    clusterX: number = CTX_CLUSTER_X,
    childX: number = CTX_CHILD_X,
    status: string | undefined = endpointStatus,
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
        x: clusterX,
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
        status,
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
        status,
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
          x: childX,
          y: top + CLUSTER_FINDING_ROW_H + j * CTX_ROW_H,
        },
        data: {
          label: f.value ?? f.label,
          typeFindings: f.typeFindings,
          status,
        },
      });
      edges.push({
        id: `${id}-${fid}`,
        source: id,
        target: fid,
        type: AP_FLOW_EDGE_TYPE,
        data: {
          count: 1,
          status,
        },
      });
    });
    if (overflow > 0) {
      const moreId = `${id}-more`;
      nodes.push({
        id: moreId,
        type: AP_FLOW_NODE_TYPE.findingCluster,
        position: {
          x: childX,
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

  // One cluster per finding type on the endpoint, hanging straight off it, each expandable in place.
  // A specific finding focus highlights its own type cluster (see the page's selection logic) rather
  // than extracting the finding, so there is exactly one cluster per type.
  let clusterY = rightTopY;
  focusTypes.forEach(([type, count]) => {
    const id = `path-cl-type|${type}|${finding.endpointKey}`;
    clusterY += emitCtxCluster(
      id,
      type,
      count,
      `${count} ${t(findingCategoryNoun(type))}`,
      clusterY,
      CLUSTER_FINDING_X,
      CLUSTER_FINDING_DETAIL_X,
    );
  });

  return {
    nodes,
    edges,
  };
};

// 'endpoints' is the special backbone focus; any other value is a finding type (or curated grouping)
// resolved through FILTER_TO_FINDING_TYPES, defaulting to the type itself so new types work with no code.
export type AttackPathFindingFilter = 'endpoints' | string;

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

// Card filter -> the finding-type values it focuses (issue 6647). "files" maps to `file` (the backend
// presents SMB `share` findings as `file`, an interim stand-in until a native file finding type
// exists); "users" also includes admin usernames per product decision.
export const FILTER_TO_FINDING_TYPES: Record<Exclude<AttackPathFindingFilter, 'endpoints'>, string[]> = {
  files: ['file'],
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

// ---------------------------------------------------------------------------
// Kill-chain causal edges (issue 6647)
// ---------------------------------------------------------------------------
// Structural mirror of the future AttackPathNodeDTO kill-chain fields (dependsOn,
// consumedFindingKeys). Declared locally (not imported from the temporary mock) so the mock module
// can be deleted 1:1 once the backend exposes these fields, without touching this builder — the
// accessor passed in is simply repointed at the DTO. The mock's KillChainExecMeta is structurally
// assignable to this type.
export interface CausalConsumedKey {
  keyType: string;
  operator: string;
  value: string;
  // Name of the event (root filter condition) this key belongs to, so the causal edge can read
  // "Triggered <event>" rather than the raw "<key> = <value>". Optional (older/nameless events).
  eventName?: string;
}

export interface CausalStepMeta {
  dependsOn: string[];
  consumedFindingKeys: CausalConsumedKey[];
}

// The lookup key used to resolve a flow node's kill-chain metadata. Kill-chain data is aggregated PER
// INJECTOR (an injector's executions carry the consumed keys; see buildKillChainMeta), and the aggregate
// is keyed by the injector node id. Injector flow nodes carry no stepTemplateId, so this resolves to the
// node id (= the injector node id the meta map is keyed by). The stepTemplateId branch stays for any
// future per-execution node rendering.
const causalLookupKey = (node: AttackPathFlowNode): string =>
  (typeof node.data.stepTemplateId === 'string' && node.data.stepTemplateId
    ? node.data.stepTemplateId
    : node.id);

// Build the per-injector kill-chain metadata from the graph DTO (issue 6647). The backend exposes
// `dependsOn` + `consumedFindingKeys` on each EXECUTION node (AttackPathNodeDTO in
// dto.attackPathExecutions, carrying stepTemplateId). Executions are rendered as EDGES, not nodes, so we
// aggregate each injector's executions' keys onto the injector node: an EDGE_EXECUTIONS edge links an
// injector (edgeSourceId) to its executions (edge.executionIds ↔ execution.ref). The result is keyed by
// injector node id — exactly what causalLookupKey resolves for injector flow nodes. `dependsOn` (step
// template ids) is resolved to the injector node ids that ran those templates, so the causal builder can
// draw injector→injector dashed edges. Returns an empty map when the DTO carries no kill-chain data
// (additive: no causal edges), e.g. a run whose steps had no conditions.
export const buildKillChainMeta = (dto: AttackPathDTO | null | undefined): Map<string, CausalStepMeta> => {
  const byInjector = new Map<string, CausalStepMeta>();
  if (!dto) {
    return byInjector;
  }
  const execEdges = (dto.attackPathEdges ?? []).filter(e => e.type === 'EDGE_EXECUTIONS');
  const execByRef = new Map<string, AttackPathNodeDTO>();
  for (const e of dto.attackPathExecutions ?? []) {
    if (e.ref) {
      execByRef.set(e.ref, e);
    }
  }
  // First pass: which injector ran each step template (to resolve dependsOn to injector node ids).
  const injectorByStepTemplateId = new Map<string, string>();
  for (const edge of execEdges) {
    const injectorId = edge.edgeSourceId;
    if (!injectorId) {
      continue;
    }
    for (const ref of edge.executionIds ?? []) {
      const stepTpl = execByRef.get(ref)?.stepTemplateId;
      if (stepTpl) {
        injectorByStepTemplateId.set(stepTpl, injectorId);
      }
    }
  }
  // Second pass: aggregate consumed keys + resolved dependsOn per injector.
  for (const edge of execEdges) {
    const injectorId = edge.edgeSourceId;
    if (!injectorId) {
      continue;
    }
    const meta = byInjector.get(injectorId) ?? {
      dependsOn: [],
      consumedFindingKeys: [],
    };
    for (const ref of edge.executionIds ?? []) {
      const exec = execByRef.get(ref);
      if (!exec) {
        continue;
      }
      for (const key of exec.consumedFindingKeys ?? []) {
        if (key.keyType
          && !meta.consumedFindingKeys.some(k => k.keyType === key.keyType && k.operator === (key.operator ?? '') && k.value === (key.value ?? ''))) {
          meta.consumedFindingKeys.push({
            keyType: key.keyType,
            operator: key.operator ?? 'EQ',
            value: key.value ?? '',
            eventName: key.eventName,
          });
        }
      }
      for (const stepTpl of exec.dependsOn ?? []) {
        const depInjector = injectorByStepTemplateId.get(stepTpl);
        if (depInjector && depInjector !== injectorId && !meta.dependsOn.includes(depInjector)) {
          meta.dependsOn.push(depInjector);
        }
      }
    }
    byInjector.set(injectorId, meta);
  }
  return byInjector;
};

// Read a finding node's produced value (stored on data.label for finding leaf nodes, with an optional
// data.value mirror), as a string for comparison against a consumed finding key.
const findingNodeValue = (node: AttackPathFlowNode): string => {
  const raw = node.data.value ?? node.data.label;
  return typeof raw === 'string' ? raw : '';
};

// A consumed key uses the raw PrimitiveType vocabulary (e.g. `share_name`, `password`) while finding
// nodes use the finding-type vocabulary (`file`, `credentials`). Reconcile the known complex sub-field
// keys to their finding type here. Primitives that already match a finding type 1:1 (`port`, `cve`,
// `ipv4`, `username`, `hostname`, `hash`…) are left untouched (identity). NOTE: reconciling the VALUE of
// a complex finding (reaching into its sub-field, e.g. a share's `share_name`) is the front-side complex
// matching still to come — today only the TYPE is reconciled and value comparison stays direct, which is
// correct for primitives; complex value-matching is a follow-up (see backend requirements topo).
const KEYTYPE_TO_FINDING_TYPE: Record<string, string> = {
  share_name: 'file',
  password: 'credentials',
};

// Label for a causal (finding → consuming action) edge. When the backend named the event (the root
// filter condition the key belongs to), read it as "Triggered <event>" so the analyst sees WHY the next
// action ran (its event matched); otherwise fall back to the raw "<key> = <masked value>" match.
const causalKeyLabel = (key: CausalConsumedKey, t: ApTranslate): string =>
  (key.eventName && key.eventName.trim()
    ? t('Triggered {event}', { event: key.eventName })
    : `${key.keyType} = ${maskFindingValue(key.keyType, key.value)}`);

// Does a produced finding node satisfy a consumed finding key?
//   - the finding's type (data.typeFindings) must equal the reconciled key type, and
//   - EQ => the finding value equals key.value exactly;
//   - IN => the finding value is one of key.value's comma-separated members (a small, explicit list
//     semantics; trimmed). Falls back to substring containment for a single-token key.
// Only EQ and IN are handled now. SKIPPED operators (no edge emitted, no silent cap): NEQ, GT, GTE,
// LT, LTE, CONTAINS, REGEX, and any other — add them here when the backend needs them.
const findingMatchesKey = (node: AttackPathFlowNode, key: CausalConsumedKey): boolean => {
  if (node.type !== AP_FLOW_NODE_TYPE.finding) {
    return false;
  }
  const reconciledType = KEYTYPE_TO_FINDING_TYPE[key.keyType] ?? key.keyType;
  if ((node.data.typeFindings ?? '') !== reconciledType) {
    return false;
  }
  // Guard the real DTO field (the mock is always a string, but the backend value may be null/undefined):
  // a non-string key value can never match and must not reach key.value.split() below.
  if (typeof key.value !== 'string') {
    return false;
  }
  const value = findingNodeValue(node);
  if (key.operator === 'EQ') {
    return value === key.value;
  }
  if (key.operator === 'IN') {
    const members = key.value.split(',').map(s => s.trim()).filter(Boolean);
    if (members.length > 1) {
      return members.includes(value);
    }
    return value.includes(key.value);
  }
  // Unsupported operator (see list above): ignore, emit nothing.
  return false;
};

/**
 * Build the additive kill-chain causal edges for a set of flow nodes (issue 6647).
 *
 * For every execution/injector node that has kill-chain metadata (resolved via {@code getMeta} on the
 * node's {@link causalLookupKey}):
 *   - each {@code consumedFindingKey} is matched against the produced FINDING leaf nodes present; every
 *     match emits a SOLID edge (data.causalKind = 'finding') from the finding node -> the consuming
 *     node, labelled "<keyType> = <value>";
 *   - each {@code dependsOn} entry emits a DASHED edge (data.causalKind = 'depend') from the depended
 *     step's node -> this node, but ONLY when this node produced no finding edge at all. When a finding
 *     matched, the solid finding edge(s) already express the causal inflow, so the redundant dashed
 *     sequencing edge is suppressed. (Finding leaf nodes carry no producing-step id in the flow model,
 *     so a finding edge cannot be attributed to a specific dependsOn entry; this per-node rule is the
 *     best correlation available until the backend links findings to their producing step.)
 *
 * Direction is always producer -> consumer. Missing nodes are skipped, never thrown on. The result is
 * purely additive: when {@code getMeta} returns undefined for every node (e.g. mock ids not yet
 * adapted), it returns [] and the existing graph is unchanged.
 */
export const buildCausalEdges = (
  nodes: AttackPathFlowNode[],
  getMeta: (stepTemplateId?: string) => CausalStepMeta | undefined,
  t: ApTranslate,
): AttackPathFlowEdge[] => {
  // Only injector/execution nodes can carry kill-chain meta and be a dependsOn target.
  const executionNodes = nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector);
  if (executionNodes.length === 0) {
    return [];
  }
  const findingNodes = nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.finding);
  // Map a lookup key -> the execution/injector node id it resolves to, so a dependsOn entry can find
  // the depended step's node. First writer wins (stable) when several nodes share a key.
  const nodeByKey = new Map<string, AttackPathFlowNode>();
  for (const n of executionNodes) {
    const key = causalLookupKey(n);
    if (!nodeByKey.has(key)) {
      nodeByKey.set(key, n);
    }
  }
  // Causal edges are appended after the selection/filter pass has stamped data.dimmed on the base
  // nodes/edges, so they must inherit the dim state from their endpoints or they would render at full
  // opacity over an otherwise dimmed graph in the focused/filtered views.
  const edgeDimmed = (a: AttackPathFlowNode, b: AttackPathFlowNode): boolean =>
    (a.data.dimmed ?? false) || (b.data.dimmed ?? false);

  const edges: AttackPathFlowEdge[] = [];
  const seen = new Set<string>();
  const pushEdge = (edge: AttackPathFlowEdge) => {
    if (seen.has(edge.id)) {
      return;
    }
    seen.add(edge.id);
    edges.push(edge);
  };

  for (const node of executionNodes) {
    const meta = getMeta(causalLookupKey(node));
    if (!meta) {
      continue;
    }
    let matchedAnyFinding = false;
    for (const key of meta.consumedFindingKeys ?? []) {
      for (const finding of findingNodes) {
        if (!findingMatchesKey(finding, key)) {
          continue;
        }
        matchedAnyFinding = true;
        pushEdge({
          id: `${AP_FLOW_CAUSAL_EDGE_TYPE}-finding-${finding.id}-${node.id}-${key.keyType}-${key.value}`,
          source: finding.id,
          target: node.id,
          type: AP_FLOW_CAUSAL_EDGE_TYPE,
          data: {
            count: 1,
            causalKind: 'finding',
            // "Triggered <event>" when the event is named, else the masked "<key> = <value>" match
            // (sensitive values are masked exactly like every other finding surface).
            label: causalKeyLabel(key, t),
            dimmed: edgeDimmed(finding, node),
          },
        });
      }
    }
    // Dashed dependsOn sequencing, only when no finding edge already expresses the inflow.
    if (!matchedAnyFinding) {
      for (const depKey of meta.dependsOn ?? []) {
        const sourceNode = nodeByKey.get(depKey);
        if (!sourceNode || sourceNode.id === node.id) {
          continue;
        }
        pushEdge({
          id: `${AP_FLOW_CAUSAL_EDGE_TYPE}-depend-${sourceNode.id}-${node.id}`,
          source: sourceNode.id,
          target: node.id,
          type: AP_FLOW_CAUSAL_EDGE_TYPE,
          data: {
            count: 1,
            causalKind: 'depend',
            dimmed: edgeDimmed(sourceNode, node),
          },
        });
      }
    }
  }

  return edges;
};

// ===== Causal execution-chain layout (issue 6647) =====
// Renders the graph as the ACTUAL kill chain, left-to-right in causal order (dependsOn topology):
//   inject → endpoint(s) it targeted → finding(s) it produced → the next inject that consumes them → …
// Because a finding is placed on its PRODUCER step and the consuming inject sits one depth to the right,
// every causal edge flows forward — there are no backward-crossing edges (the injector-fan-to-hub layout
// forced the consuming inject upstream of the finding, which read as illegible crossings). Built from the
// FULL dto: executions carry stepTemplateId / dependsOn / consumedFindingKeys / findingsNodeIds, which the
// backend only emits in full mode (applyKillChain is full-only).
const CHAIN_COL_W = 620; // horizontal span of one causal depth (inject + endpoint + finding + gap)
const CHAIN_EP_DX = 210; // inject → endpoint horizontal offset within a step
const CHAIN_FIND_DX = 400; // inject → produced-finding horizontal offset within a step
const CHAIN_FIND_ROW = 130; // vertical gap between findings stacked on the SAME endpoint (room for the
// value label rendered above each finding node, so stacked findings never overlap)
const CHAIN_STEP_GAP = 80; // vertical gap between two asset blocks sharing a depth (same column)
const CHAIN_INJECTOR_ROW = 110; // vertical slot per injector when several share one endpoint block
const CHAIN_EP_BLOCK_MIN = 120; // minimum height of one endpoint block (endpoint node + breathing room)
const CHAIN_FIND_HALF = 28; // half of AP_FINDING_SIZE (56), to centre a finding node on its row

interface ChainStep {
  injectorId: string;
  // endpoint node id -> the finding node ids this injector produced ON that endpoint (so each finding
  // hangs off the endpoint it was actually discovered on, not an arbitrary first one).
  endpoints: Map<string, string[]>;
  // endpoint node id -> the contract name run against it (what was launched), for the inject→endpoint
  // edge label. First non-empty contract name wins when several executions hit the same endpoint.
  contractByEndpoint: Map<string, string>;
  consumed: CausalConsumedKey[];
  deps: Set<string>;
}

export const buildCausalChainFlow = (
  dto: AttackPathDTO,
  t: ApTranslate,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const dtoNodes = dto.attackPathNodes ?? [];
  // Index by BOTH node id and ref: an execution edge may key an endpoint/injector by either form, and a
  // miss here is what surfaces a raw "NODE_ENDPOINT|<uuid>" label instead of the resolved hostname.
  const injectorById = new Map<string, typeof dtoNodes[number]>();
  const assetById = new Map<string, typeof dtoNodes[number]>();
  dtoNodes.forEach((n) => {
    let target: Map<string, typeof dtoNodes[number]> | null = null;
    if (n.type === 'INJECTOR') {
      target = injectorById;
    } else if (n.type === 'ASSET') {
      target = assetById;
    }
    if (!target) {
      return;
    }
    if (n.id) {
      target.set(n.id as string, n);
    }
    if (n.ref) {
      target.set(n.ref as string, n);
    }
  });
  const findingById = new Map(dtoNodes.filter(n => n.type === 'FINDING' && n.id).map(n => [n.id as string, n]));
  const execByRef = new Map((dto.attackPathExecutions ?? []).filter(e => e.ref).map(e => [e.ref as string, e]));
  const execEdges = (dto.attackPathEdges ?? []).filter(e => e.type === EDGE_EXECUTIONS);

  // Each execution edge links one injector (edgeSourceId) to one endpoint (edgeTargetId), carrying the
  // execution refs that ran it — so we recover, per execution, which injector ran it and which endpoint.
  const injByExec = new Map<string, string>();
  const epByExec = new Map<string, string>();
  for (const e of execEdges) {
    for (const ref of e.executionIds ?? []) {
      if (e.edgeSourceId) {
        injByExec.set(ref, e.edgeSourceId);
      }
      if (e.edgeTargetId) {
        epByExec.set(ref, e.edgeTargetId);
      }
    }
  }
  // Resolve a dependsOn (a prerequisite step template id) to the injector that ran it.
  const injByStepTpl = new Map<string, string>();
  for (const [ref, ex] of execByRef) {
    const inj = injByExec.get(ref);
    if (inj && ex.stepTemplateId) {
      injByStepTpl.set(ex.stepTemplateId, inj);
    }
  }

  // Aggregate executions into one step per injector (endpoints reached, findings produced/consumed, and
  // the injectors it depends on — the kill-chain stays a handful of nodes regardless of execution count).
  const steps = new Map<string, ChainStep>();
  const ensureStep = (id: string): ChainStep => {
    let s = steps.get(id);
    if (!s) {
      s = {
        injectorId: id,
        endpoints: new Map(),
        contractByEndpoint: new Map(),
        consumed: [],
        deps: new Set(),
      };
      steps.set(id, s);
    }
    return s;
  };
  for (const [ref, ex] of execByRef) {
    const inj = injByExec.get(ref);
    if (!inj) {
      continue;
    }
    const s = ensureStep(inj);
    const ep = epByExec.get(ref);
    if (ep) {
      const epFindings = s.endpoints.get(ep) ?? [];
      for (const fid of ex.findingsNodeIds ?? []) {
        if (!epFindings.includes(fid)) {
          epFindings.push(fid);
        }
      }
      s.endpoints.set(ep, epFindings);
      if (ex.contractName && !s.contractByEndpoint.has(ep)) {
        s.contractByEndpoint.set(ep, ex.contractName);
      }
    }
    for (const k of ex.consumedFindingKeys ?? []) {
      if (k.keyType && !s.consumed.some(c => c.keyType === k.keyType && c.operator === (k.operator ?? '') && c.value === (k.value ?? ''))) {
        s.consumed.push({
          keyType: k.keyType,
          operator: k.operator ?? 'EQ',
          value: k.value ?? '',
          eventName: k.eventName,
        });
      }
    }
    for (const dep of ex.dependsOn ?? []) {
      const depInj = injByStepTpl.get(dep);
      if (depInj && depInj !== inj) {
        s.deps.add(depInj);
      }
    }
  }

  // Causal depth = longest dependsOn chain to a root, so an injector always sits to the right of every
  // injector it depends on. A cycle (should not happen) is broken by treating a re-entered node as depth 0.
  const depth = new Map<string, number>();
  const inProgress = new Set<string>();
  const depthOf = (id: string): number => {
    const cached = depth.get(id);
    if (cached !== undefined) {
      return cached;
    }
    if (inProgress.has(id)) {
      return 0;
    }
    inProgress.add(id);
    let d = 0;
    for (const dep of steps.get(id)?.deps ?? []) {
      d = Math.max(d, depthOf(dep) + 1);
    }
    inProgress.delete(id);
    depth.set(id, d);
    return d;
  };
  for (const id of steps.keys()) {
    depthOf(id);
  }
  const byDepth = new Map<number, string[]>();
  for (const id of steps.keys()) {
    const d = depth.get(id) ?? 0;
    const lane = byDepth.get(d) ?? [];
    lane.push(id);
    byDepth.set(d, lane);
  }

  const nodes: AttackPathFlowNode[] = [];
  const edges: AttackPathFlowEdge[] = [];

  // Every edge label (contract name, "<type> found", causal "Triggered …") is a bordered box centred on
  // its edge. The box padding is constant; on top of that each label must keep the SAME clearance to the
  // nodes at both ends, so a long label never ends up crushed against a node while short ones float free.
  // That means the segment length must GROW with the label: a mid-point label of pixel width W centred
  // between two nodes clears both by CLEARANCE when the centre-to-centre distance is
  // W + 2·max(halfLeft, halfRight) + 2·CLEARANCE. Gaps are computed per COLUMN (its own longest label),
  // so each column is exactly as wide as it needs and endpoints of a depth stay aligned.
  const CAPTION_CHAR_PX = 6.7; // rough advance width of one caption-size char
  const LABEL_H_PAD = 12; // the label box' own horizontal padding (spacing(0.75) each side)
  const LABEL_CLEARANCE = 18; // constant gap we want between every label box and the nodes it sits between
  const INJ_HALF = AP_INJECTOR_SIZE / 2;
  const EP_HALF = AP_ENDPOINT_SIZE / 2;
  const FIND_HALF = AP_FINDING_SIZE / 2;
  const maxLen = (labels: (string | undefined)[]) => labels.reduce((m, l) => Math.max(m, (l ?? '').length), 0);
  // Centre-to-centre distance so the longest of `labels` keeps LABEL_CLEARANCE from both flanking nodes.
  const segLen = (labels: (string | undefined)[], halfLeft: number, halfRight: number) =>
    Math.round(maxLen(labels) * CAPTION_CHAR_PX + LABEL_H_PAD + 2 * Math.max(halfLeft, halfRight) + 2 * LABEL_CLEARANCE);

  const findingLabel = (injId: string) => {
    const s = steps.get(injId) as ChainStep;
    return [...s.endpoints.values()].flat()
      .map(fid => findingById.get(fid)?.typeFindings)
      .filter((type): type is string => Boolean(type))
      .map(type => t('{type} found', { type }));
  };
  const causalLabels = (injId: string) =>
    (steps.get(injId) as ChainStep).consumed.map(key => causalKeyLabel(key, t));

  const sortedDepths = [...byDepth.entries()].sort((a, b) => a[0] - b[0]);
  // Per-depth geometry, accumulated left-to-right so each column takes exactly the width its own labels
  // need: inject→endpoint fits the contract name, endpoint→finding fits the "<type> found" label, and the
  // trailing gap to the next depth fits the causal label leaving this column.
  const depthEpDx = new Map<number, number>();
  const depthFindDx = new Map<number, number>();
  const depthX = new Map<number, number>();
  let xCursor = PADDING;
  for (let i = 0; i < sortedDepths.length; i++) {
    const [d, ids] = sortedDepths[i];
    const epDx = Math.max(CHAIN_EP_DX, segLen(ids.flatMap(injId => [...(steps.get(injId) as ChainStep).contractByEndpoint.values()]), INJ_HALF, EP_HALF));
    const findGap = Math.max(CHAIN_FIND_DX - CHAIN_EP_DX, segLen(ids.flatMap(findingLabel), EP_HALF, FIND_HALF));
    const findDx = epDx + findGap;
    depthEpDx.set(d, epDx);
    depthFindDx.set(d, findDx);
    depthX.set(d, xCursor);
    // Room from this column's findings to the next depth's injectors must fit the causal label there.
    const nextIds = sortedDepths[i + 1]?.[1] ?? [];
    const causalGap = Math.max(CHAIN_COL_W - CHAIN_FIND_DX, segLen(nextIds.flatMap(causalLabels), FIND_HALF, INJ_HALF));
    xCursor += findDx + causalGap;
  }

  // A finding is placed once (unique React Flow id) on the first endpoint that produced it; any other
  // endpoint that also produced it just gets an edge to the same node.
  const placedFindings = new Set<string>();

  for (const [d, ids] of sortedDepths) {
    const x = depthX.get(d) as number;
    const chainEpDx = depthEpDx.get(d) as number;
    const chainFindDx = depthFindDx.get(d) as number;
    // Merge endpoints by ASSET within this depth: injectors sharing a depth are never causally linked (a
    // dependency pushes the consumer into a deeper column), so several independent injectors hitting the
    // same asset converge on ONE endpoint node instead of one copy per injector. Aggregate, per distinct
    // asset at this depth: the injectors targeting it (first-seen order) and the union of their findings.
    const assetOrder: string[] = [];
    const assetInjectors = new Map<string, string[]>();
    const assetFindings = new Map<string, string[]>();
    ids.forEach((injId) => {
      const s = steps.get(injId) as ChainStep;
      for (const [epId, findingIds] of s.endpoints.entries()) {
        if (!assetInjectors.has(epId)) {
          assetOrder.push(epId);
          assetInjectors.set(epId, []);
          assetFindings.set(epId, []);
        }
        assetInjectors.get(epId)!.push(injId);
        const fset = assetFindings.get(epId)!;
        findingIds.forEach((fid) => {
          if (!fset.includes(fid)) {
            fset.push(fid);
          }
        });
      }
    });

    // One vertical block per distinct asset, tall enough for BOTH its stacked injectors (left) and its
    // stacked findings (right). An injector that hits several assets is positioned once (first block).
    let cursorY = PADDING;
    const injectorPlaced = new Set<string>();
    for (const epId of assetOrder) {
      const injectors = assetInjectors.get(epId) as string[];
      const findingIds = assetFindings.get(epId) as string[];
      const h = Math.max(
        CHAIN_EP_BLOCK_MIN,
        findingIds.length * CHAIN_FIND_ROW,
        injectors.length * CHAIN_INJECTOR_ROW,
      );
      const blockTop = cursorY;
      const blockCenter = blockTop + h / 2;

      const epDto = assetById.get(epId);
      // Endpoint id keyed by depth+asset (not by injector), so injectors at this depth share this node.
      const epNodeId = `chain-ep|${d}|${epId}`;
      nodes.push({
        id: epNodeId,
        type: AP_FLOW_NODE_TYPE.asset,
        position: {
          x: x + chainEpDx,
          y: blockCenter - CLUSTER_EP_HALF_H,
        },
        data: epDto ? nodeData(epDto) : { label: friendlyNodeId(epId) },
      });

      // Injectors that hit this asset, stacked within the block, each with its own labelled edge.
      injectors.forEach((injId, i) => {
        const s = steps.get(injId) as ChainStep;
        if (!injectorPlaced.has(injId)) {
          injectorPlaced.add(injId);
          const injCenterY = injectors.length === 1
            ? blockCenter
            : blockTop + (i + 0.5) * (h / injectors.length);
          const injDto = injectorById.get(injId);
          // The full graph may omit injector nodes; label the action from its contract name rather than
          // the raw step id, so the node never reads "NODE_*|<uuid>".
          const injActionLabel = [...s.contractByEndpoint.values()][0];
          nodes.push({
            id: injId,
            type: AP_FLOW_NODE_TYPE.injector,
            position: {
              x,
              y: injCenterY - CLUSTER_INJECTOR_HALF_H,
            },
            data: injDto ? nodeData(injDto) : { label: injActionLabel || friendlyNodeId(injId) },
          });
        }
        edges.push({
          id: `${injId}-${epNodeId}`,
          source: injId,
          target: epNodeId,
          type: AP_FLOW_EDGE_TYPE,
          data: {
            count: 1,
            status: epDto?.status,
            // What was launched against this endpoint (the injector contract name), so the analyst reads
            // the action on the edge rather than guessing from the injector icon alone.
            label: s.contractByEndpoint.get(epId),
          },
        });
      });

      // Findings discovered on this asset (union across the injectors), stacked and centred on the block.
      findingIds.forEach((fid, k) => {
        const fDto = findingById.get(fid);
        const fY = blockCenter + (k - (findingIds.length - 1) / 2) * CHAIN_FIND_ROW;
        if (!placedFindings.has(fid)) {
          placedFindings.add(fid);
          nodes.push({
            id: fid,
            type: AP_FLOW_NODE_TYPE.finding,
            position: {
              x: x + chainFindDx,
              y: fY - CHAIN_FIND_HALF,
            },
            data: {
              label: fDto?.value ?? fDto?.label,
              value: fDto?.value,
              typeFindings: fDto?.typeFindings,
              assetNodeId: fDto?.assetNodeId,
              status: fDto?.status ?? 'RED',
            },
          });
        }
        edges.push({
          id: `${epNodeId}-${fid}`,
          source: epNodeId,
          target: fid,
          type: AP_FLOW_EDGE_TYPE,
          data: {
            count: 1,
            status: fDto?.status ?? 'RED',
            label: fDto?.typeFindings ? t('{type} found', { type: fDto.typeFindings }) : undefined,
          },
        });
      });

      cursorY = blockTop + h + CHAIN_STEP_GAP;
    }
  }

  // Forward causal links: a produced finding → the downstream inject that consumes a matching key. When a
  // step consumes but no produced finding matches (value not surfaced, or a pure ordering dependency), fall
  // back to a dashed dependsOn edge so the sequencing is still shown.
  const findingNodes = nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.finding);
  for (const [injId, s] of steps) {
    let matched = false;
    for (const key of s.consumed) {
      for (const fn of findingNodes) {
        if (findingMatchesKey(fn, key)) {
          matched = true;
          edges.push({
            id: `${AP_FLOW_CAUSAL_EDGE_TYPE}-finding-${fn.id}-${injId}`,
            source: fn.id,
            target: injId,
            type: AP_FLOW_CAUSAL_EDGE_TYPE,
            data: {
              count: 1,
              causalKind: 'finding',
              label: causalKeyLabel(key, t),
            },
          });
        }
      }
    }
    if (!matched) {
      for (const dep of s.deps) {
        if (steps.has(dep)) {
          edges.push({
            id: `${AP_FLOW_CAUSAL_EDGE_TYPE}-depend-${dep}-${injId}`,
            source: dep,
            target: injId,
            type: AP_FLOW_CAUSAL_EDGE_TYPE,
            data: {
              count: 1,
              causalKind: 'depend',
            },
          });
        }
      }
    }
  }

  return {
    nodes,
    edges,
  };
};
