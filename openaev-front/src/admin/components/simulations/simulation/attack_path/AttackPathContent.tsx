/**
 * AttackPathContent — the canonical attack path view.
 *
 * Used by BOTH SimulationAttackPath and ScenarioAttackPath so the two pages
 * are byte-for-byte identical.  The only difference is that ScenarioAttackPath
 * wraps this component with its own simulation-picker header.
 */
import { Alert, Box } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  useAttackPathVariant,
  type AttackPathVariant,
  type AttackPathVariantType,
} from '../../../../../utils/context/AttackPathVariantContext';
import {
  type AttackPathData,
  type AttackPathDefinition,
  type AttackPathNode,
  enrichEndpointNodes,
  computeStatsFromNodes,
  getActionsForAssetFull,
} from './attackPathUtils';
import { getMockScenarioByExerciseId } from './mockAttackPathData';
import AttackPathGraph from './AttackPathGraph';
import AttackPathFeed from './AttackPathFeed';
import AttackPathStatsComponent from './AttackPathStats';
import EndpointDetailDialog from './EndpointDetailDialog';
import ActionResultPanel from './ActionResultPanel';
import FindingsDrawer, { type DrawerFilter } from './FindingsDrawer';

export const MOCK_VARIANTS: AttackPathVariant[] = [
  {
    variant_id: 'variant-1',
    variant_name: 'Variant 1 – Annotated Path Map',
    variant_type: 'v1' as AttackPathVariantType,
    variant_description: 'Flat organic layout with reason badges on each edge — shows WHY the attack moved from one endpoint to the next (e.g. "Credentials Harvested", "Port 445 (SMB)").',
  },
  {
    variant_id: 'variant-2-updated',
    variant_name: 'Variant 2 Updated – All Node Actions',
    variant_type: 'v2' as AttackPathVariantType,
    variant_description: 'Annotated Path Map (V1) + all actions that ran on each endpoint are shown as colored chip stacks directly on the node. Green = prevented, orange = detected, red = attacker succeeded.',
  },
  {
    variant_id: 'variant-2',
    variant_name: 'Variant 2 – Endpoint View',
    variant_type: 'endpoint',
    variant_description: 'Each compromised endpoint is shown as a circle. Arrows show lateral movement.',
  },
  {
    variant_id: 'variant-3',
    variant_name: 'Variant 3 – Organic Network Map',
    variant_type: 'v3' as AttackPathVariantType,
    variant_description: 'Organic zone layout with multi-path highlighting.',
  },
  {
    variant_id: 'variant-4',
    variant_name: 'Variant 4 – Attacker Origin Map',
    variant_type: 'v4u' as AttackPathVariantType,
    variant_description: 'Flat organic layout + a distinct "Attacker Machine" node outside the network showing injector actions (nmap, netexec, nuclei). Click attacker node to reveal all recon arrows. Click any endpoint to reveal the arrow targeting it.',
  },
  {
    variant_id: 'variant-4-updated',
    variant_name: 'Variant 4 – Attacker Origin Map (Updated)',
    variant_type: 'v4u2' as AttackPathVariantType,
    variant_description: 'Same as Variant 4 but recon arrows are hidden by default. Click an endpoint to see arrows targeting only that endpoint, or select an attack path from the top bar to reveal arrows for nodes in that path.',
  },
  {
    variant_id: 'variant-4-2',
    variant_name: 'Variant 4.2 – Attacker Origin Map (Always-on Links)',
    variant_type: 'v4u3' as AttackPathVariantType,
    variant_description: 'Same as Variant 4 but injector contract → endpoint recon arrows are always visible. Select an attack path to highlight path endpoints; click any endpoint to filter arrows to that target.',
  },
  {
    variant_id: 'variant-4-3',
    variant_name: 'Variant 4.3 – Intersection Sets',
    variant_type: 'v4u4' as AttackPathVariantType,
    variant_description: 'Same as Variant 4.2 but endpoints can belong to multiple finding categories simultaneously (e.g. both Credentials Found AND Open Ports). Multi-category endpoints appear in every group they belong to, with colored intersection rings.',
  },
  {
    variant_id: 'variant-4-4',
    variant_name: 'Variant 4.4 – On-demand Action Links',
    variant_type: 'v4u5' as AttackPathVariantType,
    variant_description: 'Same as Variant 4.3 but injector→endpoint arrows are hidden by default. Click any endpoint to reveal the actions that ran on it with connecting arrows. Action groups expand/collapse on click.',
  },
  {
    variant_id: 'variant-5',
    variant_name: 'Variant 5 – Organic Map + Partial Failures',
    variant_type: 'v5' as AttackPathVariantType,
    variant_description: 'Organic zone layout. Failed path segments shown as dashed gray; successful segments stay colored.',
  },
  {
    variant_id: 'variant-6',
    variant_name: 'Variant 6 – Flat Organic (No Grouping)',
    variant_type: 'v6' as AttackPathVariantType,
    variant_description: 'Flat force-directed layout with no zone/subnet grouping. Connected nodes cluster naturally.',
  },
];

interface AttackPathContentProps {
  /** The exercise/simulation UUID to load attack path data for. */
  exerciseId: string;
  /**
   * Callback fired when the user clicks a stat badge (Endpoints, Files, Credentials).
   * The caller is responsible for navigating to the correct Findings URL.
   */
  onStatClick?: (filter: 'endpoints' | 'files' | 'credentials') => void;
  /**
   * CSS height of the whole attack path area, e.g. `calc(100vh - 260px)`.
   * Defaults to `calc(100vh - 260px)`.
   */
  height?: string;
}

const AttackPathContent: FunctionComponent<AttackPathContentProps> = ({
  exerciseId,
  onStatClick,
  height = 'calc(100vh - 260px)',
}) => {
  const { t } = useFormatter();
  const { variants, selectedVariantId, setVariants, setSelectedVariantId } = useAttackPathVariant();

  const [data, setData] = useState<AttackPathData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEndpointId, setSelectedEndpointId] = useState<string | null>(null);
  const [resultPanelNode, setResultPanelNode] = useState<AttackPathNode | null>(null);

  // Findings drawer state
  const [drawerFilter, setDrawerFilter] = useState<DrawerFilter | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  /** Incremented each call to ensure useEffect in V6 always fires, even for same endpoint */
  const [externalFocusRequest, setExternalFocusRequest] = useState<{ endpointId: string; seq: number; findingId?: string } | null>(null);
  const focusSeqRef = useRef(0);
  /** V3/V5: action node IDs to highlight in feed when user selects a path */
  const [pathHighlightedActionIds, setPathHighlightedActionIds] = useState<Set<string>>(new Set());
  /** Ref so toggle-check in handlePathClick sees latest value without stale closure */
  const pathHighlightedActionIdsRef = useRef<Set<string>>(new Set());
  /** V1: ordered step number for each action node when a path is selected */
  const [pathActionOrder, setPathActionOrder] = useState<Map<string, number>>(new Map());
  /** V4U: selected attack path shown on the attacker origin map */
  const [v4uSelectedPathId, setV4uSelectedPathId] = useState<string | null>(null);

  const enrichedNodes = useMemo(
    () => (data ? enrichEndpointNodes(data.attack_path_nodes, data.attack_path_edges) : []),
    [data],
  );

  // Compute accurate finding counts from actual enriched data (overrides hardcoded mock stats)
  const computedStats = useMemo(
    () => (data?.attack_path_stats ? computeStatsFromNodes(enrichedNodes, data.attack_path_stats) : undefined),
    [enrichedNodes, data],
  );

  // Register variants into context on mount, clean up on unmount
  useEffect(() => {
    setVariants(MOCK_VARIANTS);
    setSelectedVariantId(MOCK_VARIANTS.find((v) => v.variant_type === 'v6')?.variant_id ?? MOCK_VARIANTS[0].variant_id);
    return () => setVariants([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Load data whenever exerciseId changes
  useEffect(() => {
    setLoading(true);
    setSelectedNodeId(null);
    setSelectedEndpointId(null);
    setResultPanelNode(null);
    pathHighlightedActionIdsRef.current = new Set();
    setPathHighlightedActionIds(new Set());
    setPathActionOrder(new Map());
    try {
      setData(getMockScenarioByExerciseId(exerciseId));
      setError(null);
    } catch {
      setError('Failed to load attack path data');
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [exerciseId]);

  const v4uSelectedPathIdRef = useRef<string | null>(null);
  useEffect(() => { v4uSelectedPathIdRef.current = v4uSelectedPathId; }, [v4uSelectedPathId]);

  const handleSelectNode = useCallback((nodeId: string | null) => {
    setSelectedNodeId(nodeId);
    // Don't clear path-based feed filter while a path is still selected
    if (!v4uSelectedPathIdRef.current) {
      pathHighlightedActionIdsRef.current = new Set();
      setPathHighlightedActionIds(new Set());
      setPathActionOrder(new Map());
    }
  }, []);
  const handleEndpointDetail = useCallback((nodeId: string) => setSelectedEndpointId(nodeId), []);
  const handleOpenResult = useCallback((node: AttackPathNode) => setResultPanelNode(node), []);

  /** Open the findings drawer for a specific filter type */
  const handleStatClick = useCallback((filter: DrawerFilter) => {
    setDrawerFilter(filter);
    setDrawerOpen(true);
  }, []);

  /**
   * Called when user clicks a finding item in the FindingsDrawer.
   * Focuses the action in the feed and asks V6 to expand + focus the endpoint + finding.
   */
  const handleDrawerFindingClick = useCallback((endpointId: string, actionId?: string, findingId?: string) => {
    if (actionId) handleSelectNode(actionId);
    focusSeqRef.current += 1;
    setExternalFocusRequest({ endpointId, seq: focusSeqRef.current, findingId });
  }, [handleSelectNode]);

  /**
   * Core helper: compute action IDs + primary action for a list of asset node IDs,
   * then update state. Returns true if the new set is identical to the current one (toggle signal).
   */
  const applyPathHighlight = useCallback((assetNodeIds: string[]): boolean => {
    if (!data) return false;
    const allActionIds = new Set<string>();
    const actionsByAsset: Array<{ actions: AttackPathNode[] }> = [];
    for (const assetId of assetNodeIds) {
      const asset = data.attack_path_nodes.find((n) => n.node_id === assetId);
      if (!asset) continue;
      const acts = getActionsForAssetFull(asset, data.attack_path_nodes, data.attack_path_edges);
      acts.forEach((a) => allActionIds.add(a.node_id));
      if (acts.length > 0) actionsByAsset.push({ actions: acts });
    }
    // Toggle detection: same set already active → clear instead
    const prev = pathHighlightedActionIdsRef.current;
    const isSame = prev.size === allActionIds.size && Array.from(allActionIds).every((id) => prev.has(id));
    if (isSame) {
      pathHighlightedActionIdsRef.current = new Set();
      setPathHighlightedActionIds(new Set());
      setPathActionOrder(new Map());
      setSelectedNodeId(null);
      return true;
    }
    pathHighlightedActionIdsRef.current = allActionIds;
    setPathHighlightedActionIds(allActionIds);
    // Build step-order map: each action in path order gets a unique sequential number
    const orderMap = new Map<string, number>();
    let stepNum = 1;
    for (const assetId of assetNodeIds) {
      const asset = data.attack_path_nodes.find((n) => n.node_id === assetId);
      if (!asset) continue;
      const acts = getActionsForAssetFull(asset, data.attack_path_nodes, data.attack_path_edges)
        .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));
      for (const act of acts) {
        if (allActionIds.has(act.node_id)) {
          orderMap.set(act.node_id, stepNum);
          stepNum++;
        }
      }
    }
    setPathActionOrder(orderMap);
    // Auto-expand: latest action on the last asset with actions
    const lastGroup = actionsByAsset[actionsByAsset.length - 1];
    if (lastGroup) {
      const primary = lastGroup.actions
        .slice()
        .sort((a, b) => (b.node_executed_at ?? '').localeCompare(a.node_executed_at ?? ''));
      if (primary.length > 0) setSelectedNodeId(primary[0].node_id);
    }
    return false;
  }, [data]);

  /** V5: path line clicked */
  const handlePathClick = useCallback((assetNodeIds: string[]) => {
    applyPathHighlight(assetNodeIds);
  }, [applyPathHighlight]);

  /** V1: badge clicked → focus the action for that destination asset in the feed */
  const handleBadgeSegmentClick = useCallback((destAssetId: string) => {
    if (!data) return;
    const asset = data.attack_path_nodes.find((n) => n.node_id === destAssetId);
    if (!asset) return;
    const acts = getActionsForAssetFull(asset, data.attack_path_nodes, data.attack_path_edges);
    if (acts.length > 0) {
      const primary = acts.slice().sort((a, b) => (b.node_executed_at ?? '').localeCompare(a.node_executed_at ?? ''));
      setSelectedNodeId(primary[0].node_id);
    }
  }, [data]);

  /** V3/V5: legend path widget selection → sync feed */
  const handleLegendPathSelect = useCallback((path: AttackPathDefinition | null) => {
    if (!path) {
      pathHighlightedActionIdsRef.current = new Set();
      setPathHighlightedActionIds(new Set());
      setSelectedNodeId(null);
      return;
    }
    applyPathHighlight(path.node_ids);
  }, [applyPathHighlight]);

  /** V4U4: when selectedPathId changes, sync feed highlights */
  useEffect(() => {
    if (!data) return;
    if (!v4uSelectedPathId) {
      pathHighlightedActionIdsRef.current = new Set();
      setPathHighlightedActionIds(new Set());
      setPathActionOrder(new Map());
      return;
    }
    const path = data.attack_path_definitions?.find((p) => p.path_id === v4uSelectedPathId);
    if (!path) return;
    const allActionIds = new Set<string>();
    const orderMap = new Map<string, number>();
    let stepIdx = 0;
    for (const assetId of path.node_ids) {
      const asset = data.attack_path_nodes.find((n) => n.node_id === assetId);
      if (!asset) continue;
      const acts = getActionsForAssetFull(asset, data.attack_path_nodes, data.attack_path_edges);
      acts.forEach((a) => {
        if (!allActionIds.has(a.node_id)) {
          allActionIds.add(a.node_id);
          orderMap.set(a.node_id, stepIdx++);
        }
      });
    }
    pathHighlightedActionIdsRef.current = allActionIds;
    setPathHighlightedActionIds(allActionIds);
    setPathActionOrder(orderMap);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [v4uSelectedPathId, data]);

  if (loading) return <Loader />;

  if (error) {
    return (
      <Alert severity="error" sx={{ mt: 2 }}>
        {t(error)}
      </Alert>
    );
  }

  if (!data || data.attack_path_nodes.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        {t('No attack path data available for this simulation.')}
      </Alert>
    );
  }

  const activeVariant = variants.find((v) => v.variant_id === selectedVariantId) ?? MOCK_VARIANTS[0];
  const selectedEndpointNode = selectedEndpointId
    ? (enrichedNodes.find((n) => n.node_id === selectedEndpointId) ?? null)
    : null;
  const isV6Variant = activeVariant.variant_type === 'v4u' || activeVariant.variant_type === 'v4u2' || activeVariant.variant_type === 'v4u3' || activeVariant.variant_type === 'v4u4' || activeVariant.variant_type === 'v4u5' || activeVariant.variant_type === 'v6';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height, overflow: 'hidden' }}>

      {/* Stats banner — clicks open the findings drawer inline */}
      <Box sx={{ flexShrink: 0 }}>
        <AttackPathStatsComponent
          stats={computedStats}
          onEndpointsClick={() => handleStatClick('endpoints')}
          onFilesClick={() => handleStatClick('files')}
          onCredentialsClick={() => handleStatClick('credentials')}
          onUsersClick={() => handleStatClick('users')}
          onCvesClick={() => handleStatClick('cves')}
          paths={isV6Variant ? (data.attack_path_definitions ?? []) : undefined}
          selectedPathId={isV6Variant ? v4uSelectedPathId : undefined}
          onPathSelect={isV6Variant ? setV4uSelectedPathId : undefined}
        />
      </Box>

      {/* Feed (left) + Graph (center) */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden', position: 'relative' }}>
        <AttackPathFeed
          nodes={data.attack_path_nodes}
          selectedNodeId={selectedNodeId}
          onSelectNode={(id) => { handleSelectNode(id); if (!v4uSelectedPathIdRef.current) setPathHighlightedActionIds(new Set()); }}
          onOpenResult={handleOpenResult}
          highlightedActionIds={pathHighlightedActionIds}
          pathActionOrder={pathActionOrder}
        />
        <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
          <AttackPathGraph
            nodes={data.attack_path_nodes}
            edges={data.attack_path_edges}
            selectedNodeId={selectedNodeId}
            onSelectNode={handleSelectNode}
            onEndpointDetail={handleEndpointDetail}
            onPathClick={(activeVariant.variant_type === 'v1' || activeVariant.variant_type === 'v5' || activeVariant.variant_type === 'v6') ? handlePathClick : undefined}
            onBadgeClick={activeVariant.variant_type === 'v1' ? handleBadgeSegmentClick : undefined}
            onLegendPathSelect={handleLegendPathSelect}
            variantType={activeVariant.variant_type}
            attackPathDefinitions={data.attack_path_definitions}
            selectedPathId={isV6Variant ? v4uSelectedPathId : undefined}
            externalFocusRequest={externalFocusRequest}
          />
          <ActionResultPanel
            node={resultPanelNode}
            allNodes={data.attack_path_nodes}
            onClose={() => setResultPanelNode(null)}
          />
        </div>
      </div>

      {/* Findings Drawer — slides in from the right, rendered via Portal */}
      <FindingsDrawer
        open={drawerOpen}
        filterType={drawerFilter}
        nodes={data.attack_path_nodes}
        edges={data.attack_path_edges}
        onClose={() => setDrawerOpen(false)}
        onFindingClick={handleDrawerFindingClick}
      />

      {/* Endpoint detail dialog (Variant-2 circle clicks) */}
      <EndpointDetailDialog
        node={selectedEndpointNode}
        open={!!selectedEndpointNode}
        onClose={() => setSelectedEndpointId(null)}
        allNodes={enrichedNodes}
        allEdges={data.attack_path_edges}
      />
    </div>
  );
};

export default AttackPathContent;
