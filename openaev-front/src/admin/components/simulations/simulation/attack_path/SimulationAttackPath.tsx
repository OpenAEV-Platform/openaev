import { AccountTreeOutlined, BugReportOutlined, GroupOutlined, InsertDriveFileOutlined, LabelOutlined, PlayArrowOutlined, TrackChangesOutlined, VpnKeyOutlined } from '@mui/icons-material';
import { Alert, Box, Button, GlobalStyles, Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { FolderNetworkOutline } from 'mdi-material-ui';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchAttackPathSimulations, fetchEndpointFindings, fetchEndpointRelations, fetchExecutionDetail, fetchFindingsByCategory, fetchSimulationsMetaById } from '../../../../../actions/attack-path/attack-path-actions';
import { createRunningExerciseFromScenario } from '../../../../../actions/scenarios/scenario-actions';
import EmptyPlaceholder from '../../../../../components/common/EmptyPlaceholder';
import { criticalityColor } from '../../../../../components/criticalityColor';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import ScoreExplainerDialog, { type ScoreBreakdownRow } from '../../../../../components/ScoreExplainerDialog';
import { SIMULATION_BASE_URL } from '../../../../../constants/BaseUrls';
import type { AttackPathEdges, AttackPathExecutionDetailDTO, AttackPathFindingItemDTO, AttackPathFindingPageDTO, AttackPathNodeDTO, AttackPathSimSummaryRow, ExerciseSimple } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import useRemainingViewportHeight from '../../../../../utils/hooks/useRemainingViewportHeight';
import attackPathStatusColor from './attack-path-colors';
import { AP_ALL_ENDPOINTS, AP_FLOW_CAUSAL_EDGE_TYPE, AP_FLOW_NODE_TYPE, AP_SHARED_EP_CLUSTER_ID, applyFindingFilter, type AttackPathFindingFilter, type AttackPathFlowEdge, type AttackPathFlowNode, buildCausalChainFlow, buildCausalEdges, buildClusteredAttackPathFlow, buildFindingPathFlow, buildKillChainMeta, ENDPOINT_BATCH_SIZE, FILTER_TO_FINDING_TYPES, FINDING_BATCH_SIZE, findingCategoryNoun, friendlyNodeId, maskFindingValue, orderSimulationPickerOptions, type PathFinding, pivotEndpointIds, scopeChainFlowToEndpoint, scopeChainFlowToSeeds } from './attack-path-flow-helpers';
import { AP_GLOBAL_STYLES, AP_PANEL_DEFAULT_WIDTH, AP_PANEL_MAX_WIDTH, AP_PANEL_MIN_WIDTH, AP_VIEW_HEIGHT, AP_VISUALLY_HIDDEN } from './attack-path-styles';
import AttackPathHeader, { type FindingCard, type SearchOption } from './AttackPathHeader';
import AttackPathLegend from './AttackPathLegend';
import AttackPathTableView, { type AttackPathEndpointRow } from './AttackPathTableView';
import AttackPathCanvas, { type AttackPathAnchorRequest, type AttackPathFocusRequest, type AttackPathPursuitRequest } from './canvas/AttackPathCanvas';
import CategoryFindingsPanel from './CategoryFindingsPanel';
import EndpointDetailPanel from './EndpointDetailPanel';
import ExecutionResultTerminalPanel from './ExecutionResultTerminalPanel';
import FindingDetailPanel, { type ExpectationVerdict, type FindingExpectations, type ProducingAction } from './FindingDetailPanel';
import useAttackPathLiveGraph from './useAttackPathLiveGraph';

// A hot endpoint can have many executions; the read is bounded to the one endpoint, but the side
// panel still renders a list, so cap it (the backend /relations read would be paginated in prod).
// How many executions a panel reveals at once; the rest are one "Show more" away, so a hot endpoint's
// list is bounded on screen without ever hiding data (spec 003, FR9/FR11).
const EXEC_PAGE_SIZE = 50;
// The injector panel reads each reached endpoint's relations to scope the injector's own executions;
// it needs the whole set rather than a feed page, and the server caps this at 200 anyway.
const INJECTOR_RELATIONS_PAGE_SIZE = 200;

// The causal overlay needs the per-execution kill-chain fields, which the backend only emits in the
// full graph. We fetch that full graph solely to derive the meta, gated on the run's execution count so
// a large simulation never downloads a full payload for the overlay. Mirrors the backend
// `openaev.attackpath.collapse-threshold` (20000): at or below it, a full read is affordable.
const CAUSAL_META_MAX_EXECUTIONS = 20000;

// Findings drawer: rows shown per page (client-side paginated over the loaded category page).
const DRAWER_PAGE_SIZE = 12;
// The findings drawer loads the whole category once (bounded), then filters/paginates client-side so
// the search box and pager cover every item, not just the first backend page.
const DRAWER_FETCH_SIZE = 1000;

// Expanding a finding cluster fetches findings from at most this many of the injector's endpoints and
// de-duplicates them — a bounded, front-only stand-in until a "findings by type" endpoint exists.
const FINDING_FETCH_ENDPOINTS = 30;

// How many top-exposed endpoints are surfaced as chokepoints (badged on the map + listed in the card).
const CHOKEPOINT_TOP_N = 5;

// During a live run, the first structural deltas re-frame the whole graph at most this many times
// ("unzoom 1-2 times max"); after that the camera switches to pursuit and follows the newest nodes.
const AP_MAX_LIVE_FITS = 2;

// Chokepoint score weights an endpoint's finding count by its business criticality, so the top
// chokepoint is "the most findings on the most critical endpoint" (not raw finding count alone). The
// weights are deliberately simple and transparent (surfaced in the card's explanation): a VERY_HIGH
// asset counts 4x a LOW one; an asset with no criticality set counts as LOW (weight 1), never zero, so
// it is still ranked. Kept ordered high→low for the legend.
const CRITICALITY_WEIGHT: Record<string, number> = {
  VERY_HIGH: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
  UNKNOWN: 1,
};
const criticalityWeight = (criticality?: string): number => CRITICALITY_WEIGHT[criticality ?? ''] ?? 1;
// Human label for a criticality value (falls back to "Unknown" / "Not set").
const CRITICALITY_LABEL: Record<string, string> = {
  VERY_HIGH: 'Very high',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  UNKNOWN: 'Unknown',
};

// Synthetic seeded simulations (POST /attack-path/seed) carry no real date/name; keep them hidden
// from metadata resolution and fall back to their raw id in the picker.
const isSeedId = (id?: string) => !!id && id.startsWith('ap-seed-');

// Finding types already surfaced by the curated summary cards (endpoints/shares/credentials/users/cves).
// Every OTHER type present in the data gets an auto-generated card, so a new finding type needs no code.
const COVERED_FINDING_TYPES = new Set(['share', 'file', 'credentials', 'username', 'admin_username', 'cve']);

// Finding categories fetched (with per-finding executionIds) to attribute findings to an injector.
const INJECTOR_FINDING_CATEGORIES = ['credentials', 'users', 'cves', 'shares', 'files'];

// Backend prevention/detection status -> a human label (also used for accessibility, so status is
// never conveyed by colour alone). GREEN = prevented, ORANGE = detected, RED = neither.
const statusLabelKey = (status?: string): string => {
  switch (status) {
    case 'GREEN':
      return 'Prevented';
    case 'ORANGE':
      return 'Detected';
    case 'RED':
      return 'Undetected';
    default:
      return 'Unknown';
  }
};

// Friendly contract labels for known seed step templates (fallbacks for POC datasets where the
// backend payloadName is synthetic like "nmap-payload").
const STEP_TEMPLATE_CONTRACT_LABEL: Record<string, string> = {
  'step-tpl-nmap': 'NMAP TCP Scan',
  'step-tpl-nuclei': 'Nuclei CVE Scan',
  'step-tpl-crackmapexec': 'Netexec SMB Scan',
  'step-tpl-impacket': 'Netexec SMB Scan',
};

const toContractLabel = (execution: AttackPathNodeDTO): string | undefined => {
  // The real contract name resolved by the backend wins over any heuristic.
  if (execution.contractName) {
    return execution.contractName;
  }
  if (execution.stepTemplateId && STEP_TEMPLATE_CONTRACT_LABEL[execution.stepTemplateId]) {
    return STEP_TEMPLATE_CONTRACT_LABEL[execution.stepTemplateId];
  }
  const raw = execution.payloadName || execution.label;
  if (!raw) {
    return undefined;
  }
  // Generic fallback for synthetic "*-payload" names.
  if (raw.endsWith('-payload')) {
    const base = raw.slice(0, -8).replace(/[_-]+/g, ' ').trim();
    if (!base) {
      return raw;
    }
    return base
      .split(' ')
      .map(w => (w.length <= 4 ? w.toUpperCase() : `${w[0].toUpperCase()}${w.slice(1)}`))
      .join(' ');
  }
  return raw;
};

// A finding type -> the drawer category that lists it (with per-finding executionIds). Lets a finding
// clicked directly in the graph resolve its producing actions the same way a drawer item does.
const CATEGORY_OF_TYPE: Record<string, string> = {
  credentials: 'credentials',
  username: 'users',
  admin_username: 'users',
  cve: 'cves',
  share: 'shares',
  file: 'files',
};

// Match a drawer finding value to a graph finding value. Credentials are masked server-side in the
// drawer ("user:••••") but raw in the graph ("user:pass"), so compare only the username before the
// separator; other types compare exactly.
const findingValuesMatch = (type: string, a: string, b: string): boolean => {
  if (a === b) {
    return true;
  }
  if (type === 'credentials') {
    const ua = a.split(/[:\s]/)[0];
    const ub = b.split(/[:\s]/)[0];
    return !!ua && ua === ub;
  }
  return false;
};

/**
 * Attack-path tab (issue 6647), gated by the ATTACK_PATH preview feature. Renders the simulation as a
 * clustered graph: each injector fans out to an aggregate endpoint dot (+N) and one cluster per
 * finding type (with counts), all derived from the collapsed graph — no extra reads. An injector can
 * be expanded into its real endpoints, and the five summary cards open a right drawer (backend
 * findings list) that cross-focuses the graph and the execution feed. Clicking a feed entry opens the
 * execution Result & Terminal panel.
 */
interface SimulationAttackPathProps {
  /**
   * Scenario context: the ids of the scenario's simulations. When provided, the simulation picker is
   * shown but restricted to these runs (a scenario groups several simulations) and defaults to the most
   * recent one. When omitted (simulation context) the picker is hidden and the view is locked to the
   * route's exerciseId — the current simulation only.
   */
  scenarioExerciseIds?: string[];
  /** Scenario context: the scenario id, used by the empty-state "Launch a simulation" CTA. */
  scenarioId?: string;
  /**
   * Autonomous scenario context: an autonomous run owns exactly one simulation and is never launched
   * by hand (the AI drives it; the operator restarts from the hero), so the empty-state "Launch a
   * simulation" CTA is suppressed and the message points at the live run instead.
   */
  hideLaunchCta?: boolean;
  /**
   * Render the causal chain as an ACTION TIMELINE: endpoint-local actions (recon, dumps, local
   * escalation on the host the agent already owns) render as their own action nodes instead of being
   * folded into the endpoint they ran on. Enabled for autonomous runs, whose engagement is almost
   * entirely local steps on a single compromised host — without this the graph looks frozen after the
   * first finding even though every step executed. Off for manual BAS runs (the finding-centric view).
   */
  actionCentric?: boolean;
}

const SimulationAttackPath = ({ scenarioExerciseIds, scenarioId, hideLaunchCta = false, actionCentric = false }: SimulationAttackPathProps) => {
  const { exerciseId } = useParams() as { exerciseId?: string };
  // Scenario context lists several runs to pick from; simulation context is locked to its own run.
  const showPicker = scenarioExerciseIds !== undefined;
  const theme = useTheme();
  const { t, fldt, cnsdt } = useFormatter();
  const navigate = useNavigate();

  // The view sizes itself to the exact space left under the page chrome (no page scrollbar).
  const [rootRef, viewHeight] = useRemainingViewportHeight();

  const [simulationId, setSimulationId] = useState(exerciseId ?? '');
  const [simulations, setSimulations] = useState<AttackPathSimSummaryRow[]>([]);
  const [metaById, setMetaById] = useState<Map<string, ExerciseSimple>>(new Map());
  // The selected run's status, from the picker metadata. A finished/canceled run produces nothing more,
  // so live updating stops there; an unknown status (synthetic seed simulations) is treated as live.
  const selectedRunStatus = metaById.get(simulationId)?.exercise_status;
  const runTerminal = selectedRunStatus === 'FINISHED' || selectedRunStatus === 'CANCELED';
  // The causal overlay needs the per-execution kill-chain fields, which only the full graph carries, so
  // it is seeded only under the size ceiling (mirrors the backend collapse-threshold) — a large run never
  // downloads a full payload. The gate uses the initial summary-row count; a run that grows past the
  // ceiling mid-view keeps its overlay until reselect.
  const fullEligible = useMemo(() => {
    const row = simulations.find(s => s.simulationId === simulationId);
    // A run ABSENT from the summary list is the smallest possible run, not an ineligible one: that
    // list is fetched once at mount and only contains simulations that ALREADY have attack-path
    // rows. Requiring the row meant a view opened before the first execution landed never fetched
    // the full graph — and since the list never refreshes, it stayed aggregated for the whole
    // session, so the causal chain only appeared after a page reload.
    return (row?.executionCount ?? 0) <= CAUSAL_META_MAX_EXECUTIONS;
  }, [simulations, simulationId]);

  // One accumulated graph, live (issue 6647): a snapshot then versioned deltas on a single 3 s poll,
  // serving BOTH render modes. `dto` (collapsed clustered) and `fullDto` (full causal chain) are derived
  // projections of that store, so every existing consumer keeps its shape — and a delta commit touches
  // graph data only, never the selection/expansion/drawer state below.
  const {
    dto,
    fullDto,
    loading,
    error,
    forbidden,
    freshness,
    lastUpdatedAt,
    newNodeIds,
    newNodes,
    changedFindingTypes,
    structuralNonce,
    fullPending,
  } = useAttackPathLiveGraph({
    simulationId,
    fullEligible,
    terminal: runTerminal,
  });
  // Render the causal execution-chain layout whenever the size-gated full graph is available and carries
  // executions (small runs). Large runs never fetch it (fullDto stays null) and keep the aggregated view.
  // Declared early (not next to its other consumers below) because click handlers defined above those
  // need it in their dependency arrays, evaluated at render time.
  const chainMode = !!fullDto && (fullDto.attackPathExecutions?.length ?? 0) > 0;
  // Per-injector kill-chain metadata (dependsOn / consumedFindingKeys) for the causal overlay, derived
  // from the full projection. It describes the graph's SHAPE, so it is rebuilt only when the shape
  // moved: `structuralNonce` bumps on a seed and on any delta that introduced an id, never on an
  // attribute-only tick (a verdict flipping), which would otherwise re-walk every execution — and
  // through `causalEdges`, relay out the overlay — for data the overlay does not even read.
  const killChainCache = useRef<{
    nonce: number;
    meta: ReturnType<typeof buildKillChainMeta>;
  }>({
    nonce: -1,
    meta: new Map(),
  });
  const killChainMeta = useMemo<ReturnType<typeof buildKillChainMeta>>(
    () => {
      if (killChainCache.current.nonce !== structuralNonce) {
        killChainCache.current = {
          nonce: structuralNonce,
          meta: fullDto ? buildKillChainMeta(fullDto) : new Map(),
        };
      }
      return killChainCache.current.meta;
    },
    [fullDto, structuralNonce],
  );
  // Card focus: a summary card mapped to its finding types (dim everything off that path).
  const [activeCard, setActiveCard] = useState<AttackPathFindingFilter | null>(null);

  // Per-injector progressive endpoint reveal: injector id -> number of endpoints shown (0 = collapsed).
  const [endpointBatch, setEndpointBatch] = useState<Map<string, number>>(new Map());
  // Finding-cluster drill-down: which finding clusters are expanded, their fetched (deduped) findings,
  // and how many are revealed (batched), keyed by the finding-cluster node id.
  const [expandedFindingClusters, setExpandedFindingClusters] = useState<Set<string>>(new Set());
  // Causal-chain view: per-depth "+N endpoints" overflow cluster id -> how many hidden endpoints the
  // user revealed beyond the always-shown cap, batched by ENDPOINT_BATCH_SIZE per click.
  const [endpointClusterBatch, setEndpointClusterBatch] = useState<Map<string, number>>(new Map());
  const [findingsByCluster, setFindingsByCluster] = useState<Map<string, AttackPathNodeDTO[]>>(new Map());
  const [findingBatch, setFindingBatch] = useState<Map<string, number>>(new Map());
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedLabel, setSelectedLabel] = useState<string>('');
  // A clicked leaf finding whose full path (injector -> endpoint cluster -> finding cluster -> finding)
  // is highlighted in blue.
  const [selectedFindingId, setSelectedFindingId] = useState<string | null>(null);
  // In the focused finding-path view, an injector clicked to reverse-highlight its downstream path
  // (injector -> endpoint -> the findings it produced). Mutually exclusive with a finding highlight.
  const [selectedInjectorId, setSelectedInjectorId] = useState<string | null>(null);
  // Finding picked in the focused graph, driving the right-side finding details panel (its info +
  // producing actions that open Result & Terminal). Null = no finding panel.
  const [findingDetail, setFindingDetail] = useState<{
    type: string;
    value: string;
    // The endpoint node this finding was discovered on. In chain mode there is no `pathFinding`/
    // `focusedEndpoint` to fall back on, so we carry the origin endpoint here to resolve the panel's
    // "Discovered on" label (else it degraded to the literal word "Endpoint").
    endpointNodeId?: string;
  } | null>(null);
  const [executions, setExecutions] = useState<AttackPathNodeDTO[]>([]);
  // The clicked injector's own executions (its contracts across every endpoint it reached), listed in
  // the injector panel — the action-side mirror of the endpoint panel. Label = the injector's name.
  const [injectorExecutions, setInjectorExecutions] = useState<AttackPathNodeDTO[]>([]);
  // How many executions this injector ran in total; the list can hold fewer when a page cut in.
  const [injectorExecTotal, setInjectorExecTotal] = useState(0);
  const [injectorPanelLabel, setInjectorPanelLabel] = useState<string>('');
  // The clicked injector's findings, grouped by type — attributed to THIS injector (findings produced by
  // its own executions), shown in the injector panel's Findings section like an endpoint's.
  const [injectorFindingGroups, setInjectorFindingGroups] = useState<{
    type: string;
    values: string[];
  }[]>([]);
  const [injectorFindingsLoading, setInjectorFindingsLoading] = useState(false);
  const [endpointRelationEdges, setEndpointRelationEdges] = useState<AttackPathEdges[]>([]);
  // The selected endpoint's key, and how many executions target it in total: the panel holds one page
  // and asks for the next, so it needs the total to know whether there is one.
  const [selectedEndpointRef, setSelectedEndpointRef] = useState<string | null>(null);
  const [endpointExecTotal, setEndpointExecTotal] = useState(0);
  const [endpointExecLoadingMore, setEndpointExecLoadingMore] = useState(false);
  // The clicked endpoint's own findings (deduplicated by type+value), shown in the side panel.
  const [endpointFindings, setEndpointFindings] = useState<AttackPathNodeDTO[]>([]);
  const [endpointFindingsLoading, setEndpointFindingsLoading] = useState(false);

  // Findings drawer: a summary card opens a right drawer listing that category's findings (issue 5048).
  const [drawerCategory, setDrawerCategory] = useState<string | null>(null);
  const [drawerLabel, setDrawerLabel] = useState<string>('');
  const [findingsPage, setFindingsPage] = useState<AttackPathFindingPageDTO | null>(null);
  const [findingsLoading, setFindingsLoading] = useState(false);
  // Drawer client-side search + pagination over the loaded category page.
  const [drawerSearch, setDrawerSearch] = useState('');
  const [drawerPage, setDrawerPage] = useState(0);

  // Cross-focus: clicking a finding item centers its endpoint (focusRequest) and highlights the
  // producing executions in the feed (by their raw ids).
  const [focusRequest, setFocusRequest] = useState<AttackPathFocusRequest | null>(null);
  // Monotonic, so re-picking the SAME finding re-frames it instead of being swallowed as a no-op.
  const focusNonce = useRef(0);
  const [highlightedExecutionIds, setHighlightedExecutionIds] = useState<Set<string>>(new Set());
  const feedRowRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // Focused "attack path to this finding" view: when set, the graph shows only the injector(s) ->
  // endpoint -> finding path that produced the finding picked in the drawer. fitNonce bumps to frame it.
  const [pathFinding, setPathFinding] = useState<PathFinding | null>(null);
  const [fitNonce, setFitNonce] = useState(0);
  // Expanding a cluster holds the view on the clicked node: the canvas's growth-driven fit would
  // otherwise re-frame the whole graph and throw the user back to its entrance.
  const [anchorRequest, setAnchorRequest] = useState<AttackPathAnchorRequest | null>(null);
  const anchorOnNode = useCallback((nodeId: string) => {
    setAnchorRequest(prev => ({
      nodeId,
      nonce: (prev?.nonce ?? 0) + 1,
    }));
  }, []);
  // Bumped whenever a side panel/drawer opens so the graph legend folds away (reopenable by the user).
  const [legendCollapseNonce, setLegendCollapseNonce] = useState(0);

  // Live growth reframe with a pursuit mode: the first couple of structural deltas still re-frame the
  // whole graph (the run "unzooms" at most AP_MAX_LIVE_FITS times, settling on a readable zoom), after
  // which the camera stops pulling back and instead CHASES the newest nodes at the current zoom — the
  // real-time drawing stays centered on the action, and the fit control keeps offering the big
  // picture. Keyed on structuralNonce, so an attribute-only tick (a verdict flip) never moves the
  // camera; the focused finding-path view frames itself through its own bumps.
  const lastStructuralFit = useRef(0);
  const liveFitCount = useRef(0);
  const [pursuitRequest, setPursuitRequest] = useState<AttackPathPursuitRequest | null>(null);
  const [pursuitActive, setPursuitActive] = useState(false);
  useEffect(() => {
    // A different run starts its own framing budget from scratch.
    liveFitCount.current = 0;
    setPursuitActive(false);
    setPursuitRequest(null);
  }, [simulationId]);
  useEffect(() => {
    if (structuralNonce > 0 && structuralNonce !== lastStructuralFit.current && !pathFinding) {
      lastStructuralFit.current = structuralNonce;
      if (pursuitActive && newNodeIds.length > 0) {
        setPursuitRequest({
          nodeIds: newNodeIds,
          nonce: structuralNonce,
        });
      } else {
        setFitNonce(n => n + 1);
        // Only LIVE growth spends the framing budget. The initial load also bumps the structural
        // nonce — once for the collapsed seed, once for the causal overlay merged in after it — and
        // those carry no batch. Counting them exhausted the budget before the first delta ever
        // arrived, so pursuit engaged immediately and every growth re-fit was suppressed for the
        // rest of the run (the graph then drew itself off-screen until the user panned by hand).
        // Only a run still producing deltas graduates to pursuit; a terminal run loads once and fits.
        if (newNodeIds.length > 0) {
          liveFitCount.current += 1;
          if (!runTerminal && liveFitCount.current >= AP_MAX_LIVE_FITS) {
            setPursuitActive(true);
          }
        }
      }
    }
  }, [structuralNonce, pathFinding, pursuitActive, newNodeIds, runTerminal]);

  // The finding details panel only lives inside the focused view; close it whenever the focus ends.
  useEffect(() => {
    if (!pathFinding) {
      setFindingDetail(null);
    }
  }, [pathFinding]);

  // Execution Result & Terminal drawer: clicking a feed entry loads and opens its detail.
  const [detailExecutionId, setDetailExecutionId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AttackPathExecutionDetailDTO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Monotonic token to drop stale async responses when the user switches simulation or endpoint
  // quickly: only the latest request may write state.
  const endpointSeq = useRef(0);

  // Drawer width, drag-resizable (the graph is flex:1 and reflows as this changes). Dragging the handle
  // on the drawer's left edge leftwards widens it — useful when execution traces overflow.
  const [panelWidth, setPanelWidth] = useState(AP_PANEL_DEFAULT_WIDTH);
  const resizeRef = useRef<{
    startX: number;
    startW: number;
  } | null>(null);
  const onResizeMove = useCallback((e: MouseEvent) => {
    if (!resizeRef.current) {
      return;
    }
    const next = resizeRef.current.startW + (resizeRef.current.startX - e.clientX);
    setPanelWidth(Math.max(AP_PANEL_MIN_WIDTH, Math.min(AP_PANEL_MAX_WIDTH, next)));
  }, []);
  const onResizeEnd = useCallback(() => {
    resizeRef.current = null;
    document.removeEventListener('mousemove', onResizeMove);
    document.removeEventListener('mouseup', onResizeEnd);
    document.body.style.userSelect = '';
  }, [onResizeMove]);
  const onResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    resizeRef.current = {
      startX: e.clientX,
      startW: panelWidth,
    };
    document.addEventListener('mousemove', onResizeMove);
    document.addEventListener('mouseup', onResizeEnd);
    // Suppress text selection while dragging.
    document.body.style.userSelect = 'none';
  }, [panelWidth, onResizeMove, onResizeEnd]);

  // Hard reset on simulation change: drop everything the user had opened/expanded for the previous run
  // and re-read its graph from scratch. Deliberately NOT what a delta does — a live update keeps every
  // one of these (FR8); only switching simulations (or an explicit reload) wipes them.
  const load = useCallback(() => {
    if (!simulationId) {
      return;
    }
    // A simulation switch also invalidates any in-flight endpoint read from the previous graph.
    endpointSeq.current += 1;
    setEndpointBatch(new Map());
    setExpandedFindingClusters(new Set());
    setEndpointClusterBatch(new Map());
    setFindingsByCluster(new Map());
    setFindingBatch(new Map());
    setSelectedNodeId(null);
    setSelectedFindingId(null);
    setSelectedInjectorId(null);
    setExecutions([]);
    setEndpointRelationEdges([]);
    setEndpointFindings([]);
    setActiveCard(null);
    // Close the drawers and clear any cross-focus so nothing carries over between simulations.
    setDrawerCategory(null);
    setDetailExecutionId(null);
    setHighlightedExecutionIds(new Set());
    setFocusRequest(null);
    setPathFinding(null);
  }, [simulationId]);

  useEffect(() => {
    load();
  }, [load]);

  // Load the picker options once (simulations that have attack-path data in this tenant), then
  // resolve real simulations' date + name so the picker reads dates instead of raw ids. In scenario
  // context the list is narrowed to the scenario's own runs and the view defaults to the most recent.
  useEffect(() => {
    fetchAttackPathSimulations()
      .then((r) => {
        const all = r.data ?? [];
        // Scenario context: this scenario's runs. We must include EVERY scenario simulation as a
        // selectable option - even one with no attack-path data yet (a fresh / autonomous run starts
        // with 0 injects and fills the graph in live) - otherwise the picker shows "No options",
        // auto-select never fires, and the live graph is never mounted so it can never start drawing.
        // Simulation context: every run in the tenant (picker hidden; this is just the summary source).
        let rows: AttackPathSimSummaryRow[];
        if (scenarioExerciseIds) {
          const withData = all.filter(
            s => !!s.simulationId && scenarioExerciseIds.includes(s.simulationId),
          );
          const haveIds = new Set(withData.map(s => s.simulationId));
          const withoutData: AttackPathSimSummaryRow[] = scenarioExerciseIds
            .filter(id => !haveIds.has(id))
            .map(id => ({ simulationId: id }));
          rows = [...withData, ...withoutData];
        } else {
          rows = all;
        }
        setSimulations(rows);
        // Scenario context has no route exerciseId, so pick a default run: the most recent by start
        // date once meta resolves, falling back to the first available run if meta is unavailable so
        // the view is never stuck empty. `prev || …` never overrides a run the user has picked.
        const seedScenarioDefault = (simId?: string) => {
          if (showPicker && simId) {
            setSimulationId(prev => prev || simId);
          }
        };
        const ids = Array.from(new Set([
          ...rows.map(s => s.simulationId).filter((id): id is string => !isSeedId(id) && !!id),
          ...(!isSeedId(exerciseId) && exerciseId ? [exerciseId] : []),
        ]));
        if (ids.length > 0) {
          fetchSimulationsMetaById(ids)
            .then((m) => {
              const metaMap = new Map((m.data ?? []).map(e => [e.exercise_id, e]));
              setMetaById(metaMap);
              const mostRecent = [...rows].sort((a, b) => {
                const da = metaMap.get(a.simulationId ?? '')?.exercise_start_date ?? '';
                const db = metaMap.get(b.simulationId ?? '')?.exercise_start_date ?? '';
                return db.localeCompare(da);
              })[0];
              seedScenarioDefault(mostRecent?.simulationId);
            })
            .catch(() => seedScenarioDefault(rows[0]?.simulationId));
        } else {
          seedScenarioDefault(rows[0]?.simulationId);
        }
      })
      .catch(() => setSimulations([]));
  }, [exerciseId, scenarioExerciseIds, showPicker]);

  // Readable label for a simulation id: "date · name" for real simulations, raw id for seeds/unknowns.
  // The date alone identifies the run here: the simulation's name is already on the page header above
  // the tabs, so repeating it only widened the picker. Compact numeric form (05/08/26 16:06 in fr,
  // locale-adapted elsewhere) rather than the long prose one ("August 5, 2026 at 4:06:00 PM"). A run
  // with no start date has nothing to show but its name, so that case still falls back to it.
  const labelFor = useCallback((simId?: string): string => {
    if (!simId) {
      return '';
    }
    const meta = metaById.get(simId);
    if (meta?.exercise_start_date) {
      return cnsdt(meta.exercise_start_date);
    }
    return meta?.exercise_name || simId;
  }, [metaById, cnsdt]);

  // Click a real endpoint (only visible once its injector cluster is expanded): load its own findings
  // (grouped in the side panel) and its executions. Stale responses are dropped.
  //
  // `openPanel: false` loads the endpoint's data WITHOUT selecting it, so the side panel stays closed.
  // A focus request (table row, chokepoint, search pick, finding pick) wants the whole width for the
  // graph it just focused — opening the panel on top of it immediately narrows the view the click was
  // meant to reveal. The data is still fetched: the focused layout labels its injector edges from those
  // relations, and the panel is one node click away once the analyst wants the detail.
  const onEndpointClick = useCallback((nodeId: string, ref?: string, label?: string, opts?: { openPanel?: boolean }) => {
    // On a focus request, CLOSE any panel a previous node click left open (not just skip opening one):
    // a stale panel would keep covering the freshly focused graph, showing the new endpoint's data
    // under the old node's selection highlight.
    setSelectedNodeId(opts?.openPanel !== false ? nodeId : null);
    setSelectedFindingId(null);
    setSelectedInjectorId(null);
    setFindingDetail(null);
    setSelectedLabel(label ?? '');
    setDetailExecutionId(null);
    setDetail(null);
    setLegendCollapseNonce(n => n + 1);
    setExecutions([]);
    setEndpointRelationEdges([]);
    setEndpointFindings([]);
    setEndpointExecTotal(0);
    setSelectedEndpointRef(ref ?? null);
    // A plain node click focuses no specific execution; a finding-item click sets these after.
    setHighlightedExecutionIds(new Set());
    if (!ref) {
      return;
    }
    const seq = endpointSeq.current + 1;
    endpointSeq.current = seq;
    setEndpointFindingsLoading(true);
    fetchEndpointFindings(simulationId, ref)
      .then((r) => {
        if (seq !== endpointSeq.current) {
          return;
        }
        // De-duplicate the endpoint's findings by (type, value) so a value found by several
        // executions is listed once.
        const seen = new Set<string>();
        const deduped: AttackPathNodeDTO[] = [];
        for (const f of r.data.findings ?? []) {
          const key = `${f.typeFindings ?? ''}|${f.value ?? f.label ?? ''}`;
          if (!seen.has(key)) {
            seen.add(key);
            deduped.push(f);
          }
        }
        setEndpointFindings(deduped);
      })
      .catch(() => {
        if (seq === endpointSeq.current) {
          setEndpointFindings([]);
        }
      })
      .finally(() => {
        if (seq === endpointSeq.current) {
          setEndpointFindingsLoading(false);
        }
      });
    // First page only: a hot endpoint can carry thousands of executions, and the panel reveals them
    // a page at a time. The edges come back whole, so the graph side is complete on this one read.
    fetchEndpointRelations(simulationId, ref, 0, EXEC_PAGE_SIZE)
      .then((r) => {
        if (seq === endpointSeq.current) {
          setExecutions(r.data.executions ?? []);
          setEndpointRelationEdges(r.data.edges ?? []);
          setEndpointExecTotal(r.data.totalExecutions ?? (r.data.executions ?? []).length);
        }
      })
      .catch(() => {
        if (seq === endpointSeq.current) {
          setExecutions([]);
          setEndpointRelationEdges([]);
          setEndpointExecTotal(0);
        }
      });
  }, [simulationId]);

  /**
   * Appends the next page of the selected endpoint's executions. Guarded against overlap so a double
   * click cannot fetch the same page twice, and it dedupes by id: a live merge may already have
   * inserted a row this page also carries.
   */
  const loadMoreEndpointExecutions = useCallback(() => {
    if (!simulationId || !selectedEndpointRef || endpointExecLoadingMore) {
      return;
    }
    const seq = endpointSeq.current;
    setEndpointExecLoadingMore(true);
    const nextPage = Math.floor(executions.length / EXEC_PAGE_SIZE);
    fetchEndpointRelations(simulationId, selectedEndpointRef, nextPage, EXEC_PAGE_SIZE)
      .then((r) => {
        if (seq !== endpointSeq.current) {
          return;
        }
        setExecutions((current) => {
          const seen = new Set(current.map(e => e.id));
          return [...current, ...(r.data.executions ?? []).filter(e => !seen.has(e.id))];
        });
        setEndpointExecTotal(r.data.totalExecutions ?? executions.length);
      })
      .finally(() => {
        if (seq === endpointSeq.current) {
          setEndpointExecLoadingMore(false);
        }
      });
  }, [simulationId, selectedEndpointRef, endpointExecLoadingMore, executions]);

  // Focused view: expand the endpoint's finding clusters BY DEFAULT so the analyst sees the actual findings
  // without a click. The endpoint's findings are already loaded (endpointFindings), and the focus layout
  // keys a type's cluster deterministically as `path-cl-type|<type>|<endpointKey>`, so we pre-seed those
  // clusters (findings + expanded flag + first batch). A type with many findings (> the cap) stays a
  // collapsed "+N" cluster to keep the view readable; the user can still expand it.
  const AUTO_EXPAND_MAX = 5;
  useEffect(() => {
    if (!pathFinding || endpointFindings.length === 0) {
      return;
    }
    const epKey = pathFinding.endpointKey;
    const byType = new Map<string, typeof endpointFindings>();
    for (const f of endpointFindings) {
      const type = f.typeFindings ?? '';
      (byType.get(type) ?? byType.set(type, []).get(type)!).push(f);
    }
    const clusterData = new Map<string, typeof endpointFindings>();
    for (const [type, list] of byType) {
      if (type && list.length > 0 && list.length <= AUTO_EXPAND_MAX) {
        clusterData.set(`path-cl-type|${type}|${epKey}`, list);
      }
    }
    if (clusterData.size === 0) {
      return;
    }
    setFindingsByCluster((prev) => {
      const next = new Map(prev);
      clusterData.forEach((v, k) => {
        if (!next.has(k)) {
          next.set(k, v);
        }
      });
      return next;
    });
    setFindingBatch((prev) => {
      const next = new Map(prev);
      clusterData.forEach((_, k) => {
        if (!next.has(k)) {
          next.set(k, FINDING_BATCH_SIZE);
        }
      });
      return next;
    });
    setExpandedFindingClusters((prev) => {
      const next = new Set(prev);
      clusterData.forEach((_, k) => next.add(k));
      return next;
    });
  }, [pathFinding, endpointFindings]);

  // Progressive endpoint reveal: the "+N" header toggles expand/collapse; an "+rest" overflow reveals
  // the next batch.
  const onClusterClick = useCallback((injectorId: string, kind: 'header' | 'overflow') => {
    // Collapsing removes many nodes: re-fit so the user isn't left staring at an empty region.
    // Expanding (or revealing more) keeps the current zoom/pan so drilling down stays put.
    const collapsing = kind === 'header' && (endpointBatch.get(injectorId) ?? 0) > 0;
    setEndpointBatch((prev) => {
      const next = new Map(prev);
      const current = prev.get(injectorId) ?? 0;
      if (kind === 'overflow') {
        next.set(injectorId, current + ENDPOINT_BATCH_SIZE);
      } else if (current > 0) {
        next.set(injectorId, 0);
      } else {
        next.set(injectorId, ENDPOINT_BATCH_SIZE);
      }
      return next;
    });
    if (collapsing) {
      setFitNonce(n => n + 1);
    } else {
      // Expanding: hold the view on the injector whose endpoints just appeared.
      anchorOnNode(injectorId);
    }
  }, [endpointBatch, anchorOnNode]);

  // Causal-chain view: reveal another ENDPOINT_BATCH_SIZE of a depth's hidden endpoints per click — never
  // the whole overflow at once, so drilling into a heavy step doesn't dump the user back into a wall of
  // nodes. Keeps the current view (no refit): the newly revealed hosts appear near where the user clicked.
  const onEndpointClusterClick = useCallback((clusterId: string) => {
    setEndpointClusterBatch(prev => new Map(prev).set(clusterId, (prev.get(clusterId) ?? 0) + ENDPOINT_BATCH_SIZE));
    anchorOnNode(clusterId);
  }, [anchorOnNode]);

  // injector id -> refs of the endpoints it reached (asset ref or id), for the bounded finding fetch.
  const injectorEndpointRefs = useMemo(() => {
    const map = new Map<string, string[]>();
    if (!dto) {
      return map;
    }
    const assetById = new Map(
      (dto.attackPathNodes ?? []).filter(n => n.type === 'ASSET' && n.id).map(n => [n.id as string, n]),
    );
    for (const e of dto.attackPathEdges ?? []) {
      if (e.type === 'EDGE_EXECUTIONS' && e.edgeSourceId && e.edgeTargetId && assetById.has(e.edgeTargetId)) {
        const ref = assetById.get(e.edgeTargetId)?.ref ?? e.edgeTargetId;
        const arr = map.get(e.edgeSourceId) ?? [];
        if (!arr.includes(ref)) {
          arr.push(ref);
        }
        map.set(e.edgeSourceId, arr);
      }
    }
    return map;
  }, [dto]);

  // Endpoint ref (the raw key an execution carries) -> its friendly name, so the execution panel shows
  // "kingslanding" instead of the raw UUID/IP the execution DTO carries.
  const endpointLabelByRef = useMemo(() => {
    const map = new Map<string, string>();
    for (const n of dto?.attackPathNodes ?? []) {
      if (n.type === 'ASSET') {
        const ref = n.ref ?? n.id;
        const name = n.hostname || n.label;
        if (ref && name) {
          map.set(ref, name);
        }
      }
    }
    return map;
  }, [dto]);

  // All distinct reached-endpoint refs (deduped), for the shared endpoint hub's global finding cluster.
  const allEndpointRefs = useMemo(() => {
    if (!dto) {
      return [] as string[];
    }
    const refs: string[] = [];
    const seen = new Set<string>();
    for (const arr of injectorEndpointRefs.values()) {
      for (const ref of arr) {
        if (!seen.has(ref)) {
          seen.add(ref);
          refs.push(ref);
        }
      }
    }
    return refs;
  }, [dto, injectorEndpointRefs]);

  // Fetch a bounded, de-duplicated set of a finding type's values from the relevant endpoints — a
  // front-only stand-in until a "findings by type" backend endpoint exists. The shared endpoint hub
  // (AP_ALL_ENDPOINTS) fetches across every reached endpoint; a per-injector cluster stays scoped.
  const fetchClusterFindings = useCallback((clusterId: string, type: string, injectorId: string) => {
    const scopeRefs = injectorId === AP_ALL_ENDPOINTS ? allEndpointRefs : (injectorEndpointRefs.get(injectorId) ?? []);
    const refs = scopeRefs.slice(0, FINDING_FETCH_ENDPOINTS);
    Promise.all(
      refs.map(ref => fetchEndpointFindings(simulationId, ref)
        .then(r => r.data.findings ?? [])
        .catch(() => [] as AttackPathNodeDTO[])),
    ).then((lists) => {
      const seen = new Set<string>();
      const deduped: AttackPathNodeDTO[] = [];
      for (const f of lists.flat()) {
        if (f.typeFindings !== type) {
          continue;
        }
        const key = f.value ?? f.id ?? '';
        if (key && !seen.has(key)) {
          seen.add(key);
          deduped.push(f);
        }
      }
      setFindingsByCluster(prev => new Map(prev).set(clusterId, deduped));
    });
  }, [injectorEndpointRefs, allEndpointRefs, simulationId]);

  // Click a finding cluster (per injector when collapsed, per endpoint when expanded): the header
  // expands/collapses it into its individual findings (fetched once, batched); an overflow reveals the
  // next batch. The cluster carries its own key; an endpoint ref scopes the fetch to that host.
  const onFindingClusterClick = useCallback(
    (clusterId: string, typeFindings: string | undefined, injectorId: string | undefined, endpointRef: string | undefined, kind: 'header' | 'overflow' | 'typeOverflow') => {
      if (kind === 'overflow') {
        setFindingBatch(prev => new Map(prev).set(clusterId, (prev.get(clusterId) ?? 0) + FINDING_BATCH_SIZE));
        anchorOnNode(clusterId);
        return;
      }
      // "+N other types": purely a layout toggle (reveal/hide the type clusters the column capped
      // away). It fetches nothing — each revealed type cluster loads its own findings when clicked.
      if (kind === 'typeOverflow') {
        setExpandedFindingClusters((prev) => {
          const next = new Set(prev);
          if (next.has(clusterId)) {
            next.delete(clusterId);
          } else {
            next.add(clusterId);
          }
          return next;
        });
        setFitNonce(n => n + 1);
        return;
      }
      if (expandedFindingClusters.has(clusterId)) {
        setExpandedFindingClusters((prev) => {
          const next = new Set(prev);
          next.delete(clusterId);
          return next;
        });
        setSelectedFindingId(null);
        setFindingDetail(null);
        // Collapsing removes nodes: refit so the layout stays readable. Expanding keeps the view.
        setFitNonce(n => n + 1);
        return;
      }
      setExpandedFindingClusters(prev => new Set(prev).add(clusterId));
      setFindingBatch(prev => new Map(prev).set(clusterId, FINDING_BATCH_SIZE));
      // Hold the view on the cluster that was clicked: the revealed findings grow the world, which the
      // canvas would otherwise read as the graph changing shape and re-fit from the entrance.
      anchorOnNode(clusterId);
      setSelectedFindingId(clusterId);
      setFindingDetail(null);
      // In the focused view, scope the highlight to the action(s) that PRODUCED this finding type — so
      // clicking the portscan cluster lights Nmap (its producer), not another injector that merely reached
      // the endpoint. Mirrors a leaf-finding click, aggregated over every finding of the type.
      if (pathFinding && typeFindings) {
        const typeFindingIds = new Set(
          (fullDto?.attackPathNodes ?? [])
            .filter(n => n.type === 'FINDING' && (n.typeFindings ?? '') === typeFindings)
            .map(n => n.id),
        );
        const refs = (fullDto?.attackPathExecutions ?? [])
          .filter(e => (e.findingsNodeIds ?? []).some(id => typeFindingIds.has(id)))
          .map(e => e.ref)
          .filter((r): r is string => !!r);
        setHighlightedExecutionIds(new Set(refs));
      }
      if (!findingsByCluster.has(clusterId)) {
        if (endpointRef) {
          fetchEndpointFindings(simulationId, endpointRef)
            .then((r) => {
              const seen = new Set<string>();
              const deduped: AttackPathNodeDTO[] = [];
              const parts = clusterId.split('|');
              const isPathSame = parts[0] === 'path-cl-same';
              const isPathOther = parts[0] === 'path-cl-other';
              const excludedType = parts.length > 1 ? parts[1] : '';
              for (const f of r.data.findings ?? []) {
                const type = f.typeFindings ?? '';
                if (isPathSame) {
                  // In focused view, "same-type others" excludes the selected finding itself.
                  if (type !== excludedType || (pathFinding && (f.value ?? '') === pathFinding.value)) {
                    continue;
                  }
                } else if (isPathOther) {
                  if (type === excludedType) {
                    continue;
                  }
                } else if (type !== (typeFindings ?? '')) {
                  continue;
                }
                const key = `${f.typeFindings ?? ''}|${f.value ?? f.id ?? ''}`;
                if (!seen.has(key)) {
                  seen.add(key);
                  deduped.push(f);
                }
              }
              setFindingsByCluster(prev => new Map(prev).set(clusterId, deduped));
            })
            .catch(() => undefined);
        } else if (injectorId) {
          fetchClusterFindings(clusterId, typeFindings ?? '', injectorId);
        }
      }
    },
    [expandedFindingClusters, findingsByCluster, fetchClusterFindings, simulationId, pathFinding, fullDto, anchorOnNode],
  );

  // Auto-expand the focused finding's own type cluster (fetching its individual findings if not
  // already loaded) whenever the non-chain path-focus view targets a new endpoint/type: without this,
  // the cluster's children never render at all, so the downstream highlight pass above always had
  // nothing to light past the type cluster itself, regardless of which finding the user picked.
  useEffect(() => {
    if (!pathFinding?.type || !pathFinding?.endpointKey) {
      return;
    }
    const clusterId = `path-cl-type|${pathFinding.type}|${pathFinding.endpointKey}`;
    if (!expandedFindingClusters.has(clusterId)) {
      setExpandedFindingClusters(prev => new Set(prev).add(clusterId));
      setFindingBatch(prev => new Map(prev).set(clusterId, FINDING_BATCH_SIZE));
    }
    if (!findingsByCluster.has(clusterId)) {
      fetchEndpointFindings(simulationId, pathFinding.endpointKey)
        .then((r) => {
          const seen = new Set<string>();
          const deduped: AttackPathNodeDTO[] = [];
          for (const f of r.data.findings ?? []) {
            if ((f.typeFindings ?? '') !== pathFinding.type) {
              continue;
            }
            const key = `${f.typeFindings ?? ''}|${f.value ?? f.id ?? ''}`;
            if (!seen.has(key)) {
              seen.add(key);
              deduped.push(f);
            }
          }
          setFindingsByCluster(prev => new Map(prev).set(clusterId, deduped));
        })
        .catch(() => undefined);
    }
  }, [pathFinding?.type, pathFinding?.endpointKey, expandedFindingClusters, findingsByCluster, simulationId]);

  // Open the findings drawer for a summary category and load the whole category once (bounded); the
  // drawer then searches/paginates client-side. Values are masked server-side for credentials.
  const openFindingsDrawer = useCallback((category: string, label: string) => {
    setDrawerCategory(category);
    setDrawerLabel(label);
    setDrawerSearch('');
    setDrawerPage(0);
    setFindingsPage(null);
    setFindingsLoading(true);
    setLegendCollapseNonce(n => n + 1);
    fetchFindingsByCategory(simulationId, category, 0, DRAWER_FETCH_SIZE)
      .then(r => setFindingsPage(r.data))
      .catch(() => setFindingsPage({
        items: [],
        total: 0,
      }))
      .finally(() => setFindingsLoading(false));
  }, [simulationId]);

  // A live update that touched the open category's findings refreshes its rows silently: the same
  // bounded read runs again and swaps the loaded page, while the user's search text, current page and
  // scroll position stay exactly where they were (FR8) — no spinner, no drawer reset.
  useEffect(() => {
    if (!drawerCategory || changedFindingTypes.length === 0) {
      return undefined;
    }
    const types = FILTER_TO_FINDING_TYPES[drawerCategory] ?? [drawerCategory];
    if (!changedFindingTypes.some(type => types.includes(type))) {
      return undefined;
    }
    let cancelled = false;
    fetchFindingsByCategory(simulationId, drawerCategory, 0, DRAWER_FETCH_SIZE)
      .then((r) => {
        if (!cancelled) {
          setFindingsPage(r.data);
        }
      })
      .catch(() => {
        // Keep the rows already listed; the next delta touching this category retries.
      });
    return () => {
      cancelled = true;
    };
  }, [changedFindingTypes, drawerCategory, simulationId]);

  // Click a finding item in the drawer: close the drawer and refocus the map on ONLY the attack path
  // that produced it (injector(s) -> endpoint -> finding), fitted to an overview. The endpoint's feed
  // still loads for detail, with the producing executions highlighted.
  const onFindingItemClick = useCallback(
    (item: AttackPathFindingItemDTO) => {
      if (!item.endpointNodeId || !item.endpointKey) {
        return;
      }
      setDrawerCategory(null);
      setActiveCard(null);
      setSelectedInjectorId(null);
      setFocusRequest(null);
      // Use the endpoint's friendly label (hostname) for the panel title, like a direct node click.
      const node = (dto?.attackPathNodes ?? []).find(n => n.id === item.endpointNodeId);
      const label = node?.hostname || node?.label || item.endpointKey;
      // The finding's canonical (backend) node id — needed so the highlight reaches the exact finding,
      // not just its type cluster (the backend escapes `\`/`|`, so a share value never matches a
      // rebuilt `NODE_FINDING|type|value`, hence matching on typeFindings+value like highlightGraphFinding).
      const canonicalId = (fullDto?.attackPathNodes ?? [])
        .find(n => n.type === 'FINDING' && (n.typeFindings ?? '') === (item.type ?? '') && (n.value ?? n.label) === (item.value ?? ''))?.id;
      if (!chainMode) {
        // Non-chain path-focus view: the finding's own node only exists once its type cluster is
        // expanded (see the auto-expand effect below), so leave selectedFindingId null here — the
        // highlight memo's defaultId fallback (the type cluster) covers it until then.
        setPathFinding({
          endpointNodeId: item.endpointNodeId,
          endpointKey: item.endpointKey,
          type: item.type ?? '',
          value: item.value ?? '',
        });
        setFitNonce(n => n + 1);
      } else {
        // Chain view: switch into the SAME chokepoint-style scoped focus (scopeChainFlowToEndpoint
        // filters the real graph down to this endpoint's own causal subgraph), rather than leaving
        // the finding highlighted-but-lost in the full, possibly crowded map. type/value stay empty
        // (chokepoint's own shape) — selectedFindingId (set below) is what seeds the walk onto the
        // exact finding inside this now-scoped view, not the endpoint alone.
        setPathFinding({
          endpointNodeId: item.endpointNodeId,
          endpointKey: item.endpointKey,
          type: '',
          value: '',
        });
        setFitNonce(n => n + 1);
      }
      // onEndpointClick itself unconditionally clears selectedFindingId/findingDetail, so both must be
      // re-set AFTER it (matches openFindingFromGraph's ordering) — setting them before it here was
      // the bug: onEndpointClick silently clobbered the canonical id back to null on every click.
      // No side panel: like every other focus entry point, the click is about the focused graph, and a
      // panel opening on top of it takes back the width the focus just revealed.
      onEndpointClick(item.endpointNodeId, item.endpointKey, label, { openPanel: false });
      if (chainMode) {
        setSelectedFindingId(canonicalId ?? null);
      }
      setHighlightedExecutionIds(new Set(item.executionIds ?? []));
    },
    [onEndpointClick, dto?.attackPathNodes, fullDto?.attackPathNodes, chainMode],
  );

  // Producing contract labels per injector for the focused finding path, so each injector->endpoint
  // branch is labelled with its own contract(s), not a global merged string.
  const pathContractLabelByInjector = useMemo(() => {
    if (!pathFinding) {
      return {} as Record<string, string>;
    }
    // In endpoint focus (no specific finding highlighted) label every injector with its contract; when a
    // finding is highlighted, restrict to the executions that produced it so the branch reads its contract.
    const restrict = highlightedExecutionIds.size > 0;
    const execLabelById = new Map(
      executions
        .filter(e => !!e.ref)
        .map(e => [e.ref as string, toContractLabel(e)] as const),
    );
    const byInjector = new Map<string, string[]>();
    for (const e of endpointRelationEdges) {
      if (!e.edgeSourceId || (e.executionIds?.length ?? 0) === 0) {
        continue;
      }
      const labels = (e.executionIds ?? [])
        .filter(id => !restrict || highlightedExecutionIds.has(id))
        .map(id => execLabelById.get(id))
        .filter((s): s is string => !!s);
      if (labels.length > 0) {
        const arr = byInjector.get(e.edgeSourceId) ?? [];
        byInjector.set(e.edgeSourceId, [...arr, ...labels]);
      }
    }
    const result: Record<string, string> = {};
    for (const [injectorId, labels] of byInjector.entries()) {
      const uniq = Array.from(new Set(labels));
      result[injectorId] = uniq.length <= 2
        ? uniq.join(' · ')
        : `${uniq.slice(0, 2).join(' · ')} +${uniq.length - 2}`;
    }
    return result;
  }, [pathFinding, highlightedExecutionIds, executions, endpointRelationEdges]);

  // The injector(s) that actually produced the highlighted finding: an injector whose relation edge
  // has at least one of the finding's producing executions. The feed is scoped to the active finding
  // (drawer pick or graph click), so highlightedExecutionIds already reflects the clicked finding.
  // Decoupled from the label map above (which also needs the execution in the loaded feed), so it
  // stays correct for every category.
  const producingInjectorIds = useMemo(() => {
    const set = new Set<string>();
    if (!pathFinding || highlightedExecutionIds.size === 0) {
      return set;
    }
    for (const e of endpointRelationEdges) {
      if (e.edgeSourceId && (e.executionIds ?? []).some(id => highlightedExecutionIds.has(id))) {
        set.add(e.edgeSourceId);
      }
    }
    return set;
  }, [pathFinding, highlightedExecutionIds, endpointRelationEdges]);

  // Scroll the feed to the first producing execution once the highlight or the loaded feed changes.
  useEffect(() => {
    if (highlightedExecutionIds.size === 0) {
      return;
    }
    const firstId = executions.find(e => e.ref && highlightedExecutionIds.has(e.ref))?.id;
    if (firstId) {
      feedRowRefs.current.get(firstId)?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [highlightedExecutionIds, executions]);

  // Open the Result & Terminal drawer for a feed entry and load its detail (by raw execution id).
  const openExecutionDetail = useCallback((executionId: string) => {
    setDetailExecutionId(executionId);
    setDetail(null);
    setDetailLoading(true);
    setLegendCollapseNonce(n => n + 1);
    fetchExecutionDetail(simulationId, executionId)
      .then(r => setDetail(r.data))
      .catch(() => setDetail(null))
      .finally(() => setDetailLoading(false));
  }, [simulationId]);

  // Chokepoints: rank endpoints by a transparent score = (total findings) × (criticality weight), so
  // the top chokepoint is the most findings on the most critical endpoint — not raw finding count alone.
  // Both operands are kept for the card's explanation. Computed from the already-loaded collapsed DTO
  // (per-endpoint findingCounts + criticality resolved by the backend from the asset).
  const chokepoints = useMemo(
    () => (dto?.attackPathNodes ?? [])
      .filter(n => n.type === 'ASSET' && n.id)
      .map((n) => {
        const findings = Object.values(n.findingCounts ?? {}).reduce((s, v) => s + (v ?? 0), 0);
        const weight = criticalityWeight(n.criticality);
        return {
          nodeId: n.id as string,
          ref: n.ref ?? (n.id as string),
          label: n.hostname || n.label || n.ref || (n.id as string),
          ip: n.ip,
          findings,
          criticality: n.criticality,
          weight,
          score: findings * weight,
        };
      })
      .filter(c => c.findings > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, CHOKEPOINT_TOP_N),
    [dto],
  );
  const chokepointRankById = useMemo(() => {
    const m = new Map<string, number>();
    chokepoints.forEach((c, i) => m.set(c.nodeId, i + 1));
    return m;
  }, [chokepoints]);

  // Pivot endpoints (an ASSET both attacked and used as an attack source) get a tooltip flag.
  const pivotNodeIds = useMemo(
    () => pivotEndpointIds(dto?.attackPathEdges ?? []),
    [dto?.attackPathEdges],
  );

  // All exposed endpoints (not just the top-N) for the table view; same source as chokepoints, no
  // extra fetch. Type columns are the union of finding types present, in a stable order.
  const endpointRows = useMemo<AttackPathEndpointRow[]>(
    () => (dto?.attackPathNodes ?? [])
      .filter(n => n.type === 'ASSET' && n.id)
      .map((n) => {
        const findings = Object.values(n.findingCounts ?? {}).reduce((s, v) => s + (v ?? 0), 0);
        return {
          nodeId: n.id as string,
          ref: n.ref ?? (n.id as string),
          label: n.hostname || n.label || n.ref || (n.id as string),
          ip: n.ip,
          score: findings,
          criticality: n.criticality,
          chokepointScore: findings * criticalityWeight(n.criticality),
          findingCounts: n.findingCounts ?? {},
        };
      })
      .filter(r => r.score > 0),
    [dto],
  );
  // When the graph is focused on one endpoint's path, the table follows the focus (single endpoint),
  // consistent with the summary cards; otherwise it lists every exposed endpoint.
  const tableRows = useMemo(
    () => (pathFinding
      ? endpointRows.filter(r => r.nodeId === pathFinding.endpointNodeId)
      : endpointRows),
    [endpointRows, pathFinding],
  );
  const endpointTypeColumns = useMemo(() => {
    const set = new Set<string>();
    tableRows.forEach(r => Object.keys(r.findingCounts).forEach(k => set.add(k)));
    return [...set].sort();
  }, [tableRows]);

  // Base clustered flow — recomputed when the graph data, endpoint expansion, or finding drill-down
  // changes (positions are deterministic, so it stays off the pure selection/focus path). Top-
  // chokepoint endpoints are decorated with their rank so the node can badge them.

  // A run that HAS executions will render the causal chain, so while its full projection is still being
  // merged show a loader instead of the aggregated view (which reads as "no links yet"). `fullPending` is
  // bounded to that one read — including its failure — so a non-chained, large, or unreadable run never
  // waits on it.
  const chainLoading = useMemo(() => {
    if (fullDto) {
      return false;
    }
    const row = simulations.find(s => s.simulationId === simulationId);
    return fullPending && (row?.executionCount ?? 0) > 0;
  }, [fullDto, fullPending, simulations, simulationId]);

  // The unscoped causal chain for the whole run, in chain mode: built once and reused both to scope the
  // focused view down and to resolve a specific finding's seed id (effectiveSelectedFindingId below) to
  // whatever node actually represents it.
  // The focused finding's own type is pinned past the type cap: ties are broken by name, so a picked
  // "share" could otherwise lose its slot to a "cve" and end up with no node at all — leaving the
  // focus with nothing to seed on and silently showing another path instead. The chain-mode focus
  // entry points (drawer pick, summary-list pick) deliberately leave pathFinding.type empty and carry
  // the exact finding in selectedFindingId instead, so its type is resolved from the raw full graph
  // (NOT from fullChain, which itself depends on this pin) and pinned too — otherwise those picks
  // seeded on the "+N other types" chip whenever the picked type fell past the cap.
  const pinnedFindingTypes = useMemo(
    () => {
      const selectedType = selectedFindingId
        ? (fullDto?.attackPathNodes ?? []).find(n => n.id === selectedFindingId)?.typeFindings
        : undefined;
      return new Set(
        [pathFinding?.type, findingDetail?.type, selectedType].filter((v): v is string => !!v),
      );
    },
    [pathFinding?.type, findingDetail?.type, selectedFindingId, fullDto?.attackPathNodes],
  );
  const fullChain = useMemo(
    () => (chainMode && fullDto
      ? buildCausalChainFlow(fullDto, t, expandedFindingClusters, endpointClusterBatch, pinnedFindingTypes, actionCentric)
      : null),
    [chainMode, fullDto, t, expandedFindingClusters, endpointClusterBatch, pinnedFindingTypes, actionCentric],
  );

  // A finding picked from a drawer/summary list (rather than clicked directly on an already-rendered
  // graph node) may still be hidden inside a collapsed type cluster (more than CHAIN_FINDINGS_MAX_PER_TYPE
  // findings of that type on that endpoint) — its raw id then matches no node in the chain at all. Resolve
  // it to the node that ACTUALLY represents it (itself once expanded, its cluster while collapsed) so
  // seeding/highlighting below has something real to anchor on. Previously, seeding on the unresolved raw
  // id (e.g. one of several "Captured Files" past the cluster cap) matched nothing, so
  // scopeChainFlowToSeeds fell back to showing the ENTIRE unscoped graph with nothing highlighted.
  const effectiveSelectedFindingId = useMemo(() => {
    if (!selectedFindingId || !fullChain) {
      return selectedFindingId;
    }
    return fullChain.causalSourceByFinding.get(selectedFindingId) ?? selectedFindingId;
  }, [selectedFindingId, fullChain]);

  // Frame the highlighted path around the finding that was just selected, wherever it was picked
  // from (graph click, findings drawer, summary list). Keyed off the RESOLVED id so a finding still
  // inside a collapsed cluster anchors on the cluster node that actually exists on the canvas.
  //
  // Until now the camera never moved on a finding click: `focusRequest` was declared and handed to
  // the canvas but only ever set to null, so `centerOnNode` was dead code. On a large attack path
  // the selected finding could therefore sit off-screen entirely.
  useEffect(() => {
    if (!effectiveSelectedFindingId) {
      return;
    }
    setFocusRequest({
      nodeId: effectiveSelectedFindingId,
      nonce: focusNonce.current + 1,
    });
    focusNonce.current += 1;
  }, [effectiveSelectedFindingId]);

  const baseFlow = useMemo(
    () => {
      if (!dto) {
        return {
          nodes: [],
          edges: [],
        };
      }
      // Three layouts, in priority order:
      //  1. a finding-path focus takes over the whole graph until cleared;
      //  2. otherwise, when the size-gated full graph is available, the causal execution-chain layout
      //     (inject → endpoint → finding → next inject, left-to-right in dependsOn order) — the real
      //     kill chain, with forward-flowing causal edges;
      //  3. otherwise the aggregated injector→hub→findings view (fallback for large runs / no full data).
      let raw: {
        nodes: AttackPathFlowNode[];
        edges: AttackPathFlowEdge[];
      };
      if (pathFinding && chainMode && fullChain) {
        // Chain mode has the real kill chain already built for the whole run — scope THAT down
        // instead of falling back to buildFindingPathFlow's flatter, non-causal layout, so the
        // focused view keeps the causal ("Triggered ...") structure between actions. A specific
        // finding (effectiveSelectedFindingId — already resolved to its collapsed cluster when the
        // raw finding has no node of its own) seeds on itself for a tighter focus that skips the
        // endpoint's unrelated siblings.
        let seeds: Set<string> | null = null;
        if (effectiveSelectedFindingId) {
          seeds = new Set([effectiveSelectedFindingId]);
        } else if (selectedInjectorId && fullChain.nodes.some(n => n.id === selectedInjectorId)) {
          // A clicked action (with no finding selected) seeds the scope on itself, so the WHOLE chain
          // through it — including its downstream consequences (recipient team, follow-on actions) —
          // stays in the focused view. Without this the scope collapsed to the endpoint-only slice,
          // which drops those downstream nodes; the selected action then vanished and its highlight
          // walk found nothing, so the graph read as "nothing highlighted".
          seeds = new Set([selectedInjectorId]);
        }
        // The plain endpoint-only focus (chokepoint click, no finding/action selected) seeds on the
        // endpoint itself, matched on EVERY identifier it is known by — its graph node id AND its ref —
        // because a chain endpoint node is keyed by whatever form its execution edge carried, which is
        // not always the same form the click hands us. Matching on the node id alone left a
        // ref-keyed endpoint unresolvable, and an unresolvable seed makes the scoper return the whole
        // chain (its no-empty-canvas safety net), so the graph came back identical and the click read
        // as "nothing happened".
        raw = seeds
          ? scopeChainFlowToSeeds(fullChain, seeds)
          : scopeChainFlowToEndpoint(fullChain, [pathFinding.endpointNodeId, pathFinding.endpointKey]);
      } else if (pathFinding) {
        raw = buildFindingPathFlow(dto, pathFinding, t, pathContractLabelByInjector, {
          expanded: expandedFindingClusters,
          findingsByCluster,
          batch: findingBatch,
        });
      } else if (fullChain) {
        raw = fullChain;
      } else {
        raw = buildClusteredAttackPathFlow(dto, endpointBatch, t, {
          expanded: expandedFindingClusters,
          findingsByCluster,
          batch: findingBatch,
        }, pinnedFindingTypes);
      }
      if (chokepointRankById.size === 0 && pivotNodeIds.size === 0) {
        return raw;
      }
      return {
        nodes: raw.nodes.map(n => (n.type === AP_FLOW_NODE_TYPE.asset && (chokepointRankById.has(n.id) || pivotNodeIds.has(n.id))
          ? {
              ...n,
              data: {
                ...n.data,
                chokepointRank: chokepointRankById.get(n.id),
                isPivot: pivotNodeIds.has(n.id),
              },
            }
          : n)),
        edges: raw.edges,
      };
    },
    [
      dto, chainMode, fullChain, pathFinding, effectiveSelectedFindingId, selectedInjectorId, pathContractLabelByInjector, endpointBatch,
      expandedFindingClusters, endpointClusterBatch, findingsByCluster, findingBatch, chokepointRankById, pivotNodeIds, t,
    ],
  );

  // Highlight, in place, a finding clicked directly in the focused graph: keep it where it is and
  // just light up the attack path (its producing injector branch -> endpoint -> the finding) in blue,
  // exactly like selecting it from the drawer but without moving/re-focusing it. Its producing
  // executions come from the finding's category page (per-finding executionIds), matched on the
  // focused endpoint, and drive the injector restriction via producingInjectorIds.
  const highlightGraphFinding = useCallback((nodeId: string, type: string, value: string) => {
    if (!pathFinding) {
      return;
    }
    const mainId = `path-finding|${pathFinding.type}|${pathFinding.value}`;
    // Surface the finding details panel for the clicked finding (its own value, not the focus root).
    setFindingDetail({
      type,
      value,
      endpointNodeId: pathFinding.endpointNodeId,
    });
    setSelectedNodeId(null);
    // A new finding invalidates the previous execution's Result & Terminal panel — close it so only
    // the new finding's panel remains until the user opens one of its own producing actions.
    setDetailExecutionId(null);
    setDetail(null);
    setLegendCollapseNonce(n => n + 1);
    // The main focused finding has no in-place child highlight; any other finding highlights itself.
    setSelectedFindingId(nodeId === mainId ? null : nodeId);
    // A finding highlight and an injector reverse-highlight are mutually exclusive.
    setSelectedInjectorId(null);
    // Scope the execution feed (and the producing-injector highlight) to THIS finding's producing
    // executions, exactly like picking it in the drawer — resolved from its category page.
    const { endpointKey } = pathFinding;
    const applyExec = (ids: string[]) => setHighlightedExecutionIds(new Set(ids));
    const matchIn = (items: AttackPathFindingItemDTO[]) =>
      items.find(it => it.endpointKey === endpointKey && (it.type ?? '') === type && findingValuesMatch(type, it.value ?? '', value));
    const loaded = matchIn(findingsPage?.items ?? []);
    if (loaded) {
      applyExec(loaded.executionIds ?? []);
      return;
    }
    // Authoritative for EVERY finding type: the full graph's execution→findings links. This covers types
    // the drawer categories don't (port, hash…), which otherwise resolved to no producer — leaving every
    // injector on the endpoint highlighted instead of just the one that produced the finding. Resolve the
    // finding's CANONICAL node id from the graph (the backend escapes `\`/`|`, so a share value never
    // matches a rebuilt `NODE_FINDING|type|value`) and match executions on that.
    const canonicalId = (fullDto?.attackPathNodes ?? [])
      .find(n => n.type === 'FINDING' && (n.typeFindings ?? '') === type && (n.value ?? n.label) === value)?.id;
    const fromFull = canonicalId
      ? (fullDto?.attackPathExecutions ?? [])
          .filter(e => (e.findingsNodeIds ?? []).includes(canonicalId))
          .map(e => e.ref)
          .filter((r): r is string => !!r)
      : [];
    if (fromFull.length > 0) {
      applyExec(fromFull);
      return;
    }
    const category = CATEGORY_OF_TYPE[type];
    if (!category) {
      applyExec([]);
      return;
    }
    fetchFindingsByCategory(simulationId, category, 0, DRAWER_FETCH_SIZE)
      .then(r => applyExec(matchIn(r.data.items ?? [])?.executionIds ?? []))
      .catch(() => applyExec([]));
  }, [pathFinding, findingsPage, simulationId, fullDto]);

  // Reverse of a finding click, only meaningful in the focused finding-path view: clicking an
  // injector highlights its DOWNSTREAM path (injector -> endpoint -> the findings it produced) and
  // scopes the execution feed to that injector's executions on the focused endpoint. Stays in the
  // same focused view — no re-focus, no finding panel.
  const highlightGraphInjector = useCallback((injectorId: string) => {
    if (!pathFinding) {
      return;
    }
    setFindingDetail(null);
    setSelectedNodeId(null);
    setSelectedFindingId(null);
    // A new selection invalidates any open Result & Terminal panel.
    setDetailExecutionId(null);
    setDetail(null);
    setLegendCollapseNonce(n => n + 1);
    setSelectedInjectorId(injectorId);
    // Scope the feed (and the producing-injector highlight) to this injector's executions on the
    // focused endpoint — the mirror of scoping to a finding's producing executions.
    const ids = endpointRelationEdges
      .filter(e => e.edgeSourceId === injectorId)
      .flatMap(e => e.executionIds ?? []);
    setHighlightedExecutionIds(new Set(ids));
  }, [pathFinding, endpointRelationEdges]);

  // Click a finding node directly in the clustered graph (Q1/A): switch to the focused path view for
  // its origin endpoint AND open its details panel straight away — so any leaf finding on the map
  // opens the same endpoint / verdicts / producing-actions panel a drawer pick does, without an extra
  // click. The origin endpoint is resolved from the finding's assetNodeId; returns false (so the
  // caller falls back to a plain in-place highlight) when it can't be resolved.
  const openFindingFromGraph = useCallback((nodeId: string, type: string, value: string, assetNodeId: string): boolean => {
    // Resolve the origin endpoint from EITHER graph: the chain view is built from the full graph, whose
    // endpoint nodes may not all exist in the collapsed one. Bailing when the collapsed lookup missed left
    // the previous finding's panel on screen (clicking a share still showed the last portscan).
    const node = (dto?.attackPathNodes ?? []).find(n => n.id === assetNodeId)
      ?? (fullDto?.attackPathNodes ?? []).find(n => n.id === assetNodeId);
    if (!node && !chainMode) {
      return false;
    }
    const endpointKey = node?.ref ?? assetNodeId;
    const label = node?.hostname || node?.label || endpointKey;
    setDrawerCategory(null);
    setActiveCard(null);
    setSelectedInjectorId(null);
    setFocusRequest(null);
    if (!chainMode) {
      setSelectedFindingId(null);
      setPathFinding({
        endpointNodeId: assetNodeId,
        endpointKey,
        type,
        value,
      });
    } else {
      // Chain view: same chokepoint-style scoped focus as a drawer pick (scopeChainFlowToEndpoint
      // filters the real causal graph down to this endpoint), not a plain in-place highlight on the
      // possibly crowded full map. type/value stay empty (chokepoint's own shape); selectedFindingId
      // (set below) is what seeds the walk onto the exact finding inside this now-scoped view.
      setPathFinding({
        endpointNodeId: assetNodeId,
        endpointKey,
        type: '',
        value: '',
      });
    }
    setFitNonce(n => n + 1);
    // Loads the endpoint feed (executions + relations) so producing actions resolve. It also resets
    // findingDetail + highlightedExecutionIds + selectedFindingId, so all are set right after in the batch.
    onEndpointClick(assetNodeId, endpointKey, label);
    setFindingDetail({
      type,
      value,
      endpointNodeId: assetNodeId,
    });
    if (chainMode) {
      // In the causal-chain layout the graph already reads inject → endpoint → finding → next inject, so
      // clicking a finding must NOT collapse into the old focused-path layout. Keep the chain and select
      // the finding by its node id (its producer branch lights up via the upstream selection walk). Set
      // AFTER onEndpointClick, which resets selectedFindingId.
      // Use the node's ACTUAL id, not a rebuilt `NODE_FINDING|type|value`: the backend escapes `\` and `|`
      // in the id, so a share value like `\\host\NETLOGON` encodes with doubled backslashes. Rebuilding it
      // raw never matched the executions' findingsNodeIds → shares showed "no producing action".
      setSelectedFindingId(nodeId);
      // Producing executions come from the full graph's execution→findings links (available for EVERY
      // finding type, unlike the drawer categories which only cover credentials/users/shares/cves), so the
      // panel lists only the injector(s) that actually produced this finding — not every injector that
      // merely reached the endpoint.
      const findingNodeId = nodeId;
      const producerRefs = (fullDto?.attackPathExecutions ?? [])
        .filter(e => (e.findingsNodeIds ?? []).includes(findingNodeId))
        .map(e => e.ref)
        .filter((r): r is string => !!r);
      setHighlightedExecutionIds(new Set(producerRefs));
      return true;
    }
    // Scope the feed (and the producing-action list) to THIS finding's producing executions, resolved
    // from its category page exactly like a drawer pick.
    const category = CATEGORY_OF_TYPE[type];
    if (!category) {
      setHighlightedExecutionIds(new Set());
      return true;
    }
    fetchFindingsByCategory(simulationId, category, 0, DRAWER_FETCH_SIZE)
      .then((r) => {
        const match = (r.data.items ?? []).find(it =>
          it.endpointKey === endpointKey && (it.type ?? '') === type && findingValuesMatch(type, it.value ?? '', value));
        setHighlightedExecutionIds(new Set(match?.executionIds ?? []));
      })
      .catch(() => setHighlightedExecutionIds(new Set()));
    return true;
  }, [dto?.attackPathNodes, onEndpointClick, simulationId, chainMode, fullDto]);

  // Click a leaf finding: in the focused view highlight it in place (same actions as a drawer
  // selection); in the clustered view switch to its focused endpoint path + open its details panel
  // (Q1/A), falling back to a plain path highlight when the origin endpoint can't be resolved.
  const onFindingSelect = useCallback((nodeId: string, type?: string, value?: string, assetNodeId?: string) => {
    if (pathFinding) {
      highlightGraphFinding(nodeId, type ?? '', value ?? '');
      return;
    }
    if (assetNodeId && openFindingFromGraph(nodeId, type ?? '', value ?? '', assetNodeId)) {
      return;
    }
    setSelectedNodeId(null);
    setSelectedInjectorId(null);
    setSelectedFindingId(prev => (prev === nodeId ? null : nodeId));
  }, [pathFinding, highlightGraphFinding, openFindingFromGraph]);

  // An injector's own findings, grouped by type: attributed to this injector by keeping only category
  // findings produced by one of ITS execution refs (the category endpoint carries executionIds; the raw
  // endpoint-findings endpoint does not), so a shared endpoint's findings are not wrongly credited here.
  const loadInjectorFindings = useCallback((ownedRefs: Set<string>) => {
    setInjectorFindingGroups([]);
    if (ownedRefs.size === 0) {
      return;
    }
    // Prefer the full graph: each execution lists EVERY finding type it produced (findingsNodeIds), so
    // shares surface here — the drawer category endpoint only covers credentials/users/cves/shares, so an
    // injector that produced another type wrongly read "No findings on this endpoint".
    const findingById = new Map((fullDto?.attackPathNodes ?? [])
      .filter(n => n.type === 'FINDING' && n.id)
      .map(n => [n.id as string, n]));
    if (findingById.size > 0) {
      const byType = new Map<string, string[]>();
      for (const e of fullDto?.attackPathExecutions ?? []) {
        if (!e.ref || !ownedRefs.has(e.ref)) {
          continue;
        }
        for (const fid of e.findingsNodeIds ?? []) {
          const f = findingById.get(fid);
          const type = f?.typeFindings ?? '';
          const value = maskFindingValue(type, f?.value);
          if (!type || !value) {
            continue;
          }
          const arr = byType.get(type) ?? [];
          if (!arr.includes(value)) {
            arr.push(value);
          }
          byType.set(type, arr);
        }
      }
      setInjectorFindingGroups([...byType.entries()].map(([type, values]) => ({
        type,
        values,
      })));
      return;
    }
    // Fallback (collapsed-only graph, no full data loaded): resolve from the drawer category endpoint.
    setInjectorFindingsLoading(true);
    Promise.all(
      INJECTOR_FINDING_CATEGORIES.map(cat => fetchFindingsByCategory(simulationId, cat, 0, DRAWER_FETCH_SIZE)
        .then(r => (r.data.items ?? []).filter(it => (it.executionIds ?? []).some(id => ownedRefs.has(id))))
        .catch(() => [] as AttackPathFindingItemDTO[])),
    )
      .then((lists) => {
        const byType = new Map<string, string[]>();
        for (const it of lists.flat()) {
          const type = it.type ?? '';
          const value = maskFindingValue(type, it.value);
          if (!type || !value) {
            continue;
          }
          const arr = byType.get(type) ?? [];
          if (!arr.includes(value)) {
            arr.push(value);
          }
          byType.set(type, arr);
        }
        setInjectorFindingGroups([...byType.entries()].map(([type, values]) => ({
          type,
          values,
        })));
      })
      .finally(() => setInjectorFindingsLoading(false));
  }, [simulationId, fullDto?.attackPathNodes, fullDto?.attackPathExecutions]);

  // Load an injector's own executions (its contracts) across every endpoint it reached, for the injector
  // panel — the action-side mirror of the endpoint panel. Executions are gathered from the injector's
  // reached endpoints and filtered to the injector's own execution refs (from the graph edges), so the
  // list is exactly what this injector ran (potentially the same contract against several endpoints).
  const loadInjectorExecutions = useCallback((injectorId: string, label?: string) => {
    setInjectorPanelLabel(label || friendlyNodeId(injectorId));
    setInjectorExecutions([]);
    setInjectorFindingGroups([]);
    const refs = injectorEndpointRefs.get(injectorId) ?? [];
    if (refs.length === 0) {
      return;
    }
    // The collapsed graph edges carry no executionIds, so scope per endpoint using the endpoint-relations
    // edges (which do): keep only the executions this injector ran on each endpoint.
    Promise.all(
      // The injector panel lists its own contracts, a bounded set, so one large page per endpoint is
      // enough here — it is not the per-endpoint feed that needs paging.
      refs.map(ref => fetchEndpointRelations(simulationId, ref, 0, INJECTOR_RELATIONS_PAGE_SIZE)
        .then((r) => {
          const owned = new Set(
            (r.data.edges ?? [])
              .filter(e => e.edgeSourceId === injectorId)
              .flatMap(e => e.executionIds ?? []),
          );
          // The edges are whole, so `owned` is this injector's true count on that endpoint even when
          // the executions came back as one page — that is what the panel states it is showing.
          return {
            page: (r.data.executions ?? []).filter(e => !!e.ref && owned.has(e.ref)),
            total: owned.size,
          };
        })
        .catch(() => ({
          page: [] as AttackPathNodeDTO[],
          total: 0,
        }))),
    )
      .then((results) => {
        const seen = new Set<string>();
        const execs: AttackPathNodeDTO[] = [];
        for (const e of results.flatMap(r => r.page)) {
          if (e.ref && !seen.has(e.ref)) {
            seen.add(e.ref);
            execs.push(e);
          }
        }
        setInjectorExecutions(execs);
        // What this injector actually ran, across every endpoint it reached: when a page cut some of
        // them off, the panel says so instead of truncating silently.
        setInjectorExecTotal(results.reduce((sum, r) => sum + r.total, 0));
        // Attribute findings to this injector via its own execution refs.
        loadInjectorFindings(new Set(execs.map(e => e.ref).filter((r): r is string => !!r)));
      });
  }, [injectorEndpointRefs, simulationId, loadInjectorFindings]);

  // Click an injector (action) node. In the focused view it reverse-highlights on the focused
  // endpoint; in the clustered view it toggles a downstream highlight of the action's reach AND opens
  // the inject drawer (a representative execution's command + prevention/detection + ATT&CK), the
  // action-side mirror of the finding click.
  const onInjectorSelect = useCallback((injectorId: string, label?: string) => {
    if (pathFinding) {
      // Clicking the already-highlighted action again clears it (a consistent unselect), restoring the
      // focused endpoint's base view instead of leaving a stuck selection.
      if (selectedInjectorId === injectorId) {
        setSelectedInjectorId(null);
        setHighlightedExecutionIds(new Set());
        setDetailExecutionId(null);
        setDetail(null);
        return;
      }
      highlightGraphInjector(injectorId);
      // Also open the inject drawer for this injector's execution on the focused endpoint — same
      // behaviour as the global view, not just the highlight.
      const ids = endpointRelationEdges
        .filter(e => e.edgeSourceId === injectorId)
        .flatMap(e => e.executionIds ?? []);
      if (ids[0]) {
        openExecutionDetail(ids[0]);
      }
      return;
    }
    setSelectedNodeId(null);
    setFindingDetail(null);
    setSelectedFindingId(null);
    // Any execution detail from a previous selection is closed, so the injector panel opens as master.
    setDetailExecutionId(null);
    setDetail(null);
    const willSelect = selectedInjectorId !== injectorId;
    setSelectedInjectorId(willSelect ? injectorId : null);
    if (willSelect) {
      // Open the injector panel: its contracts/executions listed like an endpoint's, one click away from
      // the Result / Execution details / Remediation detail (the global command it ran).
      loadInjectorExecutions(injectorId, label);
    } else {
      // Toggling the same injector off also clears its panel.
      setInjectorExecutions([]);
      setInjectorFindingGroups([]);
    }
  }, [pathFinding, highlightGraphInjector, endpointRelationEdges, openExecutionDetail, selectedInjectorId, loadInjectorExecutions]);

  // The active card focus, as finding types (or the endpoints backbone).
  const focus = useMemo((): readonly string[] | 'endpoints' | null => {
    if (!activeCard) {
      return null;
    }
    if (activeCard === 'endpoints') {
      return 'endpoints';
    }
    // Curated groupings map to several types (e.g. users = username + admin_username); any other card
    // key is itself a finding type, so it focuses on exactly that type — no per-type code needed.
    return FILTER_TO_FINDING_TYPES[activeCard] ?? [activeCard];
  }, [activeCard]);

  // Mark the selected endpoint, the highlighted finding path (blue), then apply the card focus.
  const { nodes, edges } = useMemo(() => {
    // Focused finding-path view: keep the focused scope, and let clicks on findings/clusters
    // highlight the exact sub-path (injector -> endpoint -> selection).
    if (pathFinding) {
      // Endpoint focus (no specific finding, e.g. a chokepoint click) with nothing sub-selected: only
      // the focused endpoint is "selected" (blue); its injectors, edges and finding clusters keep their
      // verdict colour (green/orange/red) so blue means "what I picked", not "the whole subgraph".
      if (!pathFinding.type && !selectedInjectorId && !selectedFindingId) {
        // The endpoint's own node id in the scoped chain graph is `chain-ep|depth|<raw id>`, not the
        // raw id itself (that's the non-chain buildFindingPathFlow layout's scheme) — match either.
        const isFocusedEndpoint = (nodeId: string) => nodeId === pathFinding.endpointNodeId
          || (nodeId.startsWith('chain-ep|') && nodeId.endsWith(`|${pathFinding.endpointNodeId}`));
        return {
          nodes: baseFlow.nodes.map(n => ({
            ...n,
            selected: isFocusedEndpoint(n.id),
            data: {
              ...(n.data ?? {}),
              dimmed: false,
            },
          })),
          edges: baseFlow.edges.map(e => ({
            ...e,
            selected: false,
            data: {
              ...(e.data ?? {}),
              count: e.data?.count ?? 1,
              dimmed: false,
            },
          })),
        };
      }
      const injectorIds = new Set(
        baseFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector).map(n => n.id),
      );
      const endpointNodeIds = new Set(
        baseFlow.nodes
          .filter(n => n.type === AP_FLOW_NODE_TYPE.asset || n.type === AP_FLOW_NODE_TYPE.endpointCluster)
          .map(n => n.id),
      );
      const pathSet = new Set<string>();
      // Tracks the finding-focus "active" node (its own leaf if selected, else its type cluster) across
      // both branches below, so the downstream pass after them can re-key off it instead of
      // selectedFindingId directly (see that pass's comment for why selectedFindingId alone missed the
      // type-cluster's own default focus).
      let activeId: string | null = null;
      if (selectedInjectorId && injectorIds.has(selectedInjectorId)) {
        // Focus a clicked action on its FULL path, mirroring a finding click: the whole chain that led
        // to it (walk UPSTREAM) plus what it itself reached (walk DOWNSTREAM seeded from the action
        // alone, so sibling branches hanging off a shared upstream node are not swept in). Downstream
        // only (the old behaviour) left a late-stage action like "Send individual mails" lighting just
        // its recipient — everything that led there went dark, which read as "nothing highlighted".
        pathSet.add(selectedInjectorId);
        for (let pass = 0; pass < 8; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.source && e.target && pathSet.has(e.target) && !pathSet.has(e.source)) {
              pathSet.add(e.source);
            }
          }
        }
        const down = new Set<string>([selectedInjectorId]);
        for (let pass = 0; pass < 8; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.source && e.target && down.has(e.source) && !down.has(e.target)) {
              down.add(e.target);
              pathSet.add(e.target);
            }
          }
        }
      } else {
        // The focused finding is highlighted inside its own type cluster (no extracted node), so the
        // default active node is that type's cluster. A leaf finding clicked in place overrides it.
        const defaultId = `path-cl-type|${pathFinding.type}|${pathFinding.endpointKey}`;
        // effectiveSelectedFindingId (not the raw selectedFindingId): in chain mode a finding picked
        // from a drawer/summary list may still be hidden inside a collapsed type cluster, so its own id
        // has no node to seed on — resolved to that cluster's id instead (see its definition above).
        activeId = effectiveSelectedFindingId ?? defaultId;
        // Only the injector(s) that actually produced the focused finding light up — not every injector
        // that merely reached the endpoint — so the highlighted path stays scoped to the finding the
        // analyst opened, even after expanding its cluster. Chain mode is exempt: producingInjectorIds
        // is only ever the finding's DIRECT (one-hop) producer, so applying it here cut the walk short
        // at the second injector hop back — every EARLIER action in the causal chain (e.g. the "NetExec
        // SMB - Share Listing" that ran before the "spider_plus" pass that actually captured the file)
        // never got added, staying dimmed despite being genuinely on the path. Chain mode has no
        // shared-hub ambiguity to guard against (unlike the clustered view this restriction was written
        // for), so there's nothing to lose by walking every hop back.
        const restrictInjectors = !chainMode && producingInjectorIds.size > 0;
        // Chain mode gets its own, narrower restriction. The focused finding's OWN endpoint is a hub:
        // every action that ever ran against it is an upstream neighbour, so an unrestricted walk
        // adopted them all and the path covered the entire scope - which is why nothing ever rendered
        // dimmed. Actions that merely touched that endpoint without producing this finding are
        // context, not path, so they are skipped at that one hop. Deeper endpoints keep their actions:
        // those ARE the earlier links of the causal chain, and they are reached through an upstream
        // FINDING ("triggered") edge rather than through the focused endpoint.
        const focusEndpointIds = new Set(
          chainMode && producingInjectorIds.size > 0
            ? baseFlow.edges
                .filter(e => e.target === activeId && !!e.source)
                .map(e => e.source as string)
                .filter(id => endpointNodeIds.has(id))
            : [],
        );
        pathSet.add(activeId);
        for (let pass = 0; pass < 8; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.target && e.source && pathSet.has(e.target) && !pathSet.has(e.source)) {
              if (restrictInjectors && injectorIds.has(e.source) && !producingInjectorIds.has(e.source)) {
                continue;
              }
              if (
                injectorIds.has(e.source)
                && focusEndpointIds.has(e.target)
                && !producingInjectorIds.has(e.source)
              ) {
                continue;
              }
              pathSet.add(e.source);
            }
          }
        }
      }
      // A selected finding CLUSTER sits upstream of its expanded children (cluster → finding). The up-walk
      // above never reaches them, so they'd render dimmed once expanded. Add a scoped DOWNSTREAM pass from
      // the selection so its own expanded findings (and their edges) stay lit. Keyed off activeId (not
      // selectedFindingId) so the type cluster's OWN default focus also lights its expanded children —
      // previously only an explicitly clicked leaf/cluster id (selectedFindingId truthy) did, so the last
      // hop (type cluster -> the specific finding) stayed dimmed on the initial/default focus.
      if (activeId && pathSet.has(activeId)) {
        const down = new Set<string>([activeId]);
        for (let pass = 0; pass < 3; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.source && e.target && down.has(e.source) && !down.has(e.target)) {
              down.add(e.target);
              pathSet.add(e.target);
            }
          }
        }
      }
      return {
        nodes: baseFlow.nodes.map((n) => {
          const selected = pathSet.has(n.id);
          return {
            ...n,
            selected,
            data: {
              ...(n.data ?? {}),
              dimmed: !selected,
            },
          };
        }),
        edges: baseFlow.edges.map((e) => {
          const selected = pathSet.has(e.source) && pathSet.has(e.target);
          return {
            ...e,
            selected,
            data: {
              ...(e.data ?? {}),
              count: e.data?.count ?? 1,
              dimmed: !selected,
            },
          };
        }),
      };
    }
    // Highlight a path: walk UP from a clicked finding to its actions, or DOWN from a clicked
    // injector (action) to its reach. Same visual, mirrored direction, so both feel consistent.
    const pathSet = new Set<string>();
    if (selectedInjectorId && chainMode) {
      // Chain view: clicking an action lights the WHOLE chain that led to it. Walk UPSTREAM from the action
      // through BOTH production and causal ("Triggered …") edges (target in set → add source), so the path
      // from the first action to the one clicked — findings, endpoints and the actions between — is shown.
      pathSet.add(selectedInjectorId);
      for (let pass = 0; pass < 8; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.source && e.target && pathSet.has(e.target) && !pathSet.has(e.source)) {
            pathSet.add(e.source);
          }
        }
      }
    } else if (selectedInjectorId) {
      // Clustered view: an injector's downstream highlight shows its REACH — the endpoints it targeted — not
      // the findings. In the clustered view findings hang off the SHARED endpoint hub and aggregate every
      // injector, so walking into them would wrongly credit one injector with another's findings (e.g.
      // NetExec lighting Nmap's portscans). Stop the walk at endpoint/hub nodes: never propagate into
      // finding-type nodes.
      const findingNodeIds = new Set(
        baseFlow.nodes
          .filter(n => n.type === AP_FLOW_NODE_TYPE.finding
            || n.type === AP_FLOW_NODE_TYPE.findingType
            || n.type === AP_FLOW_NODE_TYPE.findingCluster)
          .map(n => n.id),
      );
      pathSet.add(selectedInjectorId);
      for (let pass = 0; pass < 6; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.source && e.target && pathSet.has(e.source) && !pathSet.has(e.target) && !findingNodeIds.has(e.target)) {
            pathSet.add(e.target);
          }
        }
      }
    } else if (effectiveSelectedFindingId && chainMode) {
      // Chain view: mirror the selectedInjectorId&&chainMode branch above — a finding several hops
      // into the causal chain (e.g. a captured file dropped by a late-stage action) previously only
      // lit its immediate producing injector, because the restricted walk below (kept for the
      // non-chain/clustered case) explicitly skips causal edges. In chain mode there's no shared-hub
      // ambiguity to guard against, so walk the same unrestricted way: from the first action through
      // every action that led to this finding. Seeded on effectiveSelectedFindingId (not the raw
      // selectedFindingId): a finding picked from a drawer/summary list may be hidden inside a still-
      // collapsed type cluster, which has no node of its own to seed on.
      pathSet.add(effectiveSelectedFindingId);
      for (let pass = 0; pass < 8; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.source && e.target && pathSet.has(e.target) && !pathSet.has(e.source)) {
            pathSet.add(e.source);
          }
        }
      }
    } else if (selectedFindingId) {
      // Walk UP a finding's PRODUCTION path only: endpoint(s) it was found on, then the injector(s) that
      // actually produced it. Two guards keep it scoped on a shared/hub endpoint (clustered view): never
      // follow a causal ("Triggered …") edge — those point forward to the NEXT action, so following them
      // lit the whole downstream kill-chain (NetExec + shares) off a mere portscan click; and when we know
      // the producing injector(s) from the finding's executions, don't light the other injectors that
      // merely also reached the endpoint.
      const injectorNodeIds = new Set(
        baseFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector).map(n => n.id),
      );
      const producingInjectors = new Set<string>();
      let hasProducingExecution = false;
      for (const e of fullDto?.attackPathEdges ?? []) {
        if (e.type === 'EDGE_EXECUTIONS' && e.edgeSourceId
          && (e.executionIds ?? []).some(id => highlightedExecutionIds.has(id))) {
          hasProducingExecution = true;
          // A self-loop (endpoint-local action) has no injector node to light: it still activates the
          // restriction (the producer is known — it is the endpoint itself), but adds no injector.
          if (e.edgeSourceId !== e.edgeTargetId) {
            producingInjectors.add(e.edgeSourceId);
          }
        }
      }
      const restrictInjectors = hasProducingExecution;
      pathSet.add(selectedFindingId);
      for (let pass = 0; pass < 6; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.type === AP_FLOW_CAUSAL_EDGE_TYPE) {
            continue;
          }
          if (e.target && e.source && pathSet.has(e.target) && !pathSet.has(e.source)) {
            if (restrictInjectors && injectorNodeIds.has(e.source) && !producingInjectors.has(e.source)) {
              continue;
            }
            pathSet.add(e.source);
          }
        }
      }
    } else if (selectedNodeId && chainMode) {
      // Chain view: same unrestricted backward walk as the injector/finding branches above — an
      // endpoint click previously fell through to the clustered branch below, which compares
      // against `dto`'s (collapsed-view) node ids and never matches the chain graph's own
      // (`chain-ep|depth|id`-style) ids, so it found nothing to light past the endpoint itself.
      pathSet.add(selectedNodeId);
      for (let pass = 0; pass < 8; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.source && e.target && pathSet.has(e.target) && !pathSet.has(e.source)) {
            pathSet.add(e.source);
          }
        }
      }
    } else if (selectedNodeId) {
      // Clustered endpoint click: injectors converge on the shared hub, so walking the rendered edges up
      // would light EVERY injector. Instead resolve the injectors that actually target this endpoint from
      // the raw execution edges, and light them + the hub so the whole path to the endpoint is shown.
      pathSet.add(selectedNodeId);
      pathSet.add(AP_SHARED_EP_CLUSTER_ID);
      for (const e of dto?.attackPathEdges ?? []) {
        if (e.type === 'EDGE_EXECUTIONS' && e.edgeTargetId === selectedNodeId && e.edgeSourceId
          && e.edgeSourceId !== e.edgeTargetId) {
          pathSet.add(e.edgeSourceId);
        }
      }
    }
    const withSelection = {
      nodes: baseFlow.nodes.map(n => ({
        ...n,
        selected: n.id === selectedNodeId || pathSet.has(n.id),
      })),
      edges: baseFlow.edges.map(e => ({
        ...e,
        selected: e.source === selectedNodeId || e.target === selectedNodeId
          || (pathSet.has(e.source) && pathSet.has(e.target)),
      })),
    };
    return applyFindingFilter(withSelection.nodes, withSelection.edges, focus);
  }, [
    baseFlow, pathFinding, producingInjectorIds, selectedNodeId, selectedFindingId, effectiveSelectedFindingId, selectedInjectorId,
    focus, dto?.attackPathEdges, fullDto?.attackPathEdges, highlightedExecutionIds, chainMode,
  ]);

  // Additive kill-chain causal edges (issue 6647) for the AGGREGATED view, merged on top of the status
  // graph. Drawn only when a consumed key matches a produced finding (or a dependsOn resolves). In chain
  // mode the layout already emits its own forward causal edges, so this overlay is disabled to avoid
  // duplicates. Built from the final (post-selection) nodes so the same nodes drive base and overlay.
  const causalEdges = useMemo(
    () => (chainMode ? [] : buildCausalEdges(nodes, id => (id ? killChainMeta.get(id) : undefined), t)),
    [chainMode, nodes, killChainMeta, t],
  );
  const graphEdges = useMemo(() => [...edges, ...causalEdges], [edges, causalEdges]);

  // Entrance affordance (issue 6647): the nodes a live update just introduced get a class that fades
  // them in, batched per delta rather than per entity, and disabled entirely under
  // prefers-reduced-motion (see AP_GLOBAL_STYLES). Nothing else about the node changes, so its
  // position and identity are untouched.
  const enterNodeIds = useMemo(() => new Set(newNodeIds), [newNodeIds]);

  // One screen-reader announcement per delta batch — a summary of what arrived, never one message per
  // entity (a burst of executions would otherwise flood the live region).
  const liveSummary = useMemo(() => {
    if (newNodes.length === 0) {
      return '';
    }
    // Kinds come from the node's own `type`, the same discriminant the layouts read — never from the
    // shape of its id, which is the backend's key format and not a contract the view may parse.
    const endpoints = newNodes.filter(n => n.type === 'ASSET').length;
    const findings = newNodes.filter(n => n.type === 'FINDING').length;
    const parts: string[] = [];
    if (endpoints > 0) {
      parts.push(`${endpoints} ${t('new endpoints')}`);
    }
    if (findings > 0) {
      parts.push(`${findings} ${t('new findings')}`);
    }
    return parts.length > 0 ? parts.join(' · ') : t('Attack path updated');
  }, [newNodes, t]);

  // Discreet freshness indicator (FR10): whether the view is still updating itself, retrying after a
  // failed tick (with when the last good data landed), or done because the run is over.
  const freshnessLabel = (() => {
    if (freshness === 'reconnecting') {
      return t('Reconnecting…');
    }
    return freshness === 'finished' ? t('Run finished') : t('Live');
  })();
  const freshnessTitle = (() => {
    const lastUpdate = lastUpdatedAt
      ? `${t('Last update')}: ${fldt(new Date(lastUpdatedAt).toISOString())}`
      : '';
    if (freshness === 'reconnecting') {
      return [t('Updates interrupted, retrying — showing the last known attack path.'), lastUpdate].filter(Boolean).join(' ');
    }
    if (freshness === 'finished') {
      return [t('The simulation is over: the attack path is final.'), lastUpdate].filter(Boolean).join(' ');
    }
    return [t('The attack path updates itself as the simulation runs.'), lastUpdate].filter(Boolean).join(' ');
  })();

  const counters = dto?.counters;
  const focusedEndpoint = useMemo(
    () => (pathFinding
      ? (dto?.attackPathNodes ?? []).find(n => n.id === pathFinding.endpointNodeId)
      : undefined),
    [dto?.attackPathNodes, pathFinding],
  );

  // The endpoint the finding-detail panel was discovered on, resolved from the finding's own origin node
  // (works in chain mode where there is no focusedEndpoint), against either graph. Drives the panel's
  // "Discovered on" line so it shows the real host/IP instead of the literal word "Endpoint".
  const findingEndpoint = useMemo(
    () => {
      const id = findingDetail?.endpointNodeId;
      if (id) {
        const resolved = (fullDto?.attackPathNodes ?? []).find(n => n.id === id)
          ?? (dto?.attackPathNodes ?? []).find(n => n.id === id);
        if (resolved) {
          return resolved;
        }
      }
      return focusedEndpoint;
    },
    [findingDetail?.endpointNodeId, fullDto?.attackPathNodes, dto?.attackPathNodes, focusedEndpoint],
  );

  // The action(s) that produced the finding shown in the details panel, mapped to a display row that
  // opens the Result & Terminal view. Uses the active finding's executions (a child sub-selection
  // overrides the main focused finding), resolved against the focused endpoint's execution feed.
  const producingActions = useMemo((): ProducingAction[] => {
    if (!findingDetail) {
      return [];
    }
    // The endpoint feed loads asynchronously on click, so filtering it alone flashed "no producing action"
    // until the fetch resolved. The full graph already carries the same executions in memory, so use it as
    // the source when the feed has not loaded yet — the producing actions appear immediately.
    const source = executions.length > 0 ? executions : (fullDto?.attackPathExecutions ?? []);
    return source
      .filter(e => !!e.ref && highlightedExecutionIds.has(e.ref))
      .map(e => ({
        ref: e.ref as string,
        contract: toContractLabel(e) ?? e.payloadName ?? e.label ?? t('Action'),
        statusColor: attackPathStatusColor(theme, e.status),
        statusLabel: t(statusLabelKey(e.status)),
        subtitle: [e.agentName, e.privilege].filter(Boolean).join(' · '),
        injectId: e.injectId,
        payloadId: e.payloadId,
        executionStatus: e.executionStatus,
      }));
  }, [findingDetail, highlightedExecutionIds, executions, fullDto?.attackPathExecutions, theme, t]);

  // Prevention / detection / vulnerability verdicts shown at the top of the finding panel, read from the
  // finding node's real verdicts (#6912 now persists per-execution expectation statuses; the backend
  // aggregates them worst-of across producers). The DTO already serialises the exact 'success'|'failed'|
  // 'unknown' labels the panel expects, so this is a direct map (anything unrecognised → 'unknown').
  const findingExpectations = useMemo((): FindingExpectations | undefined => {
    if (!findingDetail) {
      return undefined;
    }
    const matchByTypeValue = (n: AttackPathNodeDTO) => n.type === 'FINDING'
      && n.typeFindings === findingDetail.type
      && (n.value ?? n.label) === findingDetail.value;
    let node: AttackPathNodeDTO | undefined;
    for (const pool of [fullDto?.attackPathNodes, dto?.staticAttackPathFindings, dto?.attackPathNodes]) {
      if (!pool) {
        continue;
      }
      node = (selectedFindingId ? pool.find(n => n.id === selectedFindingId) : undefined)
        ?? pool.find(matchByTypeValue);
      if (node?.verdicts) {
        break;
      }
    }
    const norm = (s?: string): ExpectationVerdict => (s === 'success' || s === 'failed' ? s : 'unknown');
    const v = node?.verdicts;
    return {
      prevention: norm(v?.prevention),
      detection: norm(v?.detection),
      vulnerability: norm(v?.vulnerability),
    };
  }, [findingDetail, selectedFindingId, fullDto?.attackPathNodes, dto?.staticAttackPathFindings, dto?.attackPathNodes]);

  // Whether the finding backing the panel is a real finding or an output-only value (a chaining
  // output not persisted as a Finding, ADR-004), so the panel renders its degraded banner. Defaults
  // to a real finding when the flag is absent (older snapshots / non-output nodes).
  const findingDetailIsFinding = useMemo((): boolean => {
    if (!findingDetail) {
      return true;
    }
    const matchByTypeValue = (n: AttackPathNodeDTO) => n.type === 'FINDING'
      && n.typeFindings === findingDetail.type
      && (n.value ?? n.label) === findingDetail.value;
    for (const pool of [fullDto?.attackPathNodes, dto?.staticAttackPathFindings, dto?.attackPathNodes]) {
      if (!pool) {
        continue;
      }
      const node = (selectedFindingId ? pool.find(n => n.id === selectedFindingId) : undefined)
        ?? pool.find(matchByTypeValue);
      if (node && node.isFinding !== undefined) {
        return node.isFinding !== false;
      }
    }
    return true;
  }, [findingDetail, selectedFindingId, fullDto?.attackPathNodes, dto?.staticAttackPathFindings, dto?.attackPathNodes]);

  // The clicked endpoint's findings grouped by type for the side panel; secrets (credentials) masked.
  const endpointFindingGroups = useMemo(() => {
    const byType = new Map<string, string[]>();
    for (const f of endpointFindings) {
      const type = f.typeFindings ?? 'unknown';
      const arr = byType.get(type) ?? [];
      arr.push(maskFindingValue(f.typeFindings, f.value ?? f.label ?? ''));
      byType.set(type, arr);
    }
    return [...byType.entries()].map(([type, values]) => ({
      type,
      values,
    }));
  }, [endpointFindings]);

  // Drawer list: scope to the focused endpoint (in path view) and apply the search, then paginate
  // client-side. So "Discovered Users (1)" in a focused view lists that one user, not the whole set.
  const drawerFilteredItems = useMemo(() => {
    let items = findingsPage?.items ?? [];
    if (pathFinding) {
      items = items.filter(it => it.endpointKey === pathFinding.endpointKey);
    }
    const q = drawerSearch.trim().toLowerCase();
    if (q) {
      items = items.filter(it =>
        (it.value ?? '').toLowerCase().includes(q)
        || (it.endpointKey ?? '').toLowerCase().includes(q));
    }
    // Drop exact duplicates (same finding value on the same endpoint) so a value is never listed
    // twice (e.g. an endpoint IP returned more than once by the backend).
    const seen = new Set<string>();
    items = items.filter((it) => {
      const key = `${it.type ?? ''}|${it.value ?? ''}|${it.endpointKey ?? ''}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
    return items;
  }, [findingsPage, pathFinding, drawerSearch]);

  const drawerPageCount = Math.max(1, Math.ceil(drawerFilteredItems.length / DRAWER_PAGE_SIZE));
  const drawerSafePage = Math.min(drawerPage, drawerPageCount - 1);

  // Whether clearing detailExecutionId would reveal a master panel in the side slot — mirrors the
  // render conditions of the finding / endpoint / injector / category panels below. When it wouldn't
  // (the execution detail was opened directly, e.g. by clicking an action in the focused view), the
  // detail's back arrow is hidden: back would behave exactly like the close cross.
  const executionDetailHasMaster = !!findingDetail || !!selectedNodeId
    || (!!selectedInjectorId && !pathFinding)
    || (drawerCategory !== null && !selectedInjectorId);

  // The selected target's findings empty state is worded for what it actually IS — a team / person /
  // asset group carries its entityKind (endpoints are the null default), and the chain view prefixes
  // node ids with `chain-ep|<depth>|`, so strip that before resolving the graph node.
  const selectedTargetEmptyFindingsLabel = useMemo(() => {
    const rawId = selectedNodeId?.startsWith('chain-ep|')
      ? selectedNodeId.slice(selectedNodeId.indexOf('|', 'chain-ep|'.length) + 1)
      : selectedNodeId;
    const node = (dto?.attackPathNodes ?? []).find(n => n.id === rawId)
      ?? (fullDto?.attackPathNodes ?? []).find(n => n.id === rawId);
    switch (node?.entityKind) {
      case 'TEAM':
        return t('No findings on this team');
      case 'PERSON':
        return t('No findings on this person');
      case 'ASSET_GROUP':
        return t('No findings on this asset group');
      default:
        return node ? t('No findings on this endpoint') : t('No findings on this target');
    }
  }, [selectedNodeId, dto, fullDto, t]);
  const drawerPageItems = useMemo(
    () => drawerFilteredItems.slice(drawerSafePage * DRAWER_PAGE_SIZE, drawerSafePage * DRAWER_PAGE_SIZE + DRAWER_PAGE_SIZE),
    [drawerFilteredItems, drawerSafePage],
  );

  // "Discovered Shares" reads the backend share counter; "Captured Files" the native `file` counter.
  // Each finding type keeps its own stored type, so shares and files are never folded together.
  const sharesCount = counters?.shares ?? 0;
  const filesCount = counters?.files ?? 0;

  const focusedSharesCount = focusedEndpoint?.findingCounts?.share ?? 0;
  const focusedFilesCount = focusedEndpoint?.findingCounts?.file ?? 0;
  // Distinct target entities actually drawn in the path: every ASSET node (endpoints AND the injects'
  // team/asset-group recipients), minus the human-in-the-loop PERSON leaves we deliberately hide (a
  // team's individual mail recipients are execution rows the backend's `distinct targetKey` counter
  // still counts, which is why that raw count over-reports what the graph shows). Counted off the
  // collapsed dto, which carries every ASSET node even when the view clusters them, so large runs stay
  // correct.
  const reachedTargetsCount = useMemo(
    () => (dto?.attackPathNodes ?? []).filter(n => n.type === 'ASSET' && n.entityKind !== 'PERSON').length,
    [dto],
  );
  const effectiveCounters = pathFinding
    ? {
        endpoints: 1,
        credentials: focusedEndpoint?.findingCounts?.credentials ?? 0,
        users: (focusedEndpoint?.findingCounts?.username ?? 0) + (focusedEndpoint?.findingCounts?.admin_username ?? 0),
        cves: focusedEndpoint?.findingCounts?.cve ?? 0,
      }
    : {
        endpoints: reachedTargetsCount,
        credentials: counters?.credentials ?? 0,
        users: counters?.users ?? 0,
        cves: counters?.cves ?? 0,
      };

  const cards: FindingCard[] = useMemo(() => {
    const base: FindingCard[] = [
      {
        key: 'endpoints',
        label: t('Targets reached'),
        icon: <TrackChangesOutlined fontSize="small" />,
        count: effectiveCounters.endpoints,
      },
      {
        key: 'shares',
        label: t('Discovered Shares'),
        icon: <FolderNetworkOutline fontSize="small" />,
        count: pathFinding ? focusedSharesCount : sharesCount,
      },
      {
        key: 'files',
        label: t('Captured Files'),
        icon: <InsertDriveFileOutlined fontSize="small" />,
        count: pathFinding ? focusedFilesCount : filesCount,
      },
      {
        key: 'credentials',
        label: t('Captured Credentials'),
        icon: <VpnKeyOutlined fontSize="small" />,
        count: effectiveCounters.credentials,
      },
      {
        key: 'users',
        label: t('Discovered Users'),
        icon: <GroupOutlined fontSize="small" />,
        count: effectiveCounters.users,
      },
      {
        key: 'cves',
        label: t('Detected CVEs'),
        icon: <BugReportOutlined fontSize="small" />,
        count: effectiveCounters.cves,
      },
    ];
    // Data-driven extras: any finding type present on the endpoints that no curated card covers gets its
    // own card automatically (summed from per-endpoint findingCounts), so a finding type added later —
    // e.g. exposed via a new event field — surfaces with zero code change. Scoped to the focused
    // endpoint in the finding-path view, else aggregated across the whole graph.
    const sourceNodes = pathFinding && focusedEndpoint
      ? [focusedEndpoint]
      : (dto?.attackPathNodes ?? []).filter(n => n.type === 'ASSET');
    const extraTotals = new Map<string, number>();
    sourceNodes.forEach((n) => {
      Object.entries(n.findingCounts ?? {}).forEach(([type, count]) => {
        if (type && !COVERED_FINDING_TYPES.has(type) && (count ?? 0) > 0) {
          extraTotals.set(type, (extraTotals.get(type) ?? 0) + (count ?? 0));
        }
      });
    });
    const extras: FindingCard[] = [...extraTotals.entries()]
      // Count first, type key as a tie-break: the header caps how many of these show inline, so ties
      // must not fall back to Map insertion order or which types stay visible would shift across
      // renders.
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([type, count]) => {
        const noun = t(findingCategoryNoun(type));
        return {
          key: type,
          label: noun.charAt(0).toUpperCase() + noun.slice(1),
          icon: <LabelOutlined fontSize="small" />,
          count,
          // Marks a data-driven card so the header can cap how many of them it shows inline and
          // collapse the rest behind one "+N types" stat (the band divides its width equally between
          // stats, so an unbounded list of types clips every caption to a single letter).
          extra: true,
        };
      });
    return [...base, ...extras];
  }, [t, effectiveCounters, pathFinding, focusedSharesCount, sharesCount, focusedFilesCount, filesCount, dto, focusedEndpoint]);

  // Click a summary stat: focus the graph on that finding type and, for finding categories, open the
  // contextual side panel listing the (deduplicated, masked) items. Clicking again clears the focus.
  const onCardClick = (card: FindingCard) => {
    const next = activeCard === card.key ? null : card.key;
    setActiveCard(next);
    if (next && next !== 'endpoints') {
      // The category panel takes over the side slot, so dismiss whichever detail panel occupied it
      // (mirrors the old overlay drawer, which visually covered them).
      setSelectedNodeId(null);
      setSelectedFindingId(null);
      setSelectedInjectorId(null);
      setFindingDetail(null);
      setDetailExecutionId(null);
      openFindingsDrawer(next, card.label);
    } else {
      setDrawerCategory(null);
    }
  };

  const clearFocus = () => {
    setActiveCard(null);
    setDrawerCategory(null);
  };

  // Clicking the empty grid is the universal escape: it dismisses whichever side panel is open
  // (endpoint / finding / injector / category / execution detail) and clears the node selection and
  // highlight — the panel's cross must not be the only way out. The focused-path mode is deliberately
  // kept: it has its own explicit escape in the header.
  const onCanvasBackgroundClick = () => {
    setSelectedNodeId(null);
    setSelectedFindingId(null);
    setSelectedInjectorId(null);
    setInjectorExecutions([]);
    setInjectorFindingGroups([]);
    setFindingDetail(null);
    setDetailExecutionId(null);
    setDetail(null);
    setActiveCard(null);
    setDrawerCategory(null);
    setHighlightedExecutionIds(new Set());
    setFocusRequest(null);
  };

  // Leave the focused finding-path view and restore the full clustered graph (fitted).
  const clearPathFocus = () => {
    setPathFinding(null);
    setSelectedNodeId(null);
    setSelectedFindingId(null);
    setSelectedInjectorId(null);
    setHighlightedExecutionIds(new Set());
    setFitNonce(n => n + 1);
  };

  // "Top chokepoints" card popover: the ranked list of the most-exposed endpoints.
  const [chokepointExplainOpen, setChokepointExplainOpen] = useState(false);

  // Graph (node-link) vs Table (sortable/exportable list of exposed endpoints) view of the same data.
  const [view, setView] = useState<'graph' | 'table'>('graph');

  // Fullscreen: expand the whole view (cards strip + graph) over the viewport for room to navigate a large
  // chain, keeping the finding cards for context. Escape leaves it.
  const [fullscreen, setFullscreen] = useState(false);
  useEffect(() => {
    if (!fullscreen) {
      return undefined;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setFullscreen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [fullscreen]);

  // Free-text search input (endpoint / injector / finding type), used by the search autocomplete.
  const [searchInput, setSearchInput] = useState('');

  // Focus a chokepoint endpoint: redraw the graph as the focused endpoint path (injectors -> endpoint
  // -> its finding clusters). Shared by the table rows, the chokepoint dialog and the search picks.
  // The side panel is deliberately NOT opened: the point of the click is the focused graph, and the
  // panel would immediately take a third of the width back. It stays one node click away.
  const focusChokepoint = (c: {
    nodeId: string;
    ref: string;
    label: string;
  }) => {
    // Ensure the graph is showing: the strip chip and the popover card are reachable from the table
    // view too, and the focused path only renders in the graph view.
    setView('graph');
    setChokepointExplainOpen(false);
    setActiveCard(null);
    setDrawerCategory(null);
    setSelectedFindingId(null);
    setSelectedInjectorId(null);
    setFocusRequest(null);
    setPathFinding({
      endpointNodeId: c.nodeId,
      endpointKey: c.ref,
      type: '',
      value: '',
    });
    setFitNonce(n => n + 1);
    onEndpointClick(c.nodeId, c.ref, c.label, { openPanel: false });
  };

  // Keep the selected value in the options so MUI does not warn when the current simulation has no
  // attack-path summary row yet.
  const selectedRow: AttackPathSimSummaryRow | null = simulationId
    ? (simulations.find(s => s.simulationId === simulationId) ?? { simulationId })
    : null;
  const pickerOptions = orderSimulationPickerOptions(
    simulations,
    selectedRow,
    simId => metaById.get(simId ?? '')?.exercise_start_date ?? '',
  );

  // Search entries: every endpoint (by hostname/ip), injector, and finding category present in the
  // graph. Built from the already-loaded DTO — no extra fetch.
  const searchOptions = useMemo<SearchOption[]>(() => {
    const opts: SearchOption[] = [];
    (dto?.attackPathNodes ?? []).forEach((n) => {
      if (!n.id) {
        return;
      }
      if (n.type === 'ASSET') {
        opts.push({
          kind: 'endpoint',
          label: n.hostname || n.label || n.ref || n.id,
          sub: n.ip,
          nodeId: n.id,
          ref: n.ref ?? n.id,
        });
      } else if (n.type === 'INJECTOR') {
        opts.push({
          kind: 'injector',
          label: n.label || n.id,
          nodeId: n.id,
        });
      }
    });
    cards
      .filter(c => c.key !== 'endpoints' && c.count > 0)
      .forEach(c => opts.push({
        kind: 'finding',
        label: c.label,
        card: c,
      }));
    return opts;
  }, [dto, cards]);

  const searchGroupLabel = (kind: SearchOption['kind']): string => {
    if (kind === 'endpoint') {
      return t('Assets');
    }
    if (kind === 'injector') {
      return t('Injectors');
    }
    return t('Finding types');
  };

  // Adapt the graph to the selected search entry: focus an endpoint's path, highlight an injector, or
  // open a finding category's drawer. All route through the graph view.
  const onSearchSelect = (opt: SearchOption | null) => {
    if (!opt) {
      return;
    }
    if (opt.kind === 'endpoint' && opt.nodeId && opt.ref) {
      focusChokepoint({
        nodeId: opt.nodeId,
        ref: opt.ref,
        label: opt.label,
      });
    } else if (opt.kind === 'injector' && opt.nodeId) {
      setView('graph');
      onInjectorSelect(opt.nodeId, opt.label);
    } else if (opt.kind === 'finding' && opt.card) {
      setView('graph');
      onCardClick(opt.card);
    }
  };

  // The clustered builder always emits the endpoint-cluster hub, so `nodes` is never empty even with no
  // real data; treat the graph as empty unless it has an actual injector/endpoint/finding to show (so the
  // empty-state — including the "run in progress" message — appears instead of a lone "+0" hub).
  const graphHasContent = nodes.some(n =>
    n.type === AP_FLOW_NODE_TYPE.injector
    || n.type === AP_FLOW_NODE_TYPE.asset
    || n.type === AP_FLOW_NODE_TYPE.finding,
  );

  // Scenario context has runs to pick from; when it has none yet, the empty-state offers to launch one.
  const scenarioHasNoSims = showPicker && simulations.length === 0;
  // A run still in progress hasn't produced anything yet — say so (and the graph live-refreshes), rather
  // than showing the "no data" message that suggests something is wrong.
  const runInProgress = selectedRunStatus === 'RUNNING' || selectedRunStatus === 'PAUSED';
  const emptyStateMessage = (() => {
    if (scenarioHasNoSims) {
      return hideLaunchCta
        ? t('The autonomous run has not produced attack-path data yet. It appears live here as the AI executes.')
        : t('This scenario has no simulation with attack-path data yet. Launch one to reveal its attack path.');
    }
    if (runInProgress) {
      return t('Simulation running — waiting for the first inject executions…');
    }
    if (showPicker) {
      return t('No attack-path data for this simulation. Select a simulation with attack-path data above.');
    }
    return t('No attack-path data for this simulation.');
  })();
  // A short title above the detailed message, so the empty-state matches the platform's zero-state
  // language (title + explanation) rather than a lone sentence.
  const emptyStateTitle = runInProgress ? t('Simulation running') : t('No attack path to display');
  const [launching, setLaunching] = useState(false);
  // Empty-state CTA (scenario context): instantiate + start a fresh simulation from this scenario and
  // jump to it — same flow as the scenario header's "Launch now".
  const handleLaunchFromScenario = useCallback(() => {
    if (!scenarioId) {
      return;
    }
    setLaunching(true);
    createRunningExerciseFromScenario(scenarioId)
      .then((res) => {
        MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
        navigate(`${SIMULATION_BASE_URL}/${res.data.exercise_id}`);
      })
      .catch(() => {
        MESSAGING$.notifyError(t('Error while launching the simulation'));
        setLaunching(false);
      });
  }, [scenarioId, navigate, t]);

  return (
    <Box
      ref={rootRef}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // The view fills the viewport exactly (measured, not guessed), so the page never grows a
        // vertical scrollbar. Fullscreen lifts it out of the page flow to cover the whole screen.
        gap: 1,
        ...(fullscreen
          ? {
              position: 'fixed',
              inset: 0,
              zIndex: theme.zIndex.drawer + 2,
              height: '100vh',
              padding: 2,
              // A real colour (not a theme token string, which raw CSS ignores): fullscreen must be
              // opaque over the page behind it.
              backgroundColor: theme.palette.background.default,
            }
          : { height: viewHeight ?? AP_VIEW_HEIGHT }),
      }}
    >
      <GlobalStyles styles={AP_GLOBAL_STYLES} />
      {/* Live updates are announced once per batch, off-screen: sighted users see the nodes appear. */}
      <Box aria-live="polite" aria-atomic="true" sx={AP_VISUALLY_HIDDEN}>
        {liveSummary}
      </Box>

      <AttackPathHeader
        showPicker={showPicker}
        pickerOptions={pickerOptions}
        selectedRow={selectedRow}
        labelFor={labelFor}
        onSimulationChange={setSimulationId}
        hasCardFocus={!!activeCard}
        onClearFocus={clearFocus}
        hasPathFocus={!!pathFinding}
        onClearPathFocus={clearPathFocus}
        freshness={freshness}
        freshnessLabel={freshnessLabel}
        freshnessTitle={freshnessTitle}
        cards={cards}
        activeCard={activeCard}
        onCardClick={onCardClick}
        chokepointCount={pathFinding ? 0 : chokepoints.length}
        chokepointOpen={chokepointExplainOpen}
        onChokepointClick={() => setChokepointExplainOpen(true)}
        view={view}
        onViewChange={setView}
        fullscreen={fullscreen}
        onToggleFullscreen={() => setFullscreen(f => !f)}
        searchOptions={searchOptions}
        searchInput={searchInput}
        onSearchInputChange={setSearchInput}
        onSearchSelect={onSearchSelect}
        searchGroupLabel={searchGroupLabel}
      />
      {/* Chokepoint scoring explainer opened by the stats-bar chokepoint card. */}
      {!pathFinding && chokepoints.length > 0 && (
        <ScoreExplainerDialog
          open={chokepointExplainOpen}
          onClose={() => setChokepointExplainOpen(false)}
          title={t('How chokepoints are scored')}
          score={chokepoints[0]?.score ?? null}
          scoreColor={criticalityColor(chokepoints[0]?.criticality)}
          bandLabel={t(CRITICALITY_LABEL[chokepoints[0]?.criticality ?? 'UNKNOWN'] ?? CRITICALITY_LABEL.UNKNOWN)}
          verdict={t('{label} is the most exposed endpoint — fixing its findings closes the most attack paths.', { label: chokepoints[0]?.label ?? '' })}
          measures={t('A chokepoint is the endpoint where fixing findings closes the most attack paths. The score weights an endpoint\'s findings by its business criticality, so a critical host outranks a noisier but less important one.')}
          formula={(
            <>
              {t('score')}
              {' = '}
              {t('findings')}
              {' × '}
              {t('criticality weight')}
            </>
          )}
          breakdownTitle={t('Most exposed assets')}
          breakdown={chokepoints.map((c, i): ScoreBreakdownRow => ({
            key: c.nodeId,
            label: `${i + 1}. ${c.label}`,
            valueLabel: `${c.score}`,
            segments: [{
              widthPct: Math.min(100, Math.max(6, ((c.score ?? 0) / (chokepoints[0]?.score || 1)) * 100)),
              color: criticalityColor(c.criticality),
            }],
            sublabel: [
              c.ip,
              `${c.findings} × ${c.weight} (${t(CRITICALITY_LABEL[c.criticality ?? 'UNKNOWN'] ?? CRITICALITY_LABEL.UNKNOWN)})`,
            ].filter(Boolean).join(' · '),
            onClick: () => focusChokepoint(c),
          }))}
          bandsTitle={t('Criticality weights')}
          bands={(['VERY_HIGH', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']).map(k => ({
            range: `×${CRITICALITY_WEIGHT[k]}`,
            label: t(CRITICALITY_LABEL[k]),
            color: criticalityColor(k),
            desc: t('Weighs this endpoint\'s findings ×{weight} in the score.', { weight: `${CRITICALITY_WEIGHT[k]}` }),
          }))}
        />
      )}

      <Box sx={{
        display: 'flex',
        flex: 1,
        minHeight: 0,
        gap: 1,
      }}
      >
        {view === 'table' && (
          <Paper
            variant="outlined"
            sx={{
              flex: 1,
              minWidth: 0,
              overflow: 'hidden',
              display: 'flex',
            }}
          >
            <AttackPathTableView
              rows={tableRows}
              typeColumns={endpointTypeColumns}
              chokepointTopN={CHOKEPOINT_TOP_N}
              onRowFocus={row => focusChokepoint(row)}
            />
          </Paper>
        )}
        {view === 'graph' && (
          <Paper
            variant="outlined"
            sx={{
              flex: 1,
              minWidth: 0,
              position: 'relative',
            }}
          >
            {(loading || chainLoading) && <Loader />}
            {!loading && forbidden && (
              <Alert severity="warning" sx={{ m: 2 }}>
                {t('You do not have access to this simulation\'s attack path.')}
              </Alert>
            )}
            {!loading && !forbidden && error && (
              <Alert severity="error" sx={{ m: 2 }}>
                {t('Failed to load the attack-path graph. Check the simulation or reload the page.')}
              </Alert>
            )}
            {!loading && !chainLoading && !forbidden && !error && !graphHasContent && (
              // Inset the placeholder inside the (relative) graph Paper so its dashed frame sits
              // within the Paper's own outline instead of doubling up against it.
              <Box sx={{
                position: 'absolute',
                inset: 0,
                p: 1.5,
              }}
              >
                <EmptyPlaceholder
                  icon={<AccountTreeOutlined />}
                  title={emptyStateTitle}
                  message={emptyStateMessage}
                  action={scenarioHasNoSims && scenarioId && !hideLaunchCta
                    ? (
                        <Button
                          variant="contained"
                          startIcon={<PlayArrowOutlined />}
                          onClick={handleLaunchFromScenario}
                          disabled={launching}
                        >
                          {t('Launch a simulation')}
                        </Button>
                      )
                    : undefined}
                />
              </Box>
            )}
            {!loading && !chainLoading && !forbidden && !error && graphHasContent && (
              <AttackPathCanvas
                nodes={nodes}
                edges={graphEdges}
                enterNodeIds={enterNodeIds}
                onEndpointClick={onEndpointClick}
                onClusterClick={onClusterClick}
                onEndpointClusterClick={onEndpointClusterClick}
                onFindingClusterClick={onFindingClusterClick}
                onFindingSelect={onFindingSelect}
                onInjectorSelect={onInjectorSelect}
                onBackgroundClick={onCanvasBackgroundClick}
                focusRequest={focusRequest}
                fitRequest={fitNonce}
                anchorRequest={anchorRequest}
                pursuitRequest={pathFinding ? null : pursuitRequest}
                pursuitActive={pursuitActive && !pathFinding}
                showMiniMap={!pathFinding && nodes.length > 40}
                legend={<AttackPathLegend collapseSignal={legendCollapseNonce} />}
              />
            )}
          </Paper>
        )}

        {/* Resizable side slot, shared by BOTH views: a single panel shows at a time (mutually
            exclusive); the handle on its left edge drags the width, and the content (flex:1)
            reflows. A table-row click opens the endpoint panel here without leaving the table. */}
        {(!!detailExecutionId || !!findingDetail || !!selectedNodeId || (!pathFinding && !!selectedInjectorId) || drawerCategory !== null) && (
          <Box sx={{
            position: 'relative',
            display: 'flex',
            flexShrink: 0,
            width: panelWidth,
            minWidth: `${AP_PANEL_MIN_WIDTH}px`,
          }}
          >
            <Box
              role="separator"
              aria-orientation="vertical"
              aria-label={t('Resize panel')}
              onMouseDown={onResizeStart}
              sx={{
                'flex': '0 0 auto',
                'width': 6,
                'cursor': 'col-resize',
                'marginRight': 1,
                'borderRadius': 3,
                'backgroundColor': 'divider',
                'transition': theme.transitions.create('background-color'),
                '&:hover': { backgroundColor: 'primary.main' },
              }}
            />
            <Box sx={{
              flex: 1,
              minWidth: 0,
              display: 'flex',
            }}
            >
              {/* Master→detail in a single drawer: while an execution detail is open it REPLACES the
                endpoint/finding master panel (below), and its back arrow returns here. */}
              {findingDetail && !detailExecutionId && (
                <FindingDetailPanel
                  value={maskFindingValue(findingDetail.type, findingDetail.value)}
                  type={findingDetail.type}
                  simulationId={simulationId}
                  endpointLabel={findingEndpoint?.hostname || findingEndpoint?.label || findingEndpoint?.ref || pathFinding?.endpointKey || t('Endpoint')}
                  endpointSub={[findingEndpoint?.ip, findingEndpoint?.platform].filter(Boolean).join(' · ')}
                  endpointName={findingEndpoint?.hostname}
                  expectations={findingExpectations}
                  isFinding={findingDetailIsFinding}
                  actions={producingActions}
                  activeRef={detailExecutionId}
                  onSelect={openExecutionDetail}
                  onClose={() => {
                    // Clicking a finding in the clustered view also selects its endpoint (to load the
                    // feed), so clear both here — otherwise closing the finding panel would just reveal
                    // the endpoint panel and look like it never closed.
                    setFindingDetail(null);
                    setSelectedNodeId(null);
                    setDetailExecutionId(null);
                  }}
                />
              )}

              {!findingDetail && selectedNodeId && !detailExecutionId && (
                <EndpointDetailPanel
                  simulationId={simulationId}
                  endpointLabel={selectedLabel || t('Endpoint')}
                  emptyFindingsLabel={selectedTargetEmptyFindingsLabel}
                  findingsLoading={endpointFindingsLoading}
                  findingGroups={endpointFindingGroups}
                  executions={executions}
                  totalExecutions={endpointExecTotal}
                  onShowMore={loadMoreEndpointExecutions}
                  loadingMore={endpointExecLoadingMore}
                  highlightedExecutionIds={highlightedExecutionIds}
                  registerRow={(id, el) => {
                    if (el) {
                      feedRowRefs.current.set(id, el);
                    } else {
                      feedRowRefs.current.delete(id);
                    }
                  }}
                  onSelectExecution={openExecutionDetail}
                  execStatusLabel={status => t(statusLabelKey(status))}
                  onClose={() => {
                    setSelectedNodeId(null);
                    setDetailExecutionId(null);
                  }}
                />
              )}

              {/* Injector master panel: the same component as the endpoint panel — its findings (attributed
                to this injector's executions) and its contracts/executions. One click opens their
                Result / Execution details / Remediation detail (the global command it ran). */}
              {!pathFinding && !findingDetail && !selectedNodeId && selectedInjectorId && !detailExecutionId && (
                <EndpointDetailPanel
                  simulationId={simulationId}
                  endpointLabel={injectorPanelLabel || t('Injector')}
                  emptyFindingsLabel={t('No findings from this action')}
                  findingsLoading={injectorFindingsLoading}
                  findingGroups={injectorFindingGroups}
                  executions={injectorExecutions}
                  totalExecutions={injectorExecTotal}
                  highlightedExecutionIds={highlightedExecutionIds}
                  registerRow={() => {}}
                  onSelectExecution={openExecutionDetail}
                  execStatusLabel={status => t(statusLabelKey(status))}
                  onClose={() => {
                    setSelectedInjectorId(null);
                    setInjectorExecutions([]);
                    setInjectorFindingGroups([]);
                    setDetailExecutionId(null);
                  }}
                />
              )}

              {/* Contextual category panel (portscan, credentials, cve...): shows when no
                      detail panel occupies the slot, so a node click temporarily replaces it and
                      closing that detail brings the list back. */}
              {drawerCategory !== null && !findingDetail && !selectedNodeId && !selectedInjectorId && !detailExecutionId && (
                <CategoryFindingsPanel
                  label={drawerLabel}
                  count={drawerFilteredItems.length}
                  loading={findingsLoading}
                  items={drawerPageItems}
                  search={drawerSearch}
                  onSearchChange={(v) => {
                    setDrawerSearch(v);
                    setDrawerPage(0);
                  }}
                  page={drawerSafePage}
                  pageCount={drawerPageCount}
                  onPageChange={setDrawerPage}
                  loadedCount={(findingsPage?.items ?? []).length}
                  totalCount={findingsPage?.total ?? 0}
                  endpointNameFor={key => (key ? endpointLabelByRef.get(key) : undefined)}
                  onItemClick={onFindingItemClick}
                  onClose={clearFocus}
                />
              )}

              {detailExecutionId && (
                <ExecutionResultTerminalPanel
                  loading={detailLoading}
                  detail={detail}
                  endpointLabel={detail?.endpointKey ? endpointLabelByRef.get(detail.endpointKey) : undefined}
                  // Back returns to the master panel (endpoint/finding/injector/category) it was opened
                  // from; close dismisses the whole drawer. When the detail was opened DIRECTLY (e.g.
                  // clicking an action in the focused view), no master would show after clearing it, so
                  // the arrow is hidden — back would otherwise just duplicate the close cross.
                  onBack={executionDetailHasMaster ? () => setDetailExecutionId(null) : undefined}
                  onClose={() => {
                    setDetailExecutionId(null);
                    setSelectedNodeId(null);
                    setSelectedInjectorId(null);
                    setInjectorExecutions([]);
                    setInjectorFindingGroups([]);
                    setFindingDetail(null);
                  }}
                  onOpenInject={(detail as { injectId?: string } | null)?.injectId
                  // The inject belongs to the run, not the scenario: a relative `../injects/…` would point
                  // at the scenario in scenario context (which has no such inject → 404). Always target the
                  // selected simulation. A payload-backed inject opens on its Overview; a network injector
                  // (no payload) opens straight on its Execution details, where its traces live.
                    ? () => {
                        const d = detail as {
                          injectId?: string;
                          payloadId?: string;
                        };
                        const base = `${SIMULATION_BASE_URL}/${simulationId}/injects/${d.injectId}`;
                        navigate(d.payloadId ? base : `${base}/execution_details`);
                      }
                    : undefined}
                />
              )}
            </Box>
          </Box>
        )}
      </Box>

    </Box>
  );
};

export default SimulationAttackPath;
