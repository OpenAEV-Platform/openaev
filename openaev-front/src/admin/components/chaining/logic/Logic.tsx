import { Add, BoltOutlined, TerminalOutlined } from '@mui/icons-material';
import { Button, ButtonBase, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  addEdge,
  type Connection,
  type Edge,
  MarkerType,
  type Node,
  ReactFlow,
  useEdgesState,
  useNodesState,
} from '@xyflow/react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { findEndpoints } from '../../../../actions/assets/endpoint-actions';
import {
  createCondition,
  createStep,
  deleteCondition,
  deleteStep,
  fetchConditions,
  fetchSteps,
  updateCondition,
  updateStep,
} from '../../../../actions/chaining/chaining-actions';
import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { EndpointOutput, EventOutput, StepOutput } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import edgeTypes from './edges';
import type { CreateActionData } from './forms/CreateActionForm';
import CreateActionForm from './forms/CreateActionForm';
import type { ConditionRow, CreateEventData } from './forms/CreateEventForm';
import CreateEventForm from './forms/CreateEventForm';
import type { MapperConditionRow } from './forms/MapperConditionRow';
import type { UpdateActionData } from './forms/UpdateActionForm';
import UpdateActionForm from './forms/UpdateActionForm';
import type { UpdateEventData } from './forms/UpdateEventForm';
import UpdateEventForm from './forms/UpdateEventForm';
import nodeTypes from './nodes';

type DrawerView = 'choose' | 'action' | 'event' | 'editAction' | 'editEvent';

// Extended node data stored in React state so update forms can be pre-populated
interface ActionMeta {
  inject_title: string;
  inject_description: string;
  inject_injector_contract?: string;
  inject_injector?: string;
  inject_assets: string[];
  inject_asset_objects: EndpointOutput[];
  step_condition_ids: string[];
  step_conditions: MapperConditionRow[];
  contract_fields: ContractElement[];
}
interface EventMeta {
  event_name: string;
  event_description: string;
  root_logical_type: string;
  conditions: ConditionRow[];
}

interface LogicProps { workflowId: string | undefined }

const Logic = ({ workflowId }: LogicProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // Extra metadata per node (keyed by node id)
  const [actionMetas, setActionMetas] = useState<Record<string, ActionMeta>>({});
  const [eventMetas, setEventMetas] = useState<Record<string, EventMeta>>({});

  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerView, setDrawerView] = useState<DrawerView>('choose');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  // -- Load data from API on mount --
  useEffect(() => {
    if (!workflowId) return;
    const load = async () => {
      const [stepsRes, conditionsRes] = await Promise.all([
        fetchSteps(workflowId),
        fetchConditions(workflowId),
      ]);
      const steps: StepOutput[] = stepsRes.data;
      const events: EventOutput[] = conditionsRes.data;

      const newActionMetas: Record<string, ActionMeta> = {};
      const actionNodes: Node[] = steps
        .filter((s): s is StepOutput & { step_id: string } => !!s.step_id)
        .map((s, i) => {
          const data = s.step_data as Record<string, unknown> | undefined;
          const title = (data?.inject_title as string) ?? `Step ${s.step_id.slice(0, 6)}`;
          const desc = (data?.inject_description as string) ?? '';
          const rawContract = data?.inject_injector_contract;
          const contract = typeof rawContract === 'string'
            ? rawContract
            : (rawContract as Record<string, unknown>)?.injector_contract_id as string ?? '';
          const assets = (data?.inject_assets as string[]) ?? [];
          const condIds = s.step_condition_ids ?? [];
          const rawInjector = data?.inject_injector;
          const injector = typeof rawInjector === 'string'
            ? rawInjector
            : (rawInjector as Record<string, unknown>)?.injector_id as string ?? undefined;
          newActionMetas[s.step_id] = {
            inject_title: title,
            inject_description: desc,
            inject_injector_contract: contract,
            inject_injector: injector,
            inject_assets: assets,
            inject_asset_objects: [],
            step_condition_ids: condIds,
            step_conditions: (((s as unknown as Record<string, unknown>).step_mapper_conditions ?? []) as Array<{
              condition_key_type?: string;
              condition_key?: string;
              condition_mapping_type?: string;
            }>).map(mc => ({
              condition_key_type: mc.condition_key_type ?? 'text',
              condition_key: mc.condition_key ?? '',
              condition_mapping_type: mc.condition_mapping_type ?? 'GLOBAL',
            })),
            contract_fields: [],
          };
          return {
            id: s.step_id,
            type: 'action' as const,
            position: {
              x: 400,
              y: 100 + i * 140,
            },
            data: { label: title },
          };
        });

      // Resolve asset objects for all actions
      const allAssetIds = Array.from(new Set(Object.values(newActionMetas).flatMap(m => m.inject_assets)));
      let assetMap: Record<string, EndpointOutput> = {};
      if (allAssetIds.length > 0) {
        const assetRes = await findEndpoints(allAssetIds) as { data: EndpointOutput[] };
        assetMap = Object.fromEntries((assetRes.data ?? []).map(a => [a.asset_id, a]));
        for (const meta of Object.values(newActionMetas)) {
          meta.inject_asset_objects = meta.inject_assets
            .map(id => assetMap[id])
            .filter(Boolean);
        }
      }

      const newEventMetas: Record<string, EventMeta> = {};
      const eventNodes: Node[] = events.map((e, i) => {
        const allConds = e.event_conditions ?? [];
        // Find root condition (has no parent) — its type is the logical operator
        const rootCond = allConds.find(c => !c.condition_parent_id);
        // Child conditions are the actual comparison conditions
        const childConds: ConditionRow[] = allConds
          .filter(c => !!c.condition_parent_id)
          .map(c => ({
            condition_type: (c.condition_type as string) ?? 'EQ',
            condition_key_type: (c.condition_key_type as string) ?? 'status',
            condition_value: c.condition_value ?? '',
          }));
        newEventMetas[e.event_id] = {
          event_name: e.event_name ?? '',
          event_description: e.event_description ?? '',
          root_logical_type: (rootCond?.condition_type as string) ?? 'AND',
          conditions: childConds.length > 0
            ? childConds
            : [{
                condition_type: 'EQ',
                condition_key_type: 'status',
                condition_value: 'SUCCESS',
              }],
        };
        return {
          id: e.event_id,
          type: 'event' as const,
          position: {
            x: 50,
            y: 100 + i * 140,
          },
          data: { label: e.event_name },
        };
      });

      // Resolve contract fields for all actions that have a contract
      const contractIds = Array.from(new Set(
        Object.values(newActionMetas)
          .map(m => m.inject_injector_contract)
          .filter((id): id is string => !!id),
      ));
      const contractFieldsMap: Record<string, ContractElement[]> = {};
      await Promise.all(contractIds.map(async (cid) => {
        try {
          const res = await directFetchInjectorContract(cid) as { data: { injector_contract_content?: string } };
          if (res.data?.injector_contract_content) {
            const parsed = JSON.parse(res.data.injector_contract_content);
            contractFieldsMap[cid] = (parsed.fields ?? []) as ContractElement[];
          }
        } catch {
          // contract not found or content not parseable — leave empty
        }
      }));
      for (const meta of Object.values(newActionMetas)) {
        if (meta.inject_injector_contract && contractFieldsMap[meta.inject_injector_contract]) {
          meta.contract_fields = contractFieldsMap[meta.inject_injector_contract];
        }
      }

      setActionMetas(newActionMetas);
      setEventMetas(newEventMetas);
      setNodes([...eventNodes, ...actionNodes]);

      // Reconstruct edges from step_condition_ids
      const reconstructedEdges: Edge[] = [];
      for (const [stepId, meta] of Object.entries(newActionMetas)) {
        for (const condId of meta.step_condition_ids) {
          // condId is the root condition ID = event_id
          if (newEventMetas[condId]) {
            reconstructedEdges.push({
              id: `${condId}-${stepId}`,
              source: condId,
              target: stepId,
              type: 'deletable',
              markerEnd: { type: MarkerType.ArrowClosed },
            });
          }
        }
      }
      setEdges(reconstructedEdges);
    };
    load();
  }, [workflowId]);

  const onConnect = useCallback(
    (params: Connection) => {
      const sourceNode = nodes.find(n => n.id === params.source);
      if (sourceNode?.type !== 'event') return;
      const targetNode = nodes.find(n => n.id === params.target);
      if (targetNode?.type !== 'action') return;

      const eventId = params.source!;
      const stepId = params.target!;
      const meta = actionMetas[stepId];
      if (!meta || !workflowId) return;

      const currentCondIds = meta.step_condition_ids;
      if (currentCondIds.includes(eventId)) return;
      const newCondIds = [...currentCondIds, eventId];

      const stepConditions = meta.step_conditions.map((mc, i) => ({
        condition_temporary_id: String(i),
        condition_type: 'MAPPER' as const,
        condition_key_type: mc.condition_key_type as 'text',
        condition_key: mc.condition_key,
        condition_mapping_type: mc.condition_mapping_type as 'GLOBAL',
      }));

      updateStep(stepId, {
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION',
        step_condition_ids: newCondIds,
        step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
        step_data_step: {
          inject_title: meta.inject_title,
          inject_description: meta.inject_description,
          inject_injector_contract: meta.inject_injector_contract,
          inject_injector: meta.inject_injector,
          inject_assets: meta.inject_assets,
        },
      }).then(() => {
        setActionMetas(prev => ({
          ...prev,
          [stepId]: {
            ...prev[stepId],
            step_condition_ids: newCondIds,
          },
        }));
      });

      setEdges(eds => addEdge({
        ...params,
        type: 'deletable',
        markerEnd: { type: MarkerType.ArrowClosed },
      }, eds));
    },
    [nodes, setEdges, workflowId, actionMetas],
  );

  const removeEdge = useCallback(
    (source: string, target: string) => {
      if (!workflowId) return;
      const stepId = target;
      const eventId = source;
      const meta = actionMetas[stepId];
      if (!meta) return;

      const newCondIds = meta.step_condition_ids.filter(id => id !== eventId);

      const stepConditions = meta.step_conditions.map((mc, i) => ({
        condition_temporary_id: String(i),
        condition_type: 'MAPPER' as const,
        condition_key_type: mc.condition_key_type as 'text',
        condition_key: mc.condition_key,
        condition_mapping_type: mc.condition_mapping_type as 'GLOBAL',
      }));

      updateStep(stepId, {
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION',
        step_condition_ids: newCondIds,
        step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
        step_data_step: {
          inject_title: meta.inject_title,
          inject_description: meta.inject_description,
          inject_injector_contract: meta.inject_injector_contract,
          inject_injector: meta.inject_injector,
          inject_assets: meta.inject_assets,
        },
      }).then(() => {
        setActionMetas(prev => ({
          ...prev,
          [stepId]: {
            ...prev[stepId],
            step_condition_ids: newCondIds,
          },
        }));
        setEdges(eds => eds.filter(e => !(e.source === eventId && e.target === stepId)));
      });
    },
    [workflowId, actionMetas, setEdges],
  );

  const onEdgesDelete = useCallback(
    (deletedEdges: Edge[]) => {
      for (const edge of deletedEdges) {
        removeEdge(edge.source, edge.target);
      }
    },
    [removeEdge],
  );

  const onDeleteEdgeClick = useCallback(
    (_edgeId: string, source: string, target: string) => {
      removeEdge(source, target);
    },
    [removeEdge],
  );

  const handleOpenDrawer = () => {
    setSelectedNodeId(null);
    setDrawerView('choose');
    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setSelectedNodeId(null);
  };

  const editNode = useCallback((nodeId: string, type: string) => {
    setSelectedNodeId(nodeId);
    setDrawerView(type === 'action' ? 'editAction' : 'editEvent');
    setDrawerOpen(true);
  }, []);

  // -- CREATE ACTION --
  const submitAction = (data: CreateActionData) => {
    if (!workflowId) return;
    const stepConditions = data.step_conditions.map((mc, i) => ({
      condition_temporary_id: String(i),
      condition_type: 'MAPPER' as const,
      condition_key_type: mc.condition_key_type as 'text',
      condition_key: mc.condition_key,
      condition_mapping_type: mc.condition_mapping_type as 'GLOBAL',
    }));
    createStep({
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION',
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: data.inject_title,
        inject_description: data.inject_description,
        inject_injector_contract: data.inject_injector_contract,
        inject_injector: data.inject_injector,
        inject_assets: data.inject_assets,
        inject_tags: [],
        inject_content: {
          expectations: [],
          obfuscator: 'plain-text',
        },
        inject_all_teams: false,
        inject_teams: [],
        inject_asset_groups: [],
        inject_documents: [],
        inject_depends_duration: 0,
        inject_depends_on: [],
      },
    }).then((res) => {
      const step = res.data;
      if (!step.step_id) return;
      setActionMetas(prev => ({
        ...prev,
        [step.step_id!]: {
          inject_title: data.inject_title,
          inject_description: data.inject_description,
          inject_injector_contract: data.inject_injector_contract,
          inject_injector: data.inject_injector,
          inject_assets: data.inject_assets,
          inject_asset_objects: [],
          step_condition_ids: [],
          step_conditions: data.step_conditions,
          contract_fields: [],
        },
      }));
      setNodes(nds => [
        ...nds,
        {
          id: step.step_id!,
          type: 'action' as const,
          position: {
            x: 400 + Math.random() * 100,
            y: 100 + nds.length * 140,
          },
          data: { label: data.inject_title },
        },
      ]);
    });
    setDrawerOpen(false);
  };

  // -- BUILD event_conditions array for the API (root + children) --
  const buildEventConditions = (rootLogicalType: string, conditions: ConditionRow[]) => {
    const ROOT_TMP_ID = 'root';
    const root = {
      condition_temporary_id: ROOT_TMP_ID,
      condition_type: rootLogicalType as 'AND',
    };
    const children = conditions.map((c, i) => ({
      condition_temporary_id: String(i),
      condition_temporary_id_condition_parent: ROOT_TMP_ID,
      condition_type: c.condition_type as 'AND',
      condition_key_type: c.condition_key_type as 'status',
      condition_value: c.condition_value,
    }));
    return [root, ...children];
  };

  // -- CREATE EVENT --
  const submitEvent = (data: CreateEventData) => {
    if (!workflowId) return;
    createCondition({
      event_name: data.event_name,
      event_description: data.event_description || undefined,
      event_workflow_id: workflowId,
      event_conditions: buildEventConditions(data.root_logical_type, data.conditions),
    }).then((res) => {
      const event = res.data;
      setEventMetas(prev => ({
        ...prev,
        [event.event_id]: {
          event_name: data.event_name,
          event_description: data.event_description,
          root_logical_type: data.root_logical_type,
          conditions: data.conditions,
        },
      }));
      setNodes(nds => [
        ...nds,
        {
          id: event.event_id,
          type: 'event' as const,
          position: {
            x: 50 + Math.random() * 100,
            y: 100 + nds.length * 140,
          },
          data: { label: event.event_name },
        },
      ]);
    });
    setDrawerOpen(false);
  };

  // -- UPDATE ACTION --
  const handleUpdateAction = (data: UpdateActionData) => {
    if (!workflowId || !selectedNodeId) return;
    const meta = actionMetas[selectedNodeId];
    const stepConditions = data.step_conditions.map((mc, i) => ({
      condition_temporary_id: String(i),
      condition_type: 'MAPPER' as const,
      condition_key_type: mc.condition_key_type as 'text',
      condition_key: mc.condition_key,
      condition_mapping_type: mc.condition_mapping_type as 'GLOBAL',
    }));
    updateStep(selectedNodeId, {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION',
      step_condition_ids: meta?.step_condition_ids ?? [],
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: data.inject_title,
        inject_description: data.inject_description,
        inject_injector_contract: meta?.inject_injector_contract ?? undefined,
        inject_injector: meta?.inject_injector ?? undefined,
        inject_assets: data.inject_assets,
        inject_tags: [],
        inject_content: {
          expectations: [],
          obfuscator: 'plain-text',
        },
        inject_all_teams: false,
        inject_teams: [],
        inject_asset_groups: [],
        inject_documents: [],
        inject_depends_duration: 0,
        inject_depends_on: [],
      },
    }).then(() => {
      setActionMetas(prev => ({
        ...prev,
        [selectedNodeId]: {
          ...prev[selectedNodeId],
          inject_title: data.inject_title,
          inject_description: data.inject_description,
          inject_assets: data.inject_assets,
          step_conditions: data.step_conditions,
        },
      }));
      setNodes(nds => nds.map(n => (n.id === selectedNodeId
        ? {
            ...n,
            data: {
              ...n.data,
              label: data.inject_title,
            },
          }
        : n)));
    });
    setDrawerOpen(false);
    setSelectedNodeId(null);
  };

  // -- UPDATE EVENT --
  const handleUpdateEvent = (data: UpdateEventData) => {
    if (!workflowId || !selectedNodeId) return;
    updateCondition(selectedNodeId, {
      event_name: data.event_name,
      event_description: data.event_description || undefined,
      event_workflow_id: workflowId,
      event_conditions: buildEventConditions(data.root_logical_type, data.conditions),
    }).then(() => {
      setEventMetas(prev => ({
        ...prev,
        [selectedNodeId]: {
          event_name: data.event_name,
          event_description: data.event_description,
          root_logical_type: data.root_logical_type,
          conditions: data.conditions,
        },
      }));
      setNodes(nds => nds.map(n => (n.id === selectedNodeId
        ? {
            ...n,
            data: {
              ...n.data,
              label: data.event_name,
            },
          }
        : n)));
    });
    setDrawerOpen(false);
    setSelectedNodeId(null);
  };

  // -- DELETE --
  const deleteNode = useCallback((nodeId: string) => {
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;

    const remove = node.type === 'action' ? deleteStep(nodeId) : deleteCondition(nodeId);
    remove.then(() => {
      setNodes(nds => nds.filter(n => n.id !== nodeId));
      setEdges(eds => eds.filter(e => e.source !== nodeId && e.target !== nodeId));
      if (node.type === 'event') {
        setEventMetas((prev) => {
          const next = { ...prev };
          delete next[nodeId];
          return next;
        });
        // Remove this event from all action metas' step_condition_ids
        setActionMetas(prev => Object.fromEntries(
          Object.entries(prev).map(([sid, meta]) => [sid, {
            ...meta,
            step_condition_ids: meta.step_condition_ids.filter(cid => cid !== nodeId),
          }]),
        ));
      } else {
        setActionMetas((prev) => {
          const next = { ...prev };
          delete next[nodeId];
          return next;
        });
      }
    });
  }, [nodes, setNodes, setEdges]);

  const nodesWithCallbacks = useMemo(
    () => nodes.map(node => ({
      ...node,
      data: {
        ...node.data,
        onEdit: editNode,
        onDelete: deleteNode,
      },
    })),
    [nodes, editNode, deleteNode],
  );

  const edgesWithCallbacks = useMemo(
    () => edges.map(edge => ({
      ...edge,
      data: {
        ...edge.data,
        onDelete: onDeleteEdgeClick,
      },
    })),
    [edges, onDeleteEdgeClick],
  );

  const drawerTitle = () => {
    switch (drawerView) {
      case 'action':
        return t('Create an action');
      case 'event':
        return t('Create an event');
      case 'editAction':
        return t('Update action');
      case 'editEvent':
        return t('Update event');
      default:
        return t('Add component');
    }
  };

  const choiceButtonSx = {
    'display': 'flex',
    'alignItems': 'flex-start',
    'justifyContent': 'flex-start',
    'gap': 2,
    'width': '100%',
    'padding': 2,
    'borderRadius': 1,
    'border': `1px solid ${theme.palette.divider}`,
    'textAlign': 'left' as const,
    '&:hover': { backgroundColor: theme.palette.action.hover },
  };

  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };

  return (
    <div style={{
      width: '100%',
      height: 'calc(100vh - 230px)',
      position: 'relative',
    }}
    >
      <ReactFlow
        nodes={nodesWithCallbacks}
        edges={edgesWithCallbacks}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onEdgesDelete={onEdgesDelete}
        onConnect={onConnect}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        proOptions={proOptions}
        fitView
        style={{ background: 'transparent' }}
        defaultEdgeOptions={{
          type: 'deletable',
          markerEnd: { type: MarkerType.ArrowClosed },
          data: { onDelete: onDeleteEdgeClick },
        }}
      />
      {nodes.length === 0
        ? (
            <Button
              variant="contained"
              color="primary"
              size="large"
              startIcon={<Add />}
              onClick={handleOpenDrawer}
              sx={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                zIndex: 5,
              }}
            >
              {t('Add component')}
            </Button>
          )
        : (
            <Button
              variant="contained"
              color="primary"
              startIcon={<Add />}
              onClick={handleOpenDrawer}
              sx={{
                position: 'absolute',
                top: 10,
                right: 10,
                zIndex: 5,
              }}
            >
              {t('Add component')}
            </Button>
          )}
      <Drawer
        open={drawerOpen}
        handleClose={handleCloseDrawer}
        title={drawerTitle()}
      >
        <>
          {drawerView === 'choose' && (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
            >
              <ButtonBase
                sx={choiceButtonSx}
                onClick={() => setDrawerView('action')}
              >
                <TerminalOutlined sx={{
                  color: theme.palette.primary.main,
                  fontSize: 32,
                  mt: 0.5,
                }}
                />
                <div>
                  <Typography variant="subtitle1" fontWeight={600}>
                    {t('Action')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('Execute an injector contract with configured parameters')}
                  </Typography>
                </div>
              </ButtonBase>
              <ButtonBase
                sx={choiceButtonSx}
                onClick={() => setDrawerView('event')}
              >
                <BoltOutlined sx={{
                  color: theme.palette.warning.main,
                  fontSize: 32,
                  mt: 0.5,
                }}
                />
                <div>
                  <Typography variant="subtitle1" fontWeight={600}>
                    {t('Event')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('Define conditions to trigger the next actions')}
                  </Typography>
                </div>
              </ButtonBase>
            </div>
          )}
          {drawerView === 'action' && (
            <CreateActionForm
              onSubmit={submitAction}
              handleClose={handleCloseDrawer}
            />
          )}
          {drawerView === 'event' && (
            <CreateEventForm
              onSubmit={submitEvent}
              handleClose={handleCloseDrawer}
            />
          )}
          {drawerView === 'editAction' && selectedNodeId && (
            <UpdateActionForm
              initialTitle={actionMetas[selectedNodeId]?.inject_title ?? ''}
              initialDescription={actionMetas[selectedNodeId]?.inject_description ?? ''}
              initialAssets={actionMetas[selectedNodeId]?.inject_asset_objects ?? []}
              initialMapperConditions={actionMetas[selectedNodeId]?.step_conditions ?? []}
              contractFields={actionMetas[selectedNodeId]?.contract_fields ?? []}
              onSubmit={handleUpdateAction}
              handleClose={handleCloseDrawer}
            />
          )}
          {drawerView === 'editEvent' && selectedNodeId && (
            <UpdateEventForm
              initialName={eventMetas[selectedNodeId]?.event_name ?? ''}
              initialDescription={eventMetas[selectedNodeId]?.event_description ?? ''}
              initialRootLogicalType={eventMetas[selectedNodeId]?.root_logical_type ?? 'AND'}
              initialConditions={eventMetas[selectedNodeId]?.conditions ?? [{
                condition_type: 'EQ',
                condition_key_type: 'status',
                condition_value: 'SUCCESS',
              }]}
              onSubmit={handleUpdateEvent}
              handleClose={handleCloseDrawer}
            />
          )}
        </>
      </Drawer>
    </div>
  );
};

export default Logic;
