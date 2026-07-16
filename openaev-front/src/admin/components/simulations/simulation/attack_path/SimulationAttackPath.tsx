import { BugReportOutlined, Close, DnsOutlined, GroupOutlined, InsertDriveFileOutlined, VpnKeyOutlined } from '@mui/icons-material';
import { Alert, Autocomplete, Box, ButtonBase, Chip, Drawer, IconButton, Paper, TextField, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ReactFlowProvider } from '@xyflow/react';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { fetchAttackPathGraph, fetchAttackPathSimulations, fetchEndpointFindings, fetchEndpointRelations, fetchExecutionDetail, fetchFindingsByCategory, fetchSimulationsMetaById } from '../../../../../actions/attack-path/attack-path-actions';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathDTO, AttackPathExecutionDetailDTO, AttackPathFindingItemDTO, AttackPathFindingPageDTO, AttackPathNodeDTO, AttackPathSimSummaryRow, ExerciseSimple } from '../../../../../utils/api-types';
import attackPathStatusColor from './attack-path-colors';
import { applyFindingFilter, type AttackPathFindingFilter, buildClusteredAttackPathFlow, ENDPOINT_BATCH_SIZE, FILTER_TO_FINDING_TYPES, FINDING_BATCH_SIZE, maskFindingValue } from './attack-path-flow-helpers';
import AttackPathFlow, { type AttackPathFocusRequest } from './AttackPathFlow';
import AttackPathLegend from './AttackPathLegend';
import ExecutionResultTerminalPanel from './ExecutionResultTerminalPanel';

// A hot endpoint can have many executions; the read is bounded to the one endpoint, but the side
// panel still renders a list, so cap it (the backend /relations read would be paginated in prod).
const EXEC_DISPLAY_CAP = 100;

// Expanding a finding cluster fetches findings from at most this many of the injector's endpoints and
// de-duplicates them — a bounded, front-only stand-in until a "findings by type" endpoint exists.
const FINDING_FETCH_ENDPOINTS = 30;

// Synthetic seeded simulations (POST /attack-path/seed) carry no real date/name; keep them hidden
// from metadata resolution and fall back to their raw id in the picker.
const isSeedId = (id?: string) => !!id && id.startsWith('ap-seed-');

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

interface FindingCard {
  key: AttackPathFindingFilter;
  label: string;
  icon: ReactNode;
  count: number;
  hint?: string;
}

/**
 * Attack-path tab (issue 6647), gated by the ATTACK_PATH preview feature. Renders the simulation as a
 * clustered graph: each injector fans out to an aggregate endpoint dot (+N) and one cluster per
 * finding type (with counts), all derived from the collapsed graph — no extra reads. An injector can
 * be expanded into its real endpoints, and the five summary cards open a right drawer (backend
 * findings list) that cross-focuses the graph and the execution feed. Clicking a feed entry opens the
 * execution Result & Terminal panel.
 */
const SimulationAttackPath = () => {
  const { exerciseId } = useParams() as { exerciseId: string };
  const theme = useTheme();
  const { t, fldt } = useFormatter();

  const [simulationId, setSimulationId] = useState(exerciseId ?? '');
  const [simulations, setSimulations] = useState<AttackPathSimSummaryRow[]>([]);
  const [metaById, setMetaById] = useState<Map<string, ExerciseSimple>>(new Map());
  const [dto, setDto] = useState<AttackPathDTO | null>(null);
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
  const [executions, setExecutions] = useState<AttackPathNodeDTO[]>([]);
  // The clicked endpoint's own findings (deduplicated by type+value), shown in the side panel.
  const [endpointFindings, setEndpointFindings] = useState<AttackPathNodeDTO[]>([]);
  const [endpointFindingsLoading, setEndpointFindingsLoading] = useState(false);

  // Findings drawer: a summary card opens a right drawer listing that category's findings (issue 5048).
  const [drawerCategory, setDrawerCategory] = useState<string | null>(null);
  const [drawerLabel, setDrawerLabel] = useState<string>('');
  const [findingsPage, setFindingsPage] = useState<AttackPathFindingPageDTO | null>(null);
  const [findingsLoading, setFindingsLoading] = useState(false);

  // Cross-focus: clicking a finding item centers its endpoint (focusRequest) and highlights the
  // producing executions in the feed (by their raw ids).
  const [focusRequest, setFocusRequest] = useState<AttackPathFocusRequest | null>(null);
  const [highlightedExecutionIds, setHighlightedExecutionIds] = useState<Set<string>>(new Set());
  const feedRowRefs = useRef<Map<string, HTMLDivElement>>(new Map());

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
    setExecutions([]);
    setEndpointFindings([]);
    setActiveCard(null);
    // Close the drawers and clear any cross-focus so nothing carries over between simulations.
    setDrawerCategory(null);
    setDetailExecutionId(null);
    setHighlightedExecutionIds(new Set());
    setFocusRequest(null);
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

  // Load the picker options once (simulations that have attack-path data in this tenant), then
  // resolve real simulations' date + name so the picker reads dates instead of raw ids.
  useEffect(() => {
    fetchAttackPathSimulations()
      .then((r) => {
        const rows = r.data ?? [];
        setSimulations(rows);
        const ids = Array.from(new Set([
          ...rows.map(s => s.simulationId).filter((id): id is string => !isSeedId(id) && !!id),
          ...(!isSeedId(exerciseId) && exerciseId ? [exerciseId] : []),
        ]));
        if (ids.length > 0) {
          fetchSimulationsMetaById(ids)
            .then(m => setMetaById(new Map((m.data ?? []).map(e => [e.exercise_id, e]))))
            .catch(() => undefined);
        }
      })
      .catch(() => setSimulations([]));
  }, [exerciseId]);

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
    setSelectedLabel(label ?? '');
    setExecutions([]);
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
        }
      })
      .catch(() => {
        if (seq === endpointSeq.current) {
          setExecutions([]);
        }
      });
  }, [simulationId]);

  // Progressive endpoint reveal: the "+N" header toggles expand/collapse; an "+rest" overflow reveals
  // the next batch.
  const onClusterClick = useCallback((injectorId: string, kind: 'header' | 'overflow') => {
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
  }, []);

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

  // Fetch a bounded, de-duplicated set of a finding type's values from the injector's endpoints — a
  // front-only stand-in until a "findings by type" backend endpoint exists.
  const fetchClusterFindings = useCallback((clusterId: string, type: string, injectorId: string) => {
    const refs = (injectorEndpointRefs.get(injectorId) ?? []).slice(0, FINDING_FETCH_ENDPOINTS);
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
  }, [injectorEndpointRefs, simulationId]);

  // Click a finding cluster: the header expands/collapses it into its individual findings (fetched
  // once, batched); an overflow reveals the next batch.
  const onFindingClusterClick = useCallback((typeFindings: string, injectorId: string, kind: 'header' | 'overflow') => {
    const clusterId = `cl-ft-${typeFindings}-${injectorId}`;
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
      return;
    }
    setExpandedFindingClusters(prev => new Set(prev).add(clusterId));
    setFindingBatch(prev => new Map(prev).set(clusterId, FINDING_BATCH_SIZE));
    if (!findingsByCluster.has(clusterId)) {
      fetchClusterFindings(clusterId, typeFindings, injectorId);
    }
  }, [expandedFindingClusters, findingsByCluster, fetchClusterFindings]);

  // Open the findings drawer for a summary category and load its first page (backend, masked server-side).
  const openFindingsDrawer = useCallback((category: string, label: string) => {
    setDrawerCategory(category);
    setDrawerLabel(label);
    setFindingsPage(null);
    setFindingsLoading(true);
    fetchFindingsByCategory(simulationId, category)
      .then(r => setFindingsPage(r.data))
      .catch(() => setFindingsPage({
        items: [],
        total: 0,
      }))
      .finally(() => setFindingsLoading(false));
  }, [simulationId]);

  // Cross-focus: clicking a finding item closes the drawer, focuses its endpoint on the map
  // (center + select), loads that endpoint's feed, and highlights the producing executions in it.
  const onFindingItemClick = useCallback(
    (item: AttackPathFindingItemDTO) => {
      if (!item.endpointNodeId || !item.endpointKey) {
        return;
      }
      setDrawerCategory(null);
      // Use the endpoint's friendly label (hostname) for the panel title, like a direct node click.
      const node = (dto?.attackPathNodes ?? []).find(n => n.id === item.endpointNodeId);
      const label = node?.hostname || node?.label || item.endpointKey;
      onEndpointClick(item.endpointNodeId, item.endpointKey, label);
      setHighlightedExecutionIds(new Set(item.executionIds ?? []));
      setFocusRequest(prev => ({
        nodeId: item.endpointNodeId as string,
        nonce: (prev?.nonce ?? 0) + 1,
      }));
    },
    [onEndpointClick, dto?.attackPathNodes],
  );

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
    fetchExecutionDetail(simulationId, executionId)
      .then(r => setDetail(r.data))
      .catch(() => setDetail(null))
      .finally(() => setDetailLoading(false));
  }, [simulationId]);

  // Base clustered flow — recomputed when the graph data, endpoint expansion, or finding drill-down
  // changes (positions are deterministic, so it stays off the pure selection/focus path).
  const baseFlow = useMemo(
    () => (dto
      ? buildClusteredAttackPathFlow(dto, endpointBatch, {
          expanded: expandedFindingClusters,
          findingsByCluster,
          batch: findingBatch,
        })
      : {
          nodes: [],
          edges: [],
        }),
    [dto, endpointBatch, expandedFindingClusters, findingsByCluster, findingBatch],
  );

  // Click a leaf finding: highlight its full attack path (injector -> endpoint cluster -> finding
  // cluster -> finding) in blue.
  const onFindingSelect = useCallback((nodeId: string) => {
    setSelectedNodeId(null);
    setSelectedFindingId(prev => (prev === nodeId ? null : nodeId));
  }, []);

  // The active card focus, as finding types (or the endpoints backbone).
  const focus = useMemo((): readonly string[] | 'endpoints' | null => {
    if (!activeCard) {
      return null;
    }
    return activeCard === 'endpoints' ? 'endpoints' : FILTER_TO_FINDING_TYPES[activeCard];
  }, [activeCard]);

  // Mark the selected endpoint, the highlighted finding path (blue), then apply the card focus.
  const { nodes, edges } = useMemo(() => {
    // Walk up from the clicked finding to its injector so the whole path lights up.
    const pathSet = new Set<string>();
    if (selectedFindingId) {
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
  }, [baseFlow, selectedNodeId, selectedFindingId, focus]);

  const counters = dto?.counters;

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

  const cards: FindingCard[] = useMemo(() => [
    {
      key: 'endpoints',
      label: t('Discovered Endpoints'),
      icon: <DnsOutlined fontSize="small" />,
      count: counters?.endpoints ?? 0,
    },
    {
      key: 'files',
      label: t('Captured Files'),
      icon: <InsertDriveFileOutlined fontSize="small" />,
      count: filesCount,
      hint: t('Temporarily mapped to "share" findings'),
    },
    {
      key: 'credentials',
      label: t('Captured Credentials'),
      icon: <VpnKeyOutlined fontSize="small" />,
      count: counters?.credentials ?? 0,
    },
    {
      key: 'users',
      label: t('Discovered Users'),
      icon: <GroupOutlined fontSize="small" />,
      count: counters?.users ?? 0,
    },
    {
      key: 'cves',
      label: t('Detected CVEs'),
      icon: <BugReportOutlined fontSize="small" />,
      count: counters?.cves ?? 0,
    },
  ], [t, counters, filesCount]);

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

  // Keep the selected value in the options so MUI does not warn when the current simulation has no
  // attack-path summary row yet.
  const selectedRow: AttackPathSimSummaryRow | null = simulationId
    ? (simulations.find(s => s.simulationId === simulationId) ?? { simulationId })
    : null;
  const pickerOptions = selectedRow && !simulations.some(s => s.simulationId === selectedRow.simulationId)
    ? [selectedRow, ...simulations]
    : simulations;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: 'calc(100vh - 260px)',
      gap: theme.spacing(1),
    }}
    >
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
        sx={{ maxWidth: 520 }}
      />

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
        {activeCard && (
          <Chip
            label={t('Clear focus')}
            size="small"
            variant="outlined"
            onDelete={clearFocus}
            onClick={clearFocus}
            sx={{ alignSelf: 'center' }}
          />
        )}
      </div>

      <div style={{
        display: 'flex',
        flex: 1,
        minHeight: 0,
        gap: theme.spacing(1),
      }}
      >
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
            <Alert severity="info" sx={{ m: 2 }}>
              {t('No attack-path data for this simulation. Select a simulation with attack-path data above.')}
            </Alert>
          )}
          {!loading && !forbidden && !error && nodes.length > 0 && (
            <ReactFlowProvider>
              <AttackPathFlow
                nodes={nodes}
                edges={edges}
                onEndpointClick={onEndpointClick}
                onClusterClick={onClusterClick}
                onFindingClusterClick={onFindingClusterClick}
                onFindingSelect={onFindingSelect}
                focusRequest={focusRequest}
              />
              <AttackPathLegend />
            </ReactFlowProvider>
          )}
        </Paper>

        {selectedNodeId && (
          <Paper
            variant="outlined"
            sx={{
              width: 340,
              overflow: 'auto',
              padding: 2,
            }}
          >
            <Typography variant="h6" gutterBottom>{selectedLabel || t('Endpoint')}</Typography>

            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('Findings')}
            </Typography>
            {endpointFindingsLoading && (
              <Box sx={{ minHeight: 60 }}>
                <Loader variant="inElement" size="sm" />
              </Box>
            )}
            {!endpointFindingsLoading && endpointFindingGroups.length === 0 && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: 'block',
                  mb: 1,
                }}
              >
                {t('No findings on this endpoint')}
              </Typography>
            )}
            {!endpointFindingsLoading && endpointFindingGroups.map(g => (
              <Box key={g.type} sx={{ mb: 1 }}>
                <Typography
                  variant="caption"
                  sx={{
                    display: 'block',
                    fontWeight: 600,
                    color: 'text.secondary',
                    textTransform: 'uppercase',
                    letterSpacing: 0.4,
                  }}
                >
                  {`${g.type} (${g.values.length})`}
                </Typography>
                {g.values.map((v, i) => (
                  <Typography key={`${g.type}-${i}`} variant="body2" noWrap title={v}>
                    {v}
                  </Typography>
                ))}
              </Box>
            ))}

            <Typography variant="subtitle2" color="text.secondary" gutterBottom sx={{ mt: 1 }}>
              {`${t('Executions')} (${executions.length})`}
            </Typography>
            {executions.slice(0, EXEC_DISPLAY_CAP).map((e) => {
              const status = t(statusLabelKey(e.status));
              const highlighted = !!e.ref && highlightedExecutionIds.has(e.ref);
              return (
                <Box
                  key={e.id}
                  ref={(el: HTMLDivElement | null) => {
                    if (!e.id) {
                      return;
                    }
                    if (el) {
                      feedRowRefs.current.set(e.id, el);
                    } else {
                      feedRowRefs.current.delete(e.id);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                  onClick={() => e.ref && openExecutionDetail(e.ref)}
                  onKeyDown={(ev) => {
                    if (e.ref && (ev.key === 'Enter' || ev.key === ' ')) {
                      ev.preventDefault();
                      openExecutionDetail(e.ref);
                    }
                  }}
                  sx={{
                    'display': 'flex',
                    'alignItems': 'center',
                    'gap': 1,
                    'py': 0.5,
                    'px': 0.5,
                    'borderRadius': 1,
                    'borderBottom': `1px solid ${theme.palette.divider}`,
                    'backgroundColor': highlighted ? 'action.selected' : undefined,
                    // A left accent so the finding's producing execution stands out in the feed.
                    'borderLeft': highlighted ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
                    'cursor': 'pointer',
                    '&:hover': { backgroundColor: 'action.hover' },
                    '&:focus-visible': {
                      outline: `2px solid ${theme.palette.primary.main}`,
                      outlineOffset: -2,
                    },
                  }}
                >
                  <span
                    role="img"
                    aria-label={status}
                    title={status}
                    style={{
                      flex: '0 0 auto',
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: attackPathStatusColor(theme, e.status),
                    }}
                  />
                  <div style={{ minWidth: 0 }}>
                    <Typography variant="body2" noWrap>{e.payloadName || e.label}</Typography>
                    <Typography variant="caption" color="text.secondary" noWrap>
                      {[status, e.agentName, e.privilege].filter(Boolean).join(' · ')}
                    </Typography>
                  </div>
                </Box>
              );
            })}
            {executions.length > EXEC_DISPLAY_CAP && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: 'block',
                  pt: 1,
                }}
              >
                {`+${executions.length - EXEC_DISPLAY_CAP} ${t('more')}`}
              </Typography>
            )}
          </Paper>
        )}

        {detailExecutionId && (
          <ExecutionResultTerminalPanel
            loading={detailLoading}
            detail={detail}
            onClose={() => setDetailExecutionId(null)}
            onFocusOnMap={selectedNodeId
              ? () => setFocusRequest(prev => ({
                  nodeId: selectedNodeId,
                  nonce: (prev?.nonce ?? 0) + 1,
                }))
              : undefined}
          />
        )}
      </div>

      <Drawer
        anchor="right"
        open={drawerCategory !== null}
        onClose={() => setDrawerCategory(null)}
        elevation={1}
      >
        <Box
          role="presentation"
          sx={{
            width: 360,
            px: 3,
            py: 2.5,
          }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            mb: 1,
          }}
          >
            <Typography variant="h6">{`${drawerLabel} (${findingsPage?.total ?? 0})`}</Typography>
            <IconButton size="small" aria-label={t('Close')} onClick={() => setDrawerCategory(null)}>
              <Close />
            </IconButton>
          </Box>
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
          {findingsLoading && (
            <Box sx={{ minHeight: 120 }}>
              <Loader variant="inElement" size="sm" />
            </Box>
          )}
          {!findingsLoading && (findingsPage?.items?.length ?? 0) === 0 && (
            <Alert severity="info">{t('No findings')}</Alert>
          )}
          {!findingsLoading && (findingsPage?.items ?? []).map((item, index) => (
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
              <Typography variant="body2" noWrap title={item.value}>{item.value}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap title={item.endpointKey}>
                {item.endpointKey}
              </Typography>
            </Box>
          ))}
          {!findingsLoading && findingsPage
            && (findingsPage.items?.length ?? 0) < (findingsPage.total ?? 0) && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                display: 'block',
                pt: 1,
              }}
            >
              {`+${(findingsPage.total ?? 0) - (findingsPage.items?.length ?? 0)} ${t('more')}`}
            </Typography>
          )}
        </Box>
      </Drawer>
    </div>
  );
};

export default SimulationAttackPath;
