import { useTheme } from '@mui/material/styles';
import {
  ConnectionLineType,
  type Edge, MarkerType, type Node, Position,
  ReactFlow, useEdgesState, useNodesState,
} from '@xyflow/react';
import * as qs from 'qs';
import {
  type MouseEvent as ReactMouseEvent,
  useEffect, useState,
} from 'react';
import { useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { buildFilter } from '../../../../../../../components/common/queryable/filter/FilterUtils';
import { initSorting } from '../../../../../../../components/common/queryable/Page';
import { useFormatter } from '../../../../../../../components/i18n';
import {
  type EsAttackPath,
  type KillChainPhaseObject,
  type SearchPaginationInput,
} from '../../../../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../../../../utils/kill_chain_phases/kill_chain_phases';
import ColoredPercentageRate from '../components/ColoredPercentageRate';
import AttackPatternNode from './AttackPatternNode';
import DraggableEdge from './DraggableEdge';

interface Props {
  data: EsAttackPath[];
  widgetId: string;
  simulationId: string;
  simulationStartDate?: Date | null;
  simulationEndDate?: Date | null;
}

const useStyles = makeStyles()(() => ({
  reactFlow: {
    '--xy-node-background-color': 'none',
    '.react-flow__handle-left': { visibility: 'hidden' },
    '.react-flow__handle-right': { visibility: 'hidden' },
    '& .kill-chain-node': {
      'border': 'none !important',
      'textAlign': 'left',
      '.react-flow__handle-top': { display: 'none !important' },
      '.react-flow__handle-bottom': { display: 'none !important' },
    },
  },
}));

const AttackPath = ({ data, widgetId, simulationId, simulationStartDate = null, simulationEndDate = null }: Props) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { du, fldt } = useFormatter();
  const [nodes, setNodes] = useNodesState<Node>([]);
  const [attackPathsEdges, setAttackPathsEdges] = useEdgesState<Edge>([]);
  const [simulationDatesEdge, setSimulationDatesEdge] = useEdgesState<Edge>([]);
  const [hoveredNodeId, setHoveredNodeId] = useState(null);
  const navigate = useNavigate();

  // -- React Flow configuration
  const arrowSize = 15;
  const XYinitalPadding = 50;
  const XGap = 200; // Horizontal gap between nodes
  const YGap = 150; // Vertical gap between nodes

  const nodeTypes = { attackPattern: AttackPatternNode };
  enum NodeType {
    DEFAULT = 'default',
    ATTACK_PATTERN = 'attackPattern',
  }

  const edgeTypes = { draggableEdge: DraggableEdge };
  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };

  // -- React Flow nodes and edges management
  const resolveDataByKillChainPhase = (esAttackPaths: EsAttackPath[]) => {
    const killChainMap = new Map();
    esAttackPaths.filter(item => item.killChainPhases && item.killChainPhases.length > 0)
      .forEach((attackPath) => {
        (attackPath.killChainPhases ?? []).forEach((phase) => {
          if (!killChainMap.has(phase.id)) {
            killChainMap.set(phase.id, {
              id: phase.id,
              name: phase.name,
              phase_order: phase.order,
              attackPaths: [],
            });
          }
          killChainMap.get(phase.id).attackPaths.push(attackPath);
        });
      });
    return Array.from(killChainMap.values()).toSorted(sortKillChainPhase);
  };

  const getNodeId = (attackPathID: string, killChainPhase: KillChainPhaseObject) => {
    return attackPathID + '-' + killChainPhase.id;
  };

  const getAllChildrenNodeId = (attackPath: EsAttackPath) => {
    const allChildrenNodeId: string[] = [];
    if (!attackPath.attackPatternChildrenIds || attackPath.attackPatternChildrenIds.length === 0) {
      return allChildrenNodeId;
    }
    attackPath.attackPatternChildrenIds.forEach((childId) => {
      const child = data.find(item => item.attackPatternId === childId);
      allChildrenNodeId.push(
        ...(child?.killChainPhases ?? []).map(k => getNodeId(childId, k)),
      );
    });
    return allChildrenNodeId;
  };

  const createNode = (id: string, type: NodeType, data: Node['data'], xIndex: number, yIndex: number | null, className = '') => {
    return {
      id,
      type,
      data,
      position: {
        x: XYinitalPadding + XGap * xIndex,
        y: yIndex === null ? 0 : XYinitalPadding + YGap * yIndex,
      },
      targetPosition: Position.Left,
      sourcePosition: Position.Right,
      draggable: false,
      className,
    };
  };

  const createKillChainNode = (phase: KillChainPhaseObject, xIndex: number) => {
    return createNode(
      phase.id,
      NodeType.DEFAULT,
      { label: phase.name },
      xIndex,
      null,
      'kill-chain-node',
    );
  };

  const createAttackPatternNode = (
    attackPath: EsAttackPath,
    phase: KillChainPhaseObject,
    xIndex: number,
    yIndex: number,
  ) => {
    return createNode(
      getNodeId(attackPath.attackPatternId, phase),
      NodeType.ATTACK_PATTERN,
      {
        attackPath,
        onHover: setHoveredNodeId,
        onLeave: () => setHoveredNodeId(null),
      },
      xIndex,
      yIndex,
    );
  };

  const createDefaultNode = (
    nodeId: string,
    label: string,
    xIndex = 0,
    yIndex = 0,
  ) => {
    return createNode(nodeId, NodeType.DEFAULT, { label }, xIndex, yIndex);
  };

  const createEdgesByAttackPath = (attackPath: EsAttackPath, phase: KillChainPhaseObject) => {
    const nodeId = getNodeId(attackPath.attackPatternId, phase);
    return getAllChildrenNodeId(attackPath)
      .filter(nodeChildId => nodeChildId !== nodeId && hoveredNodeId !== null && hoveredNodeId === nodeId)
      .map((nodeChildId) => {
        return {
          id: attackPath.attackPatternId + '-' + phase.id + '-' + nodeChildId + '-edge',
          source: nodeId,
          target: nodeChildId,
          type: 'draggableEdge',
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: arrowSize,
            height: arrowSize,
            color: 'red',
          },
          style: { stroke: 'red' },
        };
      });
  };

  const resolvededDataByKillChainPhase = resolveDataByKillChainPhase(data);
  const initializeNodesAndEdges = () => {
    const newAttackPathsEdges: Edge[] = [];
    let newSimulationDatesEdge: Edge = {} as Edge;
    const newNodes: Node[] = [];
    const maxXIndex = resolvededDataByKillChainPhase.length > 2 ? resolvededDataByKillChainPhase.length - 1 : 1;
    let maxYIndex = 0;

    resolvededDataByKillChainPhase.forEach((phase, phaseIndex) => {
      newNodes.push(createKillChainNode(phase, phaseIndex));

      (phase.attackPaths as EsAttackPath[]).forEach((attackPath, attackPathIndex) => {
        maxYIndex = maxYIndex < attackPathIndex ? attackPathIndex : maxYIndex;
        newNodes.push(createAttackPatternNode(attackPath, phase, phaseIndex, attackPathIndex));
        newAttackPathsEdges.push(...createEdgesByAttackPath(attackPath, phase));
      });
    });

    if (simulationStartDate) {
      newNodes.push(createDefaultNode('start-date-node', fldt(simulationStartDate), 0, maxYIndex + 1));
    }
    if (simulationEndDate) {
      newNodes.push(createDefaultNode('end-date-node', fldt(simulationEndDate), maxXIndex, maxYIndex + 1));
      const duration = (new Date(simulationEndDate).getTime() - new Date(simulationStartDate!).getTime());
      newSimulationDatesEdge = {
        id: 'time-edge',
        source: 'start-date-node',
        target: 'end-date-node',
        label: du(duration),
        labelShowBg: false,
        labelStyle: {
          fill: theme.palette.text?.primary,
          fontSize: 14,
        },
      };
    }
    setNodes(newNodes);
    setAttackPathsEdges(newAttackPathsEdges);
    setSimulationDatesEdge([newSimulationDatesEdge]);
  };

  useEffect(() => {
    initializeNodesAndEdges();
  }, []);

  useEffect(() => {
    if (attackPathsEdges.length < 1 && hoveredNodeId === null) {
      return;
    }
    const newAttackPathsEdges: Edge[] = [];
    resolvededDataByKillChainPhase.forEach((phase) => {
      (phase.attackPaths as EsAttackPath[]).forEach((attackPath) => {
        newAttackPathsEdges.push(...createEdgesByAttackPath(attackPath, phase));
      });
    });
    setAttackPathsEdges(newAttackPathsEdges);
  }, [hoveredNodeId]);

  // -- React Flow nodes actions
  const onNodeClick = (event: ReactMouseEvent, node: Node) => {
    event.stopPropagation();
    if (node.type == 'attackPattern') {
      const initSearchPaginationInput: SearchPaginationInput = {
        page: 0,
        size: 20,
        sorts: initSorting('inject_updated_at', 'DESC'),
        filterGroup: {
          mode: 'or',
          filters: [
            buildFilter('inject_attack_patterns', [(node.data as { attackPath: EsAttackPath }).attackPath.attackPatternId], 'contains'),
          ],
        },
      };
      const params = qs.stringify({
        ...initSearchPaginationInput,
        key: 'simulation-injects-results',
      }, { allowEmptyArrays: true });
      const encodedParams = btoa(params);
      navigate('/admin/simulations/' + simulationId + '?query=' + encodedParams);
    }
  };

  return (
    <>
      <ColoredPercentageRate style={{
        marginLeft: 'auto',
        paddingBottom: theme.spacing(1),
      }}
      />
      <ReactFlow
        className={classes.reactFlow}
        id={widgetId}
        key={widgetId}
        colorMode={theme.palette.mode}
        nodes={nodes}
        edges={[...attackPathsEdges, ...simulationDatesEdge]}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodeClick={onNodeClick}

        // Interaction settings
        nodesDraggable={false}
        nodesConnectable={false}
        nodesFocusable={false}
        edgesFocusable={true}
        elementsSelectable={true}

        connectionLineType={ConnectionLineType.SmoothStep}

        // Pan and zoom
        panOnDrag={true}
        panOnScroll={true}
        zoomOnScroll={false}
        preventScrolling={false}

        proOptions={proOptions}
      >
      </ReactFlow>
    </>
  );
};

export default AttackPath;
