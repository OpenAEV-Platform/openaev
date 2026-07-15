import { Close, Refresh } from '@mui/icons-material';
import { Alert, Autocomplete, Box, Button, Chip, Drawer, IconButton, Paper, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ReactFlowProvider } from '@xyflow/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { fetchAttackPathGraph, fetchAttackPathSimulations, fetchEndpointFindings, fetchEndpointRelations, fetchExecutionDetail, fetchFindingsByCategory } from '../../../../../actions/attack-path/attack-path-actions';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathDTO, AttackPathEdges, AttackPathExecutionDetailDTO, AttackPathExpandDTO, AttackPathFindingItemDTO, AttackPathFindingPageDTO, AttackPathNodeDTO, AttackPathSimSummaryRow } from '../../../../../utils/api-types';
import attackPathStatusColor from './attack-path-colors';
import { buildAttackPathFlow } from './attack-path-flow-helpers';
import AttackPathFlow, { type AttackPathFocusRequest } from './AttackPathFlow';
import ExecutionResultTerminalPanel from './ExecutionResultTerminalPanel';

type Mode = 'auto' | 'full' | 'collapsed';

// A hot endpoint can have many executions; the read is bounded to the one endpoint, but the side
// panel still renders a list, so cap it (the backend /relations read would be paginated in prod).
const EXEC_DISPLAY_CAP = 100;

// Merge extra nodes into a base set, keeping the first occurrence of each id.
const mergeNodesById = (base: AttackPathNodeDTO[], extra: AttackPathNodeDTO[]): AttackPathNodeDTO[] => {
  const map = new Map(base.filter(n => n.id).map(n => [n.id, n]));
  for (const n of extra) {
    if (n.id && !map.has(n.id)) {
      map.set(n.id, n);
    }
  }
  return [...map.values()];
};

// The expand endpoint returns finding-type and finding nodes; their asset/type back-references let us
// rebuild the edges the collapsed graph did not carry.
const expandEdges = (expand: AttackPathExpandDTO): AttackPathEdges[] => {
  const edges: AttackPathEdges[] = [];
  for (const ft of expand.findingTypes ?? []) {
    if (ft.assetNodeId && ft.id) {
      edges.push({
        edgeId: `${ft.assetNodeId}-${ft.id}`,
        edgeSourceId: ft.assetNodeId,
        edgeTargetId: ft.id,
        type: 'EDGE_ENDPOINT_FINDINGS_TYPE',
        count: 1,
      });
    }
  }
  for (const f of expand.findings ?? []) {
    if (f.findingsTypeNodeId && f.id) {
      edges.push({
        edgeId: `${f.findingsTypeNodeId}-${f.id}`,
        edgeSourceId: f.findingsTypeNodeId,
        edgeTargetId: f.id,
        type: 'EDGE_FINDINGS_TYPE_FINDING',
        count: 1,
      });
    }
  }
  return edges;
};

/**
 * Attack-path execution-store POC tab (issue 6647), gated by the ATTACK_PATH preview feature.
 * Loads a simulation's graph collapsed by default (bounded node count), and on an endpoint click
 * lazy-loads that endpoint's findings and executions — the collapsed-first, expand-on-demand shape
 * the benchmark showed is what keeps a large simulation renderable.
 */
const SimulationAttackPath = () => {
  const { exerciseId } = useParams() as { exerciseId: string };
  const theme = useTheme();
  const { t } = useFormatter();

  // Default to this exercise's id; the picker (or free text) lets the POC point at a seeded dataset.
  const [simulationInput, setSimulationInput] = useState(exerciseId ?? '');
  const [simulationId, setSimulationId] = useState(exerciseId ?? '');
  const [simulations, setSimulations] = useState<AttackPathSimSummaryRow[]>([]);
  const [mode, setMode] = useState<Mode>('collapsed');
  const [dto, setDto] = useState<AttackPathDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const [extraNodes, setExtraNodes] = useState<AttackPathNodeDTO[]>([]);
  const [extraEdges, setExtraEdges] = useState<AttackPathEdges[]>([]);
  const [expandedRefs, setExpandedRefs] = useState<Set<string>>(new Set());
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedLabel, setSelectedLabel] = useState<string>('');
  const [executions, setExecutions] = useState<AttackPathNodeDTO[]>([]);

  // Findings drawer: a widget opens a right drawer listing that category's findings.
  const [drawerCategory, setDrawerCategory] = useState<string | null>(null);
  const [drawerLabel, setDrawerLabel] = useState<string>('');
  const [findingsPage, setFindingsPage] = useState<AttackPathFindingPageDTO | null>(null);
  const [findingsLoading, setFindingsLoading] = useState(false);

  // Cross-focus: clicking a finding item centers its endpoint (focusRequest) and highlights
  // the producing executions in the feed (by their raw ids).
  const [focusRequest, setFocusRequest] = useState<AttackPathFocusRequest | null>(null);
  const [highlightedExecutionIds, setHighlightedExecutionIds] = useState<Set<string>>(new Set());
  const feedRowRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // Execution Result & Terminal drawer: clicking a feed entry loads and opens its detail.
  const [detailExecutionId, setDetailExecutionId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AttackPathExecutionDetailDTO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = useCallback(() => {
    if (!simulationId) {
      return;
    }
    setLoading(true);
    setError(false);
    setExtraNodes([]);
    setExtraEdges([]);
    setExpandedRefs(new Set());
    setSelectedNodeId(null);
    setExecutions([]);
    // Close the drawers and clear any cross-focus so nothing carries over between simulations.
    setDrawerCategory(null);
    setDetailExecutionId(null);
    setHighlightedExecutionIds(new Set());
    setFocusRequest(null);
    fetchAttackPathGraph(simulationId, mode === 'auto' ? undefined : mode)
      .then(r => setDto(r.data))
      .catch(() => {
        // Clear the previous simulation's graph so a failed load shows an error, not stale data.
        setDto(null);
        setError(true);
      })
      .finally(() => setLoading(false));
  }, [simulationId, mode]);

  useEffect(() => {
    load();
  }, [load]);

  // Load the picker options once: the simulations that have attack-path data in this tenant.
  useEffect(() => {
    fetchAttackPathSimulations()
      .then(r => setSimulations(r.data ?? []))
      .catch(() => setSimulations([]));
  }, []);

  const onEndpointClick = useCallback((nodeId: string, ref?: string, label?: string) => {
    setSelectedNodeId(nodeId);
    setSelectedLabel(label ?? '');
    setExecutions([]);
    // A plain node click focuses no specific execution; a finding-item click sets these after.
    setHighlightedExecutionIds(new Set());
    if (!ref) {
      return;
    }
    // Findings are already nodes in the full graph; only the collapsed graph lazy-loads them, and
    // only once per endpoint. The executions feed is per click (it drives the side panel).
    if (dto?.mode !== 'full' && !expandedRefs.has(ref)) {
      setExpandedRefs(prev => new Set(prev).add(ref));
      fetchEndpointFindings(simulationId, ref)
        .then((r) => {
          const expand = r.data;
          setExtraNodes(prev => mergeNodesById(prev, [...(expand.findingTypes ?? []), ...(expand.findings ?? [])]));
          setExtraEdges((prev) => {
            const next = new Map(prev.map(e => [e.edgeId, e]));
            for (const e of expandEdges(expand)) {
              if (e.edgeId) next.set(e.edgeId, e);
            }
            return [...next.values()];
          });
        })
        .catch(() => {
          // Forget the ref on failure so a later click retries the expand instead of no-op.
          setExpandedRefs((prev) => {
            const next = new Set(prev);
            next.delete(ref);
            return next;
          });
        });
    }
    fetchEndpointRelations(simulationId, ref)
      .then(r => setExecutions(r.data.executions ?? []))
      .catch(() => setExecutions([]));
  }, [simulationId, dto?.mode, expandedRefs]);

  // Open the findings drawer for a widget category and load its first page.
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

  // Cross-focus: clicking a finding item closes the drawer, focuses its endpoint
  // on the map (center + select, which highlights the edges into it), loads that endpoint's feed, and
  // highlights the producing executions in it.
  const onFindingItemClick = useCallback(
    (item: AttackPathFindingItemDTO) => {
      if (!item.endpointNodeId || !item.endpointKey) {
        return;
      }
      setDrawerCategory(null);
      // Use the endpoint's friendly label (hostname) for the panel title, like a direct node click,
      // not the raw key.
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

  // Merge the base graph with whatever endpoints have been expanded, then lay it out; finally mark
  // the selected endpoint and the edges touching it so the click highlights its path.
  const { nodes, edges } = useMemo(() => {
    if (!dto) {
      return {
        nodes: [],
        edges: [],
      };
    }
    const merged: AttackPathDTO = {
      ...dto,
      attackPathNodes: mergeNodesById(dto.attackPathNodes ?? [], extraNodes),
      attackPathEdges: [...(dto.attackPathEdges ?? []), ...extraEdges],
    };
    const flow = buildAttackPathFlow(merged);
    const withSelection = {
      nodes: flow.nodes.map(n => ({
        ...n,
        selected: n.id === selectedNodeId,
      })),
      edges: flow.edges.map(e => ({
        ...e,
        selected: e.source === selectedNodeId || e.target === selectedNodeId,
      })),
    };
    return withSelection;
  }, [dto, extraNodes, extraEdges, selectedNodeId]);

  const counters = dto?.counters;

  const applySimulation = () => {
    setSimulationId(simulationInput.trim());
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: 'calc(100vh - 260px)',
      gap: theme.spacing(1),
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(2),
        flexWrap: 'wrap',
      }}
      >
        <Autocomplete
          freeSolo
          size="small"
          options={simulations}
          value={simulationInput}
          inputValue={simulationInput}
          getOptionLabel={o => (typeof o === 'string' ? o : (o.simulationId ?? ''))}
          filterOptions={(opts, state) => opts.filter(o => (o.simulationId ?? '').toLowerCase().includes(state.inputValue.toLowerCase()))}
          onInputChange={(_, v) => setSimulationInput(v)}
          onChange={(_, v) => {
            const id = typeof v === 'string' ? v : v?.simulationId;
            if (id) {
              setSimulationInput(id);
              setSimulationId(id);
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
                <span>{o.simulationId}</span>
                <span style={{
                  marginLeft: 'auto',
                  opacity: 0.65,
                  fontSize: 12,
                }}
                >
                  {`${o.endpointCount} ${t('endpoints')} · ${o.executionCount} ${t('exec.')}`}
                </span>
              </li>
            );
          }}
          renderInput={params => (
            <TextField
              {...params}
              label={t('Simulation id')}
              onKeyDown={(e) => {
                if (e.key === 'Enter') applySimulation();
              }}
              onBlur={applySimulation}
            />
          )}
          sx={{ minWidth: 440 }}
        />
        <ToggleButtonGroup
          size="small"
          exclusive
          value={mode}
          onChange={(_, v: Mode | null) => v && setMode(v)}
        >
          <ToggleButton value="collapsed">{t('Collapsed')}</ToggleButton>
          <ToggleButton value="full">{t('Full')}</ToggleButton>
          <ToggleButton value="auto">{t('Auto')}</ToggleButton>
        </ToggleButtonGroup>
        <Tooltip title={t('Reload')}>
          <IconButton size="small" onClick={load}><Refresh /></IconButton>
        </Tooltip>
        {counters && (
          <div style={{
            display: 'flex',
            gap: theme.spacing(1),
            marginLeft: 'auto',
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
          >
            {/* Endpoints is a count only for now; its per-endpoint drawer is a follow-up. */}
            <Chip label={`${t('Endpoints')} ${counters.endpoints ?? 0}`} size="small" />
            {/* Files has no finding type in the seed yet, so its count is 0 until ingestion (open question). */}
            {[
              {
                category: 'files',
                label: t('Files'),
                count: 0,
              },
              {
                category: 'credentials',
                label: t('Credentials'),
                count: counters.credentials ?? 0,
              },
              {
                category: 'users',
                label: t('Users'),
                count: counters.users ?? 0,
              },
              {
                category: 'cves',
                label: t('CVEs'),
                count: counters.cves ?? 0,
              },
            ].map(w => (
              <Button
                key={w.category}
                size="small"
                variant="outlined"
                onClick={() => openFindingsDrawer(w.category, w.label)}
              >
                {`${w.label} ${w.count}`}
              </Button>
            ))}
            {dto?.mode && <Chip label={dto.mode} size="small" color="primary" variant="outlined" />}
          </div>
        )}
      </div>

      <div style={{
        display: 'flex',
        flex: 1,
        minHeight: 0,
        gap: theme.spacing(1),
      }}
      >
        {selectedNodeId && (
          <Paper
            variant="outlined"
            style={{
              width: 340,
              overflow: 'auto',
              padding: theme.spacing(2),
            }}
          >
            <Typography variant="h6" gutterBottom>{selectedLabel || t('Endpoint')}</Typography>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {`${t('Executions')} (${executions.length})`}
            </Typography>
            {executions.slice(0, EXEC_DISPLAY_CAP).map((e) => {
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
                  <span style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: attackPathStatusColor(theme, e.status),
                  }}
                  />
                  <div style={{ minWidth: 0 }}>
                    <Typography variant="body2" noWrap>{e.payloadName || e.label}</Typography>
                    <Typography variant="caption" color="text.secondary" noWrap>
                      {[e.agentName, e.privilege].filter(Boolean).join(' · ')}
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

        <Paper
          variant="outlined"
          style={{
            flex: 1,
            minWidth: 0,
            position: 'relative',
          }}
        >
          {loading && <Loader />}
          {!loading && error && (
            <Alert severity="error" sx={{ m: 2 }}>
              {t('Failed to load the attack-path graph. Check the simulation id or reload.')}
            </Alert>
          )}
          {!loading && !error && nodes.length === 0 && (
            <Alert severity="info" sx={{ m: 2 }}>
              {t('No attack-path data for this simulation. Seed data (POST /attack-path/seed) or enter a seeded simulation id above.')}
            </Alert>
          )}
          {!loading && !error && nodes.length > 0 && (
            <ReactFlowProvider>
              <AttackPathFlow nodes={nodes} edges={edges} onEndpointClick={onEndpointClick} focusRequest={focusRequest} />
            </ReactFlowProvider>
          )}
        </Paper>
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
