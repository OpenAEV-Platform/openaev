import { AccountTreeOutlined, BugReportOutlined, DnsOutlined, GroupOutlined, HelpOutline, InsertDriveFileOutlined, LabelOutlined, LocalFireDepartment, PlayArrowOutlined, SearchOutlined, TableRowsOutlined, VpnKeyOutlined } from '@mui/icons-material';
import { Alert, Autocomplete, Box, Button, ButtonBase, Chip, Paper, Popover, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { ReactFlowProvider } from '@xyflow/react';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchAttackPathGraph, fetchAttackPathSimulations, fetchEndpointFindings, fetchEndpointRelations, fetchExecutionDetail, fetchFindingsByCategory, fetchSimulationsMetaById } from '../../../../../actions/attack-path/attack-path-actions';
import { createRunningExerciseFromScenario } from '../../../../../actions/scenarios/scenario-actions';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { SIMULATION_BASE_URL } from '../../../../../constants/BaseUrls';
import type { AttackPathDTO, AttackPathEdges, AttackPathExecutionDetailDTO, AttackPathFindingItemDTO, AttackPathFindingPageDTO, AttackPathNodeDTO, AttackPathSimSummaryRow, ExerciseSimple } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import attackPathStatusColor, { attackPathChokepointColor } from './attack-path-colors';
import { AP_ALL_ENDPOINTS, AP_FLOW_NODE_TYPE, applyFindingFilter, type AttackPathFindingFilter, type AttackPathFlowEdge, type AttackPathFlowNode, buildCausalChainFlow, buildCausalEdges, buildClusteredAttackPathFlow, buildFindingPathFlow, buildKillChainMeta, ENDPOINT_BATCH_SIZE, FILTER_TO_FINDING_TYPES, FINDING_BATCH_SIZE, findingCategoryNoun, maskFindingValue, type PathFinding } from './attack-path-flow-helpers';
import AttackPathFlow, { type AttackPathFocusRequest } from './AttackPathFlow';
import AttackPathLegend from './AttackPathLegend';
import AttackPathTableView, { type AttackPathEndpointRow } from './AttackPathTableView';
import EndpointDetailPanel from './EndpointDetailPanel';
import ExecutionResultTerminalPanel from './ExecutionResultTerminalPanel';
import FindingDetailPanel, { type FindingExpectations, type ProducingAction } from './FindingDetailPanel';

// A hot endpoint can have many executions; the read is bounded to the one endpoint, but the side
// panel still renders a list, so cap it (the backend /relations read would be paginated in prod).
const EXEC_DISPLAY_CAP = 100;

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

// Finding types already surfaced by the curated summary cards (endpoints/files/credentials/users/cves).
// Every OTHER type present in the data gets an auto-generated card, so a new finding type needs no code.
const COVERED_FINDING_TYPES = new Set(['share', 'credentials', 'username', 'admin_username', 'cve']);

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
  share: 'files',
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

interface FindingCard {
  key: AttackPathFindingFilter;
  label: string;
  icon: ReactNode;
  count: number;
  hint?: string;
}

// One entry of the graph search box: an endpoint, an injector, or a finding category. Selecting one
// adapts the graph (focus an endpoint path, highlight an injector, or open a finding-type drawer).
interface SearchOption {
  kind: 'endpoint' | 'injector' | 'finding';
  label: string;
  sub?: string;
  nodeId?: string;
  ref?: string;
  card?: FindingCard;
}

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
}

const SimulationAttackPath = ({ scenarioExerciseIds, scenarioId }: SimulationAttackPathProps) => {
  const { exerciseId } = useParams() as { exerciseId?: string };
  // Scenario context lists several runs to pick from; simulation context is locked to its own run.
  const showPicker = scenarioExerciseIds !== undefined;
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const navigate = useNavigate();

  const [simulationId, setSimulationId] = useState(exerciseId ?? '');
  const [simulations, setSimulations] = useState<AttackPathSimSummaryRow[]>([]);
  const [metaById, setMetaById] = useState<Map<string, ExerciseSimple>>(new Map());
  const [dto, setDto] = useState<AttackPathDTO | null>(null);
  // Per-injector kill-chain metadata (dependsOn / consumedFindingKeys) for the causal overlay. The
  // collapsed DTO (setDto) omits the per-execution kill-chain fields (applyKillChain is full-mode only),
  // so both the causal meta and the causal-chain layout are sourced from a separate, size-gated full-mode
  // fetch (see the effect below), kept in their own state.
  const [killChainMeta, setKillChainMeta] = useState<ReturnType<typeof buildKillChainMeta>>(new Map());
  // Full-mode graph (executions + produced findings), fetched only for small runs (see the gated effect).
  // Drives the causal execution-chain layout; null for large runs (fall back to the aggregated view).
  const [fullDto, setFullDto] = useState<AttackPathDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  // Card focus: a summary card mapped to its finding types (dim everything off that path).
  const [activeCard, setActiveCard] = useState<AttackPathFindingFilter | null>(null);

  // Per-injector progressive endpoint reveal: injector id -> number of endpoints shown (0 = collapsed).
  const [endpointBatch, setEndpointBatch] = useState<Map<string, number>>(new Map());
  // Finding-cluster drill-down: which finding clusters are expanded, their fetched (deduped) findings,
  // and how many are revealed (batched), keyed by the finding-cluster node id.
  const [expandedFindingClusters, setExpandedFindingClusters] = useState<Set<string>>(new Set());
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
  } | null>(null);
  const [executions, setExecutions] = useState<AttackPathNodeDTO[]>([]);
  const [endpointRelationEdges, setEndpointRelationEdges] = useState<AttackPathEdges[]>([]);
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
  const [highlightedExecutionIds, setHighlightedExecutionIds] = useState<Set<string>>(new Set());
  const feedRowRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // Focused "attack path to this finding" view: when set, the graph shows only the injector(s) ->
  // endpoint -> finding path that produced the finding picked in the drawer. fitNonce bumps to frame it.
  const [pathFinding, setPathFinding] = useState<PathFinding | null>(null);
  const [fitNonce, setFitNonce] = useState(0);
  // Bumped whenever a side panel/drawer opens so the graph legend folds away (reopenable by the user).
  const [legendCollapseNonce, setLegendCollapseNonce] = useState(0);

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

  // Monotonic tokens to drop stale async responses when the user switches simulation or endpoint
  // quickly: only the latest request may write state.
  const graphSeq = useRef(0);
  const endpointSeq = useRef(0);

  // Always collapsed-first: the graph auto-loads on simulation change, clustered by injector.
  const load = useCallback(() => {
    if (!simulationId) {
      return;
    }
    const seq = graphSeq.current + 1;
    graphSeq.current = seq;
    // A simulation switch also invalidates any in-flight endpoint read from the previous graph.
    endpointSeq.current += 1;
    setLoading(true);
    setError(false);
    setForbidden(false);
    setEndpointBatch(new Map());
    setExpandedFindingClusters(new Set());
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
    fetchAttackPathGraph(simulationId, 'collapsed')
      .then((r) => {
        if (seq === graphSeq.current) {
          setDto(r.data);
        }
      })
      .catch((err) => {
        if (seq !== graphSeq.current) {
          return;
        }
        // Clear the previous simulation's graph so a failed load shows an error, not stale data.
        setDto(null);
        if (err?.status === 403) {
          setForbidden(true);
        } else {
          setError(true);
        }
      })
      .finally(() => {
        if (seq === graphSeq.current) {
          setLoading(false);
        }
      });
  }, [simulationId]);

  useEffect(() => {
    load();
  }, [load]);

  // Causal overlay data source (issue 6647). The rendered graph is collapsed, which omits the
  // per-execution kill-chain fields, so we fetch the full graph once — only to derive the per-injector
  // meta — and gate it on the simulation's execution count (mirrors the backend collapse-threshold) so a
  // large run never pulls a full payload just for the overlay. When the size is unknown or above the
  // ceiling, the overlay is simply absent (additive: the graph is unchanged).
  useEffect(() => {
    if (!simulationId) {
      setKillChainMeta(new Map());
      setFullDto(null);
      return undefined;
    }
    const row = simulations.find(s => s.simulationId === simulationId);
    // No summary row (rows still loading, or a simulation with no attack-path data): no overlay, and
    // clear any state carried over from a previously viewed simulation so it never leaks onto this one.
    if (!row) {
      setKillChainMeta(new Map());
      setFullDto(null);
      return undefined;
    }
    if ((row.executionCount ?? 0) > CAUSAL_META_MAX_EXECUTIONS) {
      setKillChainMeta(new Map());
      setFullDto(null);
      return undefined;
    }
    let cancelled = false;
    fetchAttackPathGraph(simulationId, 'full')
      .then((r) => {
        if (!cancelled) {
          setKillChainMeta(buildKillChainMeta(r.data));
          setFullDto(r.data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setKillChainMeta(new Map());
          setFullDto(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [simulationId, simulations]);

  // Load the picker options once (simulations that have attack-path data in this tenant), then
  // resolve real simulations' date + name so the picker reads dates instead of raw ids. In scenario
  // context the list is narrowed to the scenario's own runs and the view defaults to the most recent.
  useEffect(() => {
    fetchAttackPathSimulations()
      .then((r) => {
        const all = r.data ?? [];
        // Scenario context: only this scenario's runs that actually have attack-path data. Simulation
        // context: every run in the tenant (the picker is hidden, so this is just the summary source).
        const rows = scenarioExerciseIds
          ? all.filter(s => !!s.simulationId && scenarioExerciseIds.includes(s.simulationId))
          : all;
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
  const labelFor = useCallback((simId?: string): string => {
    if (!simId) {
      return '';
    }
    const meta = metaById.get(simId);
    if (meta?.exercise_name) {
      return meta.exercise_start_date
        ? `${fldt(meta.exercise_start_date)} · ${meta.exercise_name}`
        : meta.exercise_name;
    }
    return simId;
  }, [metaById, fldt]);

  // Click a real endpoint (only visible once its injector cluster is expanded): load its own findings
  // (grouped in the side panel) and its executions. Stale responses are dropped.
  const onEndpointClick = useCallback((nodeId: string, ref?: string, label?: string) => {
    setSelectedNodeId(nodeId);
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
    fetchEndpointRelations(simulationId, ref)
      .then((r) => {
        if (seq === endpointSeq.current) {
          setExecutions(r.data.executions ?? []);
          setEndpointRelationEdges(r.data.edges ?? []);
        }
      })
      .catch(() => {
        if (seq === endpointSeq.current) {
          setExecutions([]);
          setEndpointRelationEdges([]);
        }
      });
  }, [simulationId]);

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
    }
  }, [endpointBatch]);

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
    (clusterId: string, typeFindings: string | undefined, injectorId: string | undefined, endpointRef: string | undefined, kind: 'header' | 'overflow') => {
      if (kind === 'overflow') {
        setFindingBatch(prev => new Map(prev).set(clusterId, (prev.get(clusterId) ?? 0) + FINDING_BATCH_SIZE));
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
      setSelectedFindingId(clusterId);
      setFindingDetail(null);
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
    [expandedFindingClusters, findingsByCluster, fetchClusterFindings, simulationId, pathFinding],
  );

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
      setSelectedFindingId(null);
      setSelectedInjectorId(null);
      setFocusRequest(null);
      setPathFinding({
        endpointNodeId: item.endpointNodeId,
        endpointKey: item.endpointKey,
        type: item.type ?? '',
        value: item.value ?? '',
      });
      setFitNonce(n => n + 1);
      // Use the endpoint's friendly label (hostname) for the panel title, like a direct node click.
      const node = (dto?.attackPathNodes ?? []).find(n => n.id === item.endpointNodeId);
      const label = node?.hostname || node?.label || item.endpointKey;
      onEndpointClick(item.endpointNodeId, item.endpointKey, label);
      setHighlightedExecutionIds(new Set(item.executionIds ?? []));
    },
    [onEndpointClick, dto?.attackPathNodes],
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
  // Render the causal execution-chain layout whenever the size-gated full graph is available and carries
  // executions (small runs). Large runs never fetch it (fullDto stays null) and keep the aggregated view.
  const chainMode = !!fullDto && (fullDto.attackPathExecutions?.length ?? 0) > 0;

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
      if (pathFinding) {
        raw = buildFindingPathFlow(dto, pathFinding, t, pathContractLabelByInjector, {
          expanded: expandedFindingClusters,
          findingsByCluster,
          batch: findingBatch,
        });
      } else if (chainMode && fullDto) {
        raw = buildCausalChainFlow(fullDto, t);
      } else {
        raw = buildClusteredAttackPathFlow(dto, endpointBatch, t, {
          expanded: expandedFindingClusters,
          findingsByCluster,
          batch: findingBatch,
        });
      }
      if (chokepointRankById.size === 0) {
        return raw;
      }
      return {
        nodes: raw.nodes.map(n => (n.type === AP_FLOW_NODE_TYPE.asset && chokepointRankById.has(n.id)
          ? {
              ...n,
              data: {
                ...n.data,
                chokepointRank: chokepointRankById.get(n.id),
              },
            }
          : n)),
        edges: raw.edges,
      };
    },
    [dto, chainMode, fullDto, pathFinding, pathContractLabelByInjector, endpointBatch, expandedFindingClusters, findingsByCluster, findingBatch, chokepointRankById, t],
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
    // injector on the endpoint highlighted instead of just the one that produced the finding.
    const findingNodeId = `NODE_FINDING|${type}|${value}`;
    const fromFull = (fullDto?.attackPathExecutions ?? [])
      .filter(e => (e.findingsNodeIds ?? []).includes(findingNodeId))
      .map(e => e.ref)
      .filter((r): r is string => !!r);
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
  const openFindingFromGraph = useCallback((type: string, value: string, assetNodeId: string): boolean => {
    const node = (dto?.attackPathNodes ?? []).find(n => n.id === assetNodeId);
    if (!node) {
      return false;
    }
    const endpointKey = node.ref ?? assetNodeId;
    const label = node.hostname || node.label || endpointKey;
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
      setFitNonce(n => n + 1);
    }
    // Loads the endpoint feed (executions + relations) so producing actions resolve. It also resets
    // findingDetail + highlightedExecutionIds + selectedFindingId, so all are set right after in the batch.
    onEndpointClick(assetNodeId, endpointKey, label);
    setFindingDetail({
      type,
      value,
    });
    if (chainMode) {
      // In the causal-chain layout the graph already reads inject → endpoint → finding → next inject, so
      // clicking a finding must NOT collapse into the old focused-path layout. Keep the chain and select
      // the finding by its node id (its producer branch lights up via the upstream selection walk). Set
      // AFTER onEndpointClick, which resets selectedFindingId.
      setSelectedFindingId(`NODE_FINDING|${type}|${value}`);
      // Producing executions come from the full graph's execution→findings links (available for EVERY
      // finding type, unlike the drawer categories which only cover credentials/users/files/cves), so the
      // panel lists only the injector(s) that actually produced this finding — not every injector that
      // merely reached the endpoint.
      const findingNodeId = `NODE_FINDING|${type}|${value}`;
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
    if (assetNodeId && openFindingFromGraph(type ?? '', value ?? '', assetNodeId)) {
      return;
    }
    setSelectedNodeId(null);
    setSelectedInjectorId(null);
    setSelectedFindingId(prev => (prev === nodeId ? null : nodeId));
  }, [pathFinding, highlightGraphFinding, openFindingFromGraph]);

  // Open the Result & Terminal drawer for a representative execution of an injector (action): its
  // executed command (Terminal tab), prevention/detection verdicts (Result tab) and ATT&CK techniques
  // (header) — the action-side mirror of the finding panel. The representative execution is resolved
  // from one of the injector's reached endpoints, matched to the injector by payload/step template.
  const openInjectorExecution = useCallback((injectorId: string, label?: string) => {
    const refs = injectorEndpointRefs.get(injectorId) ?? [];
    if (refs.length === 0) {
      return;
    }
    const base = (label ?? '').toLowerCase();
    const matchesInjector = (e: AttackPathNodeDTO) =>
      (e.payloadName ?? '').toLowerCase().replace(/-payload$/, '') === base
      || (e.stepTemplateId ?? '').toLowerCase() === `step-tpl-${base}`;
    fetchEndpointRelations(simulationId, refs[0])
      .then((r) => {
        const exec = (r.data.executions ?? []).find(e => !!e.ref && matchesInjector(e));
        if (exec?.ref) {
          openExecutionDetail(exec.ref);
        }
      })
      .catch(() => undefined);
  }, [injectorEndpointRefs, simulationId, openExecutionDetail]);

  // Click an injector (action) node. In the focused view it reverse-highlights on the focused
  // endpoint; in the clustered view it toggles a downstream highlight of the action's reach AND opens
  // the inject drawer (a representative execution's command + prevention/detection + ATT&CK), the
  // action-side mirror of the finding click.
  const onInjectorSelect = useCallback((injectorId: string, label?: string) => {
    if (pathFinding) {
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
    const willSelect = selectedInjectorId !== injectorId;
    setSelectedInjectorId(willSelect ? injectorId : null);
    if (willSelect) {
      openInjectorExecution(injectorId, label);
    } else {
      // Toggling the same injector off also closes its inject drawer.
      setDetailExecutionId(null);
      setDetail(null);
    }
  }, [pathFinding, highlightGraphInjector, endpointRelationEdges, openExecutionDetail, selectedInjectorId, openInjectorExecution]);

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
        return {
          nodes: baseFlow.nodes.map(n => ({
            ...n,
            selected: n.id === pathFinding.endpointNodeId,
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
      const pathSet = new Set<string>();
      if (selectedInjectorId && injectorIds.has(selectedInjectorId)) {
        // Reverse focus: walk DOWNSTREAM from the clicked injector (injector -> endpoint -> the
        // findings it reached), the mirror of the finding walk-up below.
        pathSet.add(selectedInjectorId);
        for (let pass = 0; pass < 8; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.source && e.target && pathSet.has(e.source) && !pathSet.has(e.target)) {
              pathSet.add(e.target);
            }
          }
        }
      } else {
        // The focused finding is highlighted inside its own type cluster (no extracted node), so the
        // default active node is that type's cluster. A leaf finding clicked in place overrides it.
        const defaultId = `path-cl-type|${pathFinding.type}|${pathFinding.endpointKey}`;
        const activeId = selectedFindingId ?? defaultId;
        // Only the injector(s) that actually produced the focused finding light up — not every injector
        // that merely reached the endpoint — so the highlighted path stays scoped to the finding the
        // analyst opened, even after expanding its cluster.
        const restrictInjectors = producingInjectorIds.size > 0;
        pathSet.add(activeId);
        for (let pass = 0; pass < 8; pass += 1) {
          for (const e of baseFlow.edges) {
            if (e.target && e.source && pathSet.has(e.target) && !pathSet.has(e.source)) {
              if (restrictInjectors && injectorIds.has(e.source) && !producingInjectorIds.has(e.source)) {
                continue;
              }
              pathSet.add(e.source);
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
    if (selectedInjectorId) {
      pathSet.add(selectedInjectorId);
      for (let pass = 0; pass < 6; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.source && e.target && pathSet.has(e.source) && !pathSet.has(e.target)) {
            pathSet.add(e.target);
          }
        }
      }
    } else if (selectedFindingId) {
      pathSet.add(selectedFindingId);
      for (let pass = 0; pass < 6; pass += 1) {
        for (const e of baseFlow.edges) {
          if (e.target && e.source && pathSet.has(e.target) && !pathSet.has(e.source)) {
            pathSet.add(e.source);
          }
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
  }, [baseFlow, pathFinding, producingInjectorIds, selectedNodeId, selectedFindingId, selectedInjectorId, focus]);

  // Additive kill-chain causal edges (issue 6647) for the AGGREGATED view, merged on top of the status
  // graph. Drawn only when a consumed key matches a produced finding (or a dependsOn resolves). In chain
  // mode the layout already emits its own forward causal edges, so this overlay is disabled to avoid
  // duplicates. Built from the final (post-selection) nodes so the same nodes drive base and overlay.
  const causalEdges = useMemo(
    () => (chainMode ? [] : buildCausalEdges(nodes, id => (id ? killChainMeta.get(id) : undefined), t)),
    [chainMode, nodes, killChainMeta, t],
  );
  const graphEdges = useMemo(() => [...edges, ...causalEdges], [edges, causalEdges]);

  const counters = dto?.counters;
  const focusedEndpoint = useMemo(
    () => (pathFinding
      ? (dto?.attackPathNodes ?? []).find(n => n.id === pathFinding.endpointNodeId)
      : undefined),
    [dto?.attackPathNodes, pathFinding],
  );

  // The action(s) that produced the finding shown in the details panel, mapped to a display row that
  // opens the Result & Terminal view. Uses the active finding's executions (a child sub-selection
  // overrides the main focused finding), resolved against the focused endpoint's execution feed.
  const producingActions = useMemo((): ProducingAction[] => {
    if (!findingDetail) {
      return [];
    }
    return executions
      .filter(e => !!e.ref && highlightedExecutionIds.has(e.ref))
      .map(e => ({
        ref: e.ref as string,
        contract: toContractLabel(e) ?? e.payloadName ?? e.label ?? t('Action'),
        statusColor: attackPathStatusColor(theme, e.status),
        statusLabel: t(statusLabelKey(e.status)),
        subtitle: [e.agentName, e.privilege].filter(Boolean).join(' · '),
      }));
  }, [findingDetail, highlightedExecutionIds, executions, theme, t]);

  // Prevention / detection / vulnerability verdicts shown at the top of the finding panel.
  // TODO(#6647): replace this placeholder with the real per-finding expectation verdicts once the
  // backend exposes them; for now a CVE finding is flagged vulnerable and the rest is a placeholder.
  const findingExpectations = useMemo((): FindingExpectations | undefined => {
    if (!findingDetail) {
      return undefined;
    }
    return {
      prevention: 'success',
      detection: 'success',
      vulnerability: findingDetail.type === 'cve' ? 'failed' : 'unknown',
    };
  }, [findingDetail]);

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
  const drawerPageItems = useMemo(
    () => drawerFilteredItems.slice(drawerSafePage * DRAWER_PAGE_SIZE, drawerSafePage * DRAWER_PAGE_SIZE + DRAWER_PAGE_SIZE),
    [drawerFilteredItems, drawerSafePage],
  );

  // "Captured Files" has no backend counter: derive an approximate share count from the collapsed
  // endpoints' per-type finding counts (temporary until a native "file" finding type exists).
  const filesCount = useMemo(() => {
    let sum = 0;
    for (const n of dto?.attackPathNodes ?? []) {
      if (n.type === 'ASSET') {
        sum += n.findingCounts?.share ?? 0;
      }
    }
    return sum;
  }, [dto]);

  const focusedFilesCount = focusedEndpoint?.findingCounts?.share ?? 0;
  const effectiveCounters = pathFinding
    ? {
        endpoints: 1,
        credentials: focusedEndpoint?.findingCounts?.credentials ?? 0,
        users: (focusedEndpoint?.findingCounts?.username ?? 0) + (focusedEndpoint?.findingCounts?.admin_username ?? 0),
        cves: focusedEndpoint?.findingCounts?.cve ?? 0,
      }
    : {
        endpoints: counters?.endpoints ?? 0,
        credentials: counters?.credentials ?? 0,
        users: counters?.users ?? 0,
        cves: counters?.cves ?? 0,
      };

  const cards: FindingCard[] = useMemo(() => {
    const base: FindingCard[] = [
      {
        key: 'endpoints',
        label: t('Discovered Endpoints'),
        icon: <DnsOutlined fontSize="small" />,
        count: effectiveCounters.endpoints,
      },
      {
        key: 'files',
        label: t('Captured Files'),
        icon: <InsertDriveFileOutlined fontSize="small" />,
        count: pathFinding ? focusedFilesCount : filesCount,
        hint: t('Temporarily mapped to "share" findings'),
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
      .sort((a, b) => b[1] - a[1])
      .map(([type, count]) => {
        const noun = t(findingCategoryNoun(type));
        return {
          key: type,
          label: noun.charAt(0).toUpperCase() + noun.slice(1),
          icon: <LabelOutlined fontSize="small" />,
          count,
        };
      });
    return [...base, ...extras];
  }, [t, effectiveCounters, pathFinding, focusedFilesCount, filesCount, dto, focusedEndpoint]);

  // Click a summary card: focus the graph on that finding type and, for finding categories, open the
  // right drawer listing the (deduplicated, masked) items. Clicking again clears the focus/drawer.
  const onCardClick = (card: FindingCard) => {
    const next = activeCard === card.key ? null : card.key;
    setActiveCard(next);
    if (next && next !== 'endpoints') {
      openFindingsDrawer(next, card.label);
    } else {
      setDrawerCategory(null);
    }
  };

  const clearFocus = () => {
    setActiveCard(null);
    setDrawerCategory(null);
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
  const [chokepointsAnchor, setChokepointsAnchor] = useState<HTMLElement | null>(null);

  // Graph (node-link) vs Table (sortable/exportable list of exposed endpoints) view of the same data.
  const [view, setView] = useState<'graph' | 'table'>('graph');

  // Free-text search input (endpoint / injector / finding type), used by the search autocomplete.
  const [searchInput, setSearchInput] = useState('');

  // Focus a chokepoint endpoint: redraw the graph as the focused endpoint path (injectors -> endpoint
  // -> its finding clusters) and open its side panel (findings + executions). Uses the endpoint-focus
  // mode of buildFindingPathFlow (empty finding type/value).
  const focusChokepoint = (c: {
    nodeId: string;
    ref: string;
    label: string;
  }) => {
    // Ensure the graph is showing: the strip chip and the popover card are reachable from the table
    // view too, and the focused path only renders in the graph view.
    setView('graph');
    setChokepointsAnchor(null);
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
    onEndpointClick(c.nodeId, c.ref, c.label);
  };

  // Keep the selected value in the options so MUI does not warn when the current simulation has no
  // attack-path summary row yet.
  const selectedRow: AttackPathSimSummaryRow | null = simulationId
    ? (simulations.find(s => s.simulationId === simulationId) ?? { simulationId })
    : null;
  const pickerOptions = selectedRow && !simulations.some(s => s.simulationId === selectedRow.simulationId)
    ? [selectedRow, ...simulations]
    : simulations;

  // Chokepoint accent (violet), reserved so it never reads as a prevention/detection verdict.
  const chokepointColor = attackPathChokepointColor(theme);

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
      return t('Endpoints');
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

  // Scenario context has runs to pick from; when it has none yet, the empty-state offers to launch one.
  const scenarioHasNoSims = showPicker && simulations.length === 0;
  const emptyStateMessage = (() => {
    if (scenarioHasNoSims) {
      return t('This scenario has no simulation with attack-path data yet. Launch one to reveal its attack path.');
    }
    if (showPicker) {
      return t('No attack-path data for this simulation. Select a simulation with attack-path data above.');
    }
    return t('No attack-path data for this simulation.');
  })();
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
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      // Give the graph as much vertical room as possible (it grows with the endpoint count); the offset
      // only reserves the page chrome above (header + tabs + the picker/cards strip).
      height: 'calc(100vh - 200px)',
      gap: theme.spacing(1),
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        {showPicker && (
          <Autocomplete
            size="small"
            options={pickerOptions}
            value={selectedRow}
            isOptionEqualToValue={(o, v) => o.simulationId === v.simulationId}
            getOptionLabel={o => labelFor(o.simulationId)}
            onChange={(_, v) => {
              if (v?.simulationId) {
                setSimulationId(v.simulationId);
              }
            }}
            renderOption={(props, o) => {
              const { key, ...rest } = props as { key: string } & Record<string, unknown>;
              return (
                <li
                  key={key}
                  {...rest}
                  style={{
                    display: 'flex',
                    gap: 8,
                  }}
                >
                  <span>{labelFor(o.simulationId)}</span>
                  <span style={{
                    marginLeft: 'auto',
                    opacity: 0.65,
                    fontSize: 12,
                  }}
                  >
                    {`${o.endpointCount ?? 0} ${t('endpoints')} · ${o.executionCount ?? 0} ${t('exec.')}`}
                  </span>
                </li>
              );
            }}
            renderInput={params => <TextField {...params} label={t('Simulation')} />}
            sx={{
              maxWidth: 520,
              flex: '1 1 320px',
            }}
          />
        )}
        <div style={{
          display: 'flex',
          gap: theme.spacing(1),
          alignItems: 'center',
        }}
        >
          {activeCard && (
            <Chip
              label={t('Clear focus')}
              size="small"
              variant="outlined"
              onDelete={clearFocus}
              onClick={clearFocus}
            />
          )}
          {pathFinding && (
            <Chip
              label={t('Back to full graph')}
              size="small"
              color="primary"
              variant="outlined"
              onDelete={clearPathFocus}
              onClick={clearPathFocus}
            />
          )}
          <ToggleButtonGroup
            size="small"
            exclusive
            value={view}
            onChange={(_, v) => v && setView(v)}
            aria-label={t('View')}
            sx={{ ml: 'auto' }}
          >
            <ToggleButton value="graph" aria-label={t('Graph')}>
              <Tooltip title={t('Graph')}><AccountTreeOutlined fontSize="small" /></Tooltip>
            </ToggleButton>
            <ToggleButton value="table" aria-label={t('Table')}>
              <Tooltip title={t('Table')}><TableRowsOutlined fontSize="small" /></Tooltip>
            </ToggleButton>
          </ToggleButtonGroup>
          <Autocomplete<SearchOption>
            size="small"
            options={searchOptions}
            value={null}
            inputValue={searchInput}
            onInputChange={(_, v) => setSearchInput(v)}
            onChange={(_, v) => {
              onSearchSelect(v);
              setSearchInput('');
            }}
            blurOnSelect
            clearOnBlur
            groupBy={o => searchGroupLabel(o.kind)}
            getOptionLabel={o => o.label}
            isOptionEqualToValue={(o, v) => o.nodeId === v.nodeId && o.label === v.label}
            filterOptions={(opts, state) => {
              const q = state.inputValue.trim().toLowerCase();
              if (!q) {
                return opts;
              }
              return opts.filter(o => o.label.toLowerCase().includes(q) || (o.sub ?? '').toLowerCase().includes(q));
            }}
            renderOption={(props, o) => {
              const { key, ...rest } = props as { key: string } & Record<string, unknown>;
              return (
                <li key={key} {...rest}>
                  <div style={{ minWidth: 0 }}>
                    <Typography variant="body2" noWrap>{o.label}</Typography>
                    {o.sub && <Typography variant="caption" color="text.secondary" noWrap>{o.sub}</Typography>}
                  </div>
                </li>
              );
            }}
            renderInput={params => (
              <TextField
                {...params}
                placeholder={t('Search endpoint, injector, finding…')}
                InputProps={{
                  ...params.InputProps,
                  startAdornment: <SearchOutlined fontSize="small" sx={{ mr: 0.5 }} />,
                }}
              />
            )}
            sx={{ width: 260 }}
          />
        </div>
      </div>

      <div style={{
        display: 'flex',
        alignItems: 'stretch',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        {cards.filter(c => c.count > 0).map((c) => {
          const active = activeCard === c.key;
          const card = (
            <ButtonBase
              key={c.key}
              onClick={() => onCardClick(c)}
              aria-pressed={active}
              focusRipple
              sx={{
                'flex': '1 1 0',
                'minWidth': 150,
                'justifyContent': 'flex-start',
                'textAlign': 'left',
                'gap': 1.5,
                'padding': theme.spacing(1.5),
                'borderRadius': 1,
                'border': `1px solid ${active ? theme.palette.primary.main : theme.palette.divider}`,
                'backgroundColor': active ? theme.palette.action.selected : theme.palette.background.paper,
                'transition': theme.transitions.create(['border-color', 'background-color']),
                '&:hover': { borderColor: theme.palette.primary.main },
              }}
            >
              <span style={{
                color: theme.palette.primary.main,
                display: 'flex',
              }}
              >
                {c.icon}
              </span>
              <div style={{ minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                  {c.label}
                </Typography>
                <Typography variant="h6" sx={{ lineHeight: 1.1 }}>
                  {c.count}
                </Typography>
              </div>
            </ButtonBase>
          );
          return c.hint
            ? (
                <Tooltip key={c.key} title={c.hint} arrow>
                  <span style={{
                    flex: '1 1 0',
                    display: 'flex',
                    minWidth: 150,
                  }}
                  >
                    {card}
                  </span>
                </Tooltip>
              )
            : card;
        })}

        {/* Top chokepoints: the most-exposed endpoints (most findings). Opens a ranked, clickable list. */}
        {!pathFinding && chokepoints.length > 0 && (
          <>
            <ButtonBase
              onClick={e => setChokepointsAnchor(e.currentTarget)}
              aria-haspopup="true"
              aria-expanded={Boolean(chokepointsAnchor)}
              focusRipple
              sx={{
                'flex': '1 1 0',
                'minWidth': 150,
                'justifyContent': 'flex-start',
                'textAlign': 'left',
                'gap': 1.5,
                'padding': theme.spacing(1.5),
                'borderRadius': 1,
                'border': `1px solid ${chokepointsAnchor ? chokepointColor : theme.palette.divider}`,
                'backgroundColor': theme.palette.background.paper,
                'transition': theme.transitions.create(['border-color', 'background-color']),
                '&:hover': { borderColor: chokepointColor },
              }}
            >
              <span style={{
                color: chokepointColor,
                display: 'flex',
              }}
              >
                <LocalFireDepartment fontSize="small" />
              </span>
              <div style={{ minWidth: 0 }}>
                <span style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 3,
                }}
                >
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {t('Top chokepoints')}
                  </Typography>
                  <Tooltip
                    arrow
                    title={t('Chokepoints rank endpoints by findings weighted by criticality (score = findings × criticality weight), so the top one is the most findings on the most critical endpoint. Click to see how it is computed.')}
                  >
                    <HelpOutline sx={{
                      fontSize: 13,
                      color: 'text.disabled',
                    }}
                    />
                  </Tooltip>
                </span>
                <Typography variant="h6" sx={{ lineHeight: 1.1 }}>
                  {chokepoints.length}
                </Typography>
              </div>
            </ButtonBase>
            <Popover
              open={Boolean(chokepointsAnchor)}
              anchorEl={chokepointsAnchor}
              onClose={() => setChokepointsAnchor(null)}
              anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'left',
              }}
            >
              <Box sx={{
                p: 1,
                minWidth: 340,
                maxWidth: 400,
              }}
              >
                {/* Transparent formula, mirroring the exposure-score explanation: what it measures, the
                    exact formula, and the criticality weights it uses. */}
                <Box sx={{
                  px: 1,
                  pb: 1,
                  mb: 0.5,
                  borderBottom: `1px solid ${theme.palette.divider}`,
                }}
                >
                  <Typography variant="subtitle2">{t('How chokepoints are scored')}</Typography>
                  <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 0.5 }}>
                    {t('A chokepoint is the endpoint where fixing findings closes the most attack paths. The score weights an endpoint\'s findings by its business criticality, so a critical host outranks a noisier but less important one.')}
                  </Typography>
                  <Box sx={{
                    mt: 1,
                    p: 0.75,
                    borderRadius: 1,
                    backgroundColor: alpha(chokepointColor, 0.12),
                    fontFamily: 'monospace',
                    fontSize: 12,
                    textAlign: 'center',
                  }}
                  >
                    {t('score = findings × criticality weight')}
                  </Box>
                  <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>
                    {t('Criticality weight')}
                    {': '}
                    {Object.entries(CRITICALITY_WEIGHT)
                      .filter(([k]) => k !== 'UNKNOWN')
                      .map(([k, w]) => `${t(CRITICALITY_LABEL[k])} ×${w}`)
                      .join(' · ')}
                    {` · ${t(CRITICALITY_LABEL.UNKNOWN)} ×${CRITICALITY_WEIGHT.UNKNOWN}`}
                  </Typography>
                </Box>
                <Typography
                  variant="subtitle2"
                  sx={{
                    px: 1,
                    pb: 0.5,
                  }}
                >
                  {t('Most exposed endpoints')}
                </Typography>
                {chokepoints.map((c, i) => (
                  <Box
                    key={c.nodeId}
                    role="button"
                    tabIndex={0}
                    onClick={() => focusChokepoint(c)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        focusChokepoint(c);
                      }
                    }}
                    sx={{
                      'display': 'flex',
                      'alignItems': 'center',
                      'gap': 1,
                      'px': 1,
                      'py': 0.75,
                      'borderRadius': 1,
                      'cursor': 'pointer',
                      '&:hover': { backgroundColor: 'action.hover' },
                      '&:focus-visible': {
                        outline: `2px solid ${theme.palette.primary.main}`,
                        outlineOffset: -2,
                      },
                    }}
                  >
                    <span style={{
                      flex: '0 0 auto',
                      width: 20,
                      height: 20,
                      borderRadius: '50%',
                      background: chokepointColor,
                      color: theme.palette.getContrastText(chokepointColor),
                      fontSize: 11,
                      fontWeight: 700,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                    >
                      {i + 1}
                    </span>
                    <div style={{
                      minWidth: 0,
                      flex: 1,
                    }}
                    >
                      <Typography variant="body2" noWrap title={c.label}>{c.label}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {[
                          c.ip,
                          `${c.findings} ${t('findings')} × ${c.weight} (${t(CRITICALITY_LABEL[c.criticality ?? 'UNKNOWN'] ?? CRITICALITY_LABEL.UNKNOWN)}) = ${c.score}`,
                        ].filter(Boolean).join(' · ')}
                      </Typography>
                    </div>
                  </Box>
                ))}
              </Box>
            </Popover>
          </>
        )}
      </div>

      <div style={{
        display: 'flex',
        flex: 1,
        minHeight: 0,
        gap: theme.spacing(1),
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
          <>
            <Paper
              variant="outlined"
              sx={{
                flex: 1,
                minWidth: 0,
                position: 'relative',
              }}
            >
              {loading && <Loader />}
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
              {!loading && !forbidden && !error && nodes.length === 0 && (
                <Box sx={{
                  m: 'auto',
                  p: 4,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: theme.spacing(1.5),
                  textAlign: 'center',
                  color: 'text.secondary',
                }}
                >
                  <AccountTreeOutlined sx={{
                    fontSize: 48,
                    opacity: 0.35,
                  }}
                  />
                  <Typography variant="body1" sx={{ maxWidth: 440 }}>
                    {emptyStateMessage}
                  </Typography>
                  {scenarioHasNoSims && scenarioId && (
                    <Button
                      variant="contained"
                      startIcon={<PlayArrowOutlined />}
                      onClick={handleLaunchFromScenario}
                      disabled={launching}
                    >
                      {t('Launch a simulation')}
                    </Button>
                  )}
                </Box>
              )}
              {!loading && !forbidden && !error && nodes.length > 0 && (
                <ReactFlowProvider>
                  <AttackPathFlow
                    nodes={nodes}
                    edges={graphEdges}
                    onEndpointClick={onEndpointClick}
                    onClusterClick={onClusterClick}
                    onFindingClusterClick={onFindingClusterClick}
                    onFindingSelect={onFindingSelect}
                    onInjectorSelect={onInjectorSelect}
                    focusRequest={focusRequest}
                    fitRequest={fitNonce}
                    showMiniMap={!pathFinding && nodes.length > 40}
                  />
                  <AttackPathLegend collapseSignal={legendCollapseNonce} />
                </ReactFlowProvider>
              )}
            </Paper>

            {findingDetail && (
              <FindingDetailPanel
                value={maskFindingValue(findingDetail.type, findingDetail.value)}
                type={findingDetail.type}
                endpointLabel={focusedEndpoint?.hostname || focusedEndpoint?.label || pathFinding?.endpointKey || t('Endpoint')}
                endpointSub={[focusedEndpoint?.ip, focusedEndpoint?.platform].filter(Boolean).join(' · ')}
                expectations={findingExpectations}
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

            {!findingDetail && selectedNodeId && (
              <EndpointDetailPanel
                endpointLabel={selectedLabel || t('Endpoint')}
                findingsLoading={endpointFindingsLoading}
                findingGroups={endpointFindingGroups}
                executions={executions}
                execDisplayCap={EXEC_DISPLAY_CAP}
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

            {detailExecutionId && (
              <ExecutionResultTerminalPanel
                loading={detailLoading}
                detail={detail}
                onClose={() => setDetailExecutionId(null)}
                onOpenInject={(detail as { injectId?: string } | null)?.injectId
                  ? () => navigate(`../injects/${(detail as { injectId?: string }).injectId}`)
                  : undefined}
              />
            )}
          </>
        )}
      </div>

      <Drawer
        open={drawerCategory !== null}
        handleClose={() => setDrawerCategory(null)}
        title={`${drawerLabel} (${drawerFilteredItems.length})`}
      >
        <>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: 'block',
              mb: 1.5,
            }}
          >
            {t('Click any item to highlight it on the attack map and focus the producing action in the feed.')}
          </Typography>
          <TextField
            size="small"
            fullWidth
            value={drawerSearch}
            onChange={(e) => {
              setDrawerSearch(e.target.value);
              setDrawerPage(0);
            }}
            placeholder={t('Search')}
            sx={{ mb: 1.5 }}
          />
          {findingsLoading && (
            <Box sx={{ minHeight: 120 }}>
              <Loader variant="inElement" size="sm" />
            </Box>
          )}
          {!findingsLoading && drawerFilteredItems.length === 0 && (
            <Alert severity="info">{t('No findings')}</Alert>
          )}
          {!findingsLoading && drawerPageItems.map((item, index) => (
            <Box
              key={`${item.endpointKey}-${item.value}-${index}`}
              role="button"
              tabIndex={0}
              onClick={() => onFindingItemClick(item)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onFindingItemClick(item);
                }
              }}
              sx={{
                'py': 0.75,
                'px': 0.5,
                'borderRadius': 1,
                'borderBottom': `1px solid ${theme.palette.divider}`,
                'cursor': 'pointer',
                '&:hover': { backgroundColor: 'action.hover' },
                '&:focus-visible': {
                  backgroundColor: 'action.hover',
                  outline: `2px solid ${theme.palette.primary.main}`,
                  outlineOffset: -2,
                },
              }}
            >
              <Typography variant="body2" title={maskFindingValue(item.type, item.value)} sx={{ wordBreak: 'break-all' }}>{maskFindingValue(item.type, item.value)}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap title={item.endpointKey}>
                {item.endpointKey}
              </Typography>
            </Box>
          ))}
          {!findingsLoading && drawerFilteredItems.length > DRAWER_PAGE_SIZE && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              pt: 1.5,
            }}
            >
              <Chip
                size="small"
                variant="outlined"
                label={t('Previous')}
                disabled={drawerSafePage <= 0}
                onClick={() => setDrawerPage(p => Math.max(0, p - 1))}
              />
              <Typography variant="caption" color="text.secondary">
                {`${drawerSafePage + 1} / ${drawerPageCount}`}
              </Typography>
              <Chip
                size="small"
                variant="outlined"
                label={t('Next')}
                disabled={drawerSafePage >= drawerPageCount - 1}
                onClick={() => setDrawerPage(p => Math.min(drawerPageCount - 1, p + 1))}
              />
            </Box>
          )}
        </>
      </Drawer>
    </div>
  );
};

export default SimulationAttackPath;
