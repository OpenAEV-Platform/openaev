import { Refresh } from '@mui/icons-material';
import { Alert, Autocomplete, Box, Chip, IconButton, Paper, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ReactFlowProvider } from '@xyflow/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchAttackPathGraph, fetchAttackPathSimulations, fetchEndpointFindings, fetchEndpointRelations } from '../../../../../actions/attack-path-poc/attack-path-poc-actions';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathDTO, AttackPathEdges, AttackPathExpandDTO, AttackPathNodeDTO, AttackPathSimSummaryRow } from '../../../../../utils/api-types';
import attackPathStatusColor from './attack-path-poc-colors';
import { buildAttackPathFlow } from './attack-path-poc-flow-helpers';
import AttackPathPocFlow from './AttackPathPocFlow';

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
 * Attack-path execution-store POC tab (issue 6647), gated by the ATTACK_PATH_POC preview feature.
 * Loads a simulation's graph collapsed by default (bounded node count), and on an endpoint click
 * lazy-loads that endpoint's findings and executions — the collapsed-first, expand-on-demand shape
 * the benchmark showed is what keeps a large simulation renderable.
 */
const SimulationAttackPathPoc = () => {
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

  const [extraNodes, setExtraNodes] = useState<AttackPathNodeDTO[]>([]);
  const [extraEdges, setExtraEdges] = useState<AttackPathEdges[]>([]);
  const [expandedRefs, setExpandedRefs] = useState<Set<string>>(new Set());
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedLabel, setSelectedLabel] = useState<string>('');
  const [executions, setExecutions] = useState<AttackPathNodeDTO[]>([]);

  const load = useCallback(() => {
    if (!simulationId) {
      return;
    }
    setLoading(true);
    setExtraNodes([]);
    setExtraEdges([]);
    setExpandedRefs(new Set());
    setSelectedNodeId(null);
    setExecutions([]);
    fetchAttackPathGraph(simulationId, mode === 'auto' ? undefined : mode)
      .then(r => setDto(r.data))
      .finally(() => setLoading(false));
  }, [simulationId, mode]);

  useEffect(() => {
    load();
  }, [load]);

  // Load the picker options once: the simulations that have attack-path data in this tenant.
  useEffect(() => {
    fetchAttackPathSimulations().then(r => setSimulations(r.data ?? []));
  }, []);

  const onEndpointClick = useCallback((nodeId: string, ref?: string, label?: string) => {
    setSelectedNodeId(nodeId);
    setSelectedLabel(label ?? '');
    setExecutions([]);
    if (!ref) {
      return;
    }
    // Findings are already nodes in the full graph; only the collapsed graph lazy-loads them, and
    // only once per endpoint. The executions feed is per click (it drives the side panel).
    if (dto?.mode !== 'full' && !expandedRefs.has(ref)) {
      setExpandedRefs(prev => new Set(prev).add(ref));
      fetchEndpointFindings(simulationId, ref).then((r) => {
        const expand = r.data;
        setExtraNodes(prev => mergeNodesById(prev, [...(expand.findingTypes ?? []), ...(expand.findings ?? [])]));
        setExtraEdges((prev) => {
          const next = new Map(prev.map(e => [e.edgeId, e]));
          for (const e of expandEdges(expand)) {
            if (e.edgeId) next.set(e.edgeId, e);
          }
          return [...next.values()];
        });
      });
    }
    fetchEndpointRelations(simulationId, ref).then(r => setExecutions(r.data.executions ?? []));
  }, [simulationId, dto?.mode, expandedRefs]);

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
          }}
          >
            <Chip label={`${t('Endpoints')} ${counters.endpoints ?? 0}`} size="small" />
            <Chip label={`${t('Credentials')} ${counters.credentials ?? 0}`} size="small" />
            <Chip label={`${t('Users')} ${counters.users ?? 0}`} size="small" />
            <Chip label={`${t('CVEs')} ${counters.cves ?? 0}`} size="small" />
            <Chip label={`${t('Ports')} ${counters.ports ?? 0}`} size="small" />
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
        <Paper
          variant="outlined"
          style={{
            flex: 1,
            minWidth: 0,
            position: 'relative',
          }}
        >
          {loading && <Loader />}
          {!loading && nodes.length === 0 && (
            <Alert severity="info" sx={{ m: 2 }}>
              {t('No attack-path data for this simulation. Seed data (POST /poc/attack-path/seed) or enter a seeded simulation id above.')}
            </Alert>
          )}
          {!loading && nodes.length > 0 && (
            <ReactFlowProvider>
              <AttackPathPocFlow nodes={nodes} edges={edges} onEndpointClick={onEndpointClick} />
            </ReactFlowProvider>
          )}
        </Paper>

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
            {executions.slice(0, EXEC_DISPLAY_CAP).map(e => (
              <Box
                key={e.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  py: 0.5,
                  borderBottom: `1px solid ${theme.palette.divider}`,
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
            ))}
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
      </div>
    </div>
  );
};

export default SimulationAttackPathPoc;
