import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
  type Connection,
  ConnectionLineType,
  type ConnectionState,
  type Edge,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';
import moment from 'moment-timezone';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useContext, useEffect, useLayoutEffect, useRef, useState } from 'react';

import { type AssetGroupsHelper } from '../actions/asset_groups/assetgroup-helper';
import { type EndpointHelper } from '../actions/assets/asset-helper';
import { type ExercisesHelper } from '../actions/exercises/exercise-helper';
import { type InjectOutputType, type InjectStore } from '../actions/injects/Inject';
import { type InjectHelper } from '../actions/injects/inject-helper';
import { type ScenariosHelper } from '../actions/scenarios/scenario-helper';
import { type TeamsHelper } from '../actions/teams/team-helper';
import { InjectTestContext, PermissionsContext } from '../admin/components/common/Context';
import { useHelper } from '../store';
import { type Inject, type InjectDependency } from '../utils/api-types';
import handle from '../utils/period/Period';
import { flowXToSeconds, formatRelativeTime, GAP_SIZE, NODE_HEIGHT_CLEARANCE, NODE_WIDTH_CLEARANCE, secondsToFlowX, TIME_SCALES } from './chained_timeline/chronoUtils';
import TimelineControls from './chained_timeline/TimelineControls';
import TimelineEmptyState from './chained_timeline/TimelineEmptyState';
import TimelineGhost from './chained_timeline/TimelineGhost';
import TimelineGrid from './chained_timeline/TimelineGrid';
import { type NodeInject } from './chained_timeline/TimelineInjectNode';
import timelineNodeTypes from './chained_timeline/timelineNodeTypes';
import TimelineNowMarker from './chained_timeline/TimelineNowMarker';
import TimelineRuler, { RULER_HEIGHT } from './chained_timeline/TimelineRuler';
import ChainingUtils from './common/chaining/ChainingUtils';
import { useFormatter } from './i18n';

const SCALE_STORAGE_KEY = 'chained-timeline:scale';

// The dependency created when connecting two injects: child fires when the
// parent execution succeeds (editable later from the inject update form).
const buildExecutionDependency = (parentId: string, childId: string): InjectDependency => ({
  dependency_relationship: {
    inject_children_id: childId,
    inject_parent_id: parentId,
  },
  dependency_condition: {
    mode: 'and',
    conditions: [
      {
        key: 'Execution',
        operator: 'eq',
        value: true,
      },
    ],
  },
});

interface Props {
  injects: InjectOutputType[];
  onSelectedInject(inject?: InjectOutputType): void;
  onTimelineClick(duration: number): void;
  onUpdateInject: (data: Inject[]) => void;
  onCreate: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onUpdate: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onDelete: (result: string) => void;
}

/**
 * The interactive (time-based) view of the injects tab: a horizontally
 * scrolling playground where each inject is a card positioned at its trigger
 * time. Dragging a card reschedules it, connecting two cards chains them on
 * an execution condition, and clicking empty canvas creates an inject at
 * that exact moment. The playground sizes itself to the remaining viewport
 * height so the page never scrolls.
 */
const ChainedTimelineFlow: FunctionComponent<Props> = ({
  injects,
  onSelectedInject,
  onTimelineClick,
  onUpdateInject,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  const theme = useTheme();
  const { fld, ft } = useFormatter();
  const { permissions } = useContext(PermissionsContext);
  const reactFlow = useReactFlow();
  const { contextId } = useContext(InjectTestContext);

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeInject>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [scaleIndex, setScaleIndex] = useState<number>(() => {
    const stored = Number(localStorage.getItem(SCALE_STORAGE_KEY));
    return Number.isInteger(stored) && stored >= 0 && stored < TIME_SCALES.length ? stored : 0;
  });
  const [draggingOngoing, setDraggingOngoing] = useState(false);
  const [connectOngoing, setConnectOngoing] = useState(false);
  const [currentUpdatedNode, setCurrentUpdatedNode] = useState<NodeInject | null>(null);
  const [ghost, setGhost] = useState<{
    visible: boolean;
    x: number;
    label: string;
  }>({
    visible: false,
    x: 0,
    label: '',
  });
  const [ghostClickable, setGhostClickable] = useState(false);

  const minutesPerGap = TIME_SCALES[scaleIndex].minutesPerGap;

  const { injectsMap, teams, assets, assetGroups, scenario, exercise }
    = useHelper((helper: ExercisesHelper & InjectHelper & TeamsHelper & EndpointHelper & AssetGroupsHelper & ScenariosHelper) => ({
      injectsMap: helper.getInjectsMap(),
      teams: helper.getTeamsMap(),
      assets: helper.getEndpointsMap(),
      assetGroups: helper.getAssetGroupMaps(),
      scenario: helper.getScenario(contextId),
      exercise: helper.getExercise(contextId),
    }));

  // Fit-to-viewport height: the playground fills the space between its top
  // edge and the bottom of the window, so the page itself never scrolls.
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState(480);
  useLayoutEffect(() => {
    const measure = () => {
      if (wrapperRef.current) {
        const top = wrapperRef.current.getBoundingClientRect().top;
        setHeight(Math.max(420, window.innerHeight - top - 16));
      }
    };
    measure();
    window.addEventListener('resize', measure);
    // The hero above loads asynchronously and pushes the tab content down;
    // re-measure on any layout change of the page body.
    const observer = new ResizeObserver(measure);
    observer.observe(document.body);
    return () => {
      window.removeEventListener('resize', measure);
      observer.disconnect();
    };
  }, []);

  // Timeline anchor: scenarios anchor on their recurrence (cron time of day),
  // simulations on their actual start date; otherwise times are relative.
  let startDate: string | undefined;
  if (scenario !== undefined) {
    const cronObject = handle(scenario.scenario_recurrence);
    startDate = scenario?.scenario_recurrence_start ? scenario?.scenario_recurrence_start : exercise?.exercise_start_date;
    if (startDate !== undefined) {
      startDate = cronObject !== null
        ? moment(startDate).utc().hour(cronObject.getRecurrenceTime().hour || 0).minute(cronObject.getRecurrenceTime().minute || 0)
            .format()
        : moment(startDate).utc().format();
    }
  } else if (exercise !== undefined) {
    startDate = exercise.exercise_start_date !== null ? exercise.exercise_start_date : undefined;
  }

  /** Move an item inside the array (row-packing helper). */
  const moveItem = (array: NodeInject[], to: number, from: number) => {
    const item = array[from];
    array.splice(from, 1);
    array.splice(to, 0, item);
    return array;
  };

  /** Space claimed by a node and its dependency parents (for row packing). */
  const calculateBoundingBox = (currentNode: NodeInject, nodesAvailable: NodeInject[]) => {
    if (currentNode.data.inject?.inject_depends_on) {
      const nodesId = currentNode.data.inject?.inject_depends_on.map(value => value.dependency_relationship?.inject_parent_id);
      const dependencies = nodesAvailable.filter(dependencyNode => nodesId.includes(dependencyNode.id));
      const minX = Math.min(currentNode.position.x, ...dependencies.map(value => value.data.boundingBox!.topLeft.x));
      const minY = Math.min(currentNode.position.y, ...dependencies.map(value => value.data.boundingBox!.topLeft.y));
      const maxX = Math.max(currentNode.position.x + NODE_WIDTH_CLEARANCE, ...dependencies.map(value => value.data.boundingBox!.bottomRight.x));
      const maxY = Math.max(currentNode.position.y + NODE_HEIGHT_CLEARANCE, ...dependencies.map(value => value.data.boundingBox!.bottomRight.y));
      return {
        topLeft: {
          x: minX,
          y: minY,
        },
        bottomRight: {
          x: maxX,
          y: maxY,
        },
      };
    }
    return {
      topLeft: currentNode.position,
      bottomRight: {
        x: currentNode.position.x + NODE_WIDTH_CLEARANCE,
        y: currentNode.position.y + NODE_HEIGHT_CLEARANCE,
      },
    };
  };

  /**
   * Row-packing auto layout: X is fully determined by the trigger time, Y is
   * assigned to the first free row so overlapping cards stack below each
   * other and children stay below their parents.
   */
  const calculateInjectPosition = (nodeInjects: NodeInject[]) => {
    let reorganizedInjects = nodeInjects;

    nodeInjects.forEach((node, i) => {
      let childNodes = reorganizedInjects.slice(i).filter(nextNode => nextNode.id !== node.id
        && nextNode.data.inject?.inject_depends_on !== undefined
        && nextNode.data.inject?.inject_depends_on !== null
        && nextNode.data.inject!.inject_depends_on
          .find(dependsOn => dependsOn.dependency_relationship?.inject_parent_id === node.id) !== undefined);

      childNodes = childNodes.sort((a, b) => a.data.inject!.inject_depends_duration - b.data.inject!.inject_depends_duration);

      childNodes.forEach((childNode, j) => {
        reorganizedInjects = moveItem(reorganizedInjects, i + j + 1, reorganizedInjects.indexOf(childNode, i));
      });
    });

    reorganizedInjects.forEach((nodeInject, index) => {
      const nodeInjectPosition = nodeInject.position;
      const nodeInjectData = nodeInject.data;

      const previousNodes = reorganizedInjects.slice(0, index)
        .filter(previousNode => previousNode.data.boundingBox !== undefined
          && nodeInjectData.boundingBox !== undefined
          && nodeInjectData.boundingBox?.topLeft.x >= previousNode.data.boundingBox.topLeft.x
          && nodeInjectData.boundingBox?.topLeft.x < previousNode.data.boundingBox.bottomRight.x);

      const arrayOfY = previousNodes
        .map(previousNode => (previousNode.data.boundingBox?.bottomRight.y ? previousNode.data.boundingBox?.bottomRight.y : 0));
      const maxY = Math.max(0, ...arrayOfY);

      nodeInjectPosition.y = 0;
      let rowFound = false;
      for (let row = 1; row <= (maxY / NODE_HEIGHT_CLEARANCE) + 1; row += 1) {
        if (!arrayOfY.includes(row * NODE_HEIGHT_CLEARANCE)) {
          nodeInjectPosition.y = (row - 1) * NODE_HEIGHT_CLEARANCE;
          rowFound = true;
          break;
        }
      }

      if (!rowFound) {
        nodeInjectPosition.y = previousNodes.length === 0 ? 0 : maxY;
      }
      if (nodeInject.data.inject?.inject_depends_on) {
        const nodesId = nodeInject.data.inject?.inject_depends_on.map(value => value.dependency_relationship?.inject_parent_id);
        const dependencies = reorganizedInjects.filter(dependencyNode => nodesId.includes(dependencyNode.id));
        const minY = dependencies.length > 0 ? Math.min(...dependencies.map(value => value.data.boundingBox!.topLeft.y)) : 0;

        nodeInjectPosition.y = nodeInjectPosition.y < minY ? minY : nodeInjectPosition.y;
      }

      nodeInjectData.fixedY = nodeInjectPosition.y;
      nodeInjectData.boundingBox = calculateBoundingBox(nodeInject, reorganizedInjects);
      reorganizedInjects[index] = nodeInject;
    });
  };

  const updateEdges = () => {
    const newEdges = injects.filter(inject => inject.inject_depends_on !== null && inject.inject_depends_on !== undefined)
      .flatMap((inject) => {
        const results: Edge[] = [];
        if (inject.inject_depends_on !== undefined) {
          for (let i = 0; i < inject.inject_depends_on.length; i += 1) {
            if (inject.inject_depends_on[i].dependency_relationship?.inject_children_id === inject.inject_id) {
              results.push({
                id: `${inject.inject_depends_on[i].dependency_relationship?.inject_parent_id}->${inject.inject_depends_on[i].dependency_relationship?.inject_children_id}`,
                target: `${inject.inject_depends_on[i].dependency_relationship?.inject_children_id}`,
                targetHandle: `target-${inject.inject_depends_on[i].dependency_relationship?.inject_children_id}`,
                source: `${inject.inject_depends_on[i].dependency_relationship?.inject_parent_id}`,
                sourceHandle: `source-${inject.inject_depends_on[i].dependency_relationship?.inject_parent_id}`,
                label: ChainingUtils.fromInjectDependencyToLabel(inject.inject_depends_on[i]),
                labelShowBg: true,
                labelBgPadding: [6, 3],
                labelBgBorderRadius: 4,
                labelBgStyle: {
                  fill: theme.palette.background.accent,
                  fillOpacity: 0.95,
                },
                labelStyle: {
                  fill: theme.palette.text?.primary,
                  fontSize: 11,
                },
              });
            }
          }
        }
        return results;
      });

    setEdges(newEdges);
  };

  const updateNodes = () => {
    if (injects.length > 0) {
      const injectsNodes = injects
        .sort((a, b) => a.inject_depends_duration - b.inject_depends_duration)
        .map((inject: InjectOutputType) => ({
          id: `${inject.inject_id}`,
          type: 'inject',
          data: {
            inject,
            fixedY: 0,
            startDate,
            canManage: permissions.canManage,
            onSelectedInject,
            boundingBox: {
              topLeft: {
                x: secondsToFlowX(inject.inject_depends_duration, minutesPerGap),
                y: 0,
              },
              bottomRight: {
                x: secondsToFlowX(inject.inject_depends_duration, minutesPerGap) + NODE_WIDTH_CLEARANCE,
                y: NODE_HEIGHT_CLEARANCE,
              },
            },
            targets: inject.inject_assets!.map(asset => assets[asset]?.asset_name)
              .concat(inject.inject_asset_groups!.map(assetGroup => assetGroups[assetGroup]?.asset_group_name))
              .concat(inject.inject_teams!.map(team => teams[team]?.team_name)),
            onCreate,
            onUpdate,
            onDelete,
          },
          position: {
            x: secondsToFlowX(inject.inject_depends_duration, minutesPerGap),
            y: 0,
          },
        } as NodeInject));

      if (currentUpdatedNode !== null) {
        const updated = injectsNodes.find(inject => inject.id === currentUpdatedNode.id);
        if (updated) {
          updated.position.x = currentUpdatedNode.position.x;
        }
      }

      setCurrentUpdatedNode(null);
      setDraggingOngoing(false);
      calculateInjectPosition(injectsNodes);
      setNodes(injectsNodes);
      updateEdges();
    } else {
      setNodes([]);
      setEdges([]);
    }
  };

  useEffect(() => {
    updateNodes();
  }, [injects, minutesPerGap, startDate]);

  const hideGhost = () => {
    if (!connectOngoing) {
      setGhost(previous => ({
        ...previous,
        visible: false,
      }));
      setGhostClickable(false);
    }
  };

  const showGhost = () => {
    if (!connectOngoing && permissions.canManage) {
      setGhostClickable(true);
    }
  };

  /** Persist the new trigger time when a card drag ends. */
  const nodeDragStop = (_event: MouseEvent | TouchEvent, node: NodeInject) => {
    const injectFromMap = injectsMap[node.id];
    if (injectFromMap !== undefined) {
      const inject = {
        ...injectFromMap,
        inject_injector_contract: injectFromMap.inject_injector_contract.injector_contract_id,
        inject_id: node.id,
        inject_depends_duration: flowXToSeconds(node.position.x, minutesPerGap),
        inject_depends_on: injectFromMap.inject_depends_on !== null
          ? injectFromMap.inject_depends_on
          : null,
      };
      onUpdateInject([inject]);
      setCurrentUpdatedNode(node);
      setDraggingOngoing(false);
    }
  };

  const connectStart = () => {
    setConnectOngoing(true);
    setGhost(previous => ({
      ...previous,
      visible: false,
    }));
    setGhostClickable(false);
  };

  const connectEnd = () => {
    setTimeout(() => {
      setConnectOngoing(false);
      showGhost();
    }, 100);
  };

  /** Chain two injects: the child must fire strictly after its parent. */
  const connect = (connection: Connection) => {
    const inject = injects.find(currentInject => currentInject.inject_id === connection.target);
    const injectParent = injects.find(currentInject => currentInject.inject_id === connection.source);
    if (inject !== undefined && injectParent !== undefined && inject.inject_depends_duration > injectParent.inject_depends_duration) {
      const injectToUpdate = {
        ...injectsMap[inject.inject_id],
        inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
        inject_id: inject.inject_id,
        inject_depends_on: [buildExecutionDependency(injectParent.inject_id, inject.inject_id)],
      };
      onUpdateInject([injectToUpdate]);
    }
  };

  /**
   * Horizontal-only drag, clamped between the parent (cannot fire before it)
   * and the earliest child (cannot fire after it). The card's time chip
   * updates live through the mutated depends_duration.
   */
  const nodeDrag = (_event: MouseEvent | TouchEvent, node: NodeInject) => {
    setDraggingOngoing(true);
    const { position, data } = node;
    const dependsOn = nodes.find(currentNode => (data.inject?.inject_depends_on !== null
      && data.inject?.inject_depends_on!.find(value => value.dependency_relationship?.inject_parent_id === currentNode.id)));
    const dependsTo = nodes
      .filter(currentNode => (currentNode.data.inject?.inject_depends_on !== undefined
        && currentNode.data.inject?.inject_depends_on !== null
        && currentNode.data.inject?.inject_depends_on.find(value => value.dependency_relationship?.inject_parent_id === node.id) !== undefined))
      .sort((a, b) => a.data.inject!.inject_depends_duration - b.data.inject!.inject_depends_duration)[0];
    const aSecond = GAP_SIZE / (minutesPerGap * 60);
    if (dependsOn?.position && position.x <= dependsOn?.position.x) {
      position.x = dependsOn.position.x + aSecond;
    }

    if (dependsTo?.position && position.x >= dependsTo?.position.x) {
      position.x = dependsTo.position.x - aSecond;
    }

    if (position.x < 0) {
      position.x = 0;
    }

    if (node.data.fixedY !== undefined) {
      position.y = node.data.fixedY;
      if (data.inject) data.inject.inject_depends_duration = flowXToSeconds(node.position.x, minutesPerGap);
    }
  };

  /** Click on empty canvas: create an inject at the clicked time. */
  const onPlaygroundClick = (event: ReactMouseEvent) => {
    if (ghostClickable && permissions.canManage) {
      const position = reactFlow.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });
      onTimelineClick(flowXToSeconds(position.x, minutesPerGap));
    }
  };

  /** Ghost guide following the cursor over empty canvas. */
  const onMouseMove = (eventMove: ReactMouseEvent) => {
    if (!draggingOngoing && !connectOngoing && permissions.canManage && wrapperRef.current) {
      const rect = wrapperRef.current.getBoundingClientRect();
      const flowPosition = reactFlow.screenToFlowPosition({
        x: eventMove.clientX,
        y: eventMove.clientY,
      }, { snapToGrid: false });
      const seconds = flowXToSeconds(Math.max(0, flowPosition.x), minutesPerGap);
      const label = startDate === undefined
        ? formatRelativeTime(seconds)
        : (() => {
            const date = moment.utc(startDate).add(seconds, 's').toDate();
            return `${fld(date)} - ${ft(date)}`;
          })();
      setGhost({
        visible: ghostClickable,
        x: eventMove.clientX - rect.left,
        label,
      });
    }
  };

  /**
   * Edge re-drag: dropped in the void removes the dependency; dropped on
   * another card moves the dependency (keeping child-after-parent ordering).
   */
  const onReconnectEnd = (event: ReactMouseEvent, edge: Edge, handleType: 'source' | 'target', connectionState: Omit<ConnectionState, 'inProgress'>) => {
    if (!connectionState.isValid) {
      const inject = injects.find(currentInject => currentInject.inject_id === edge.target);
      if (inject !== undefined) {
        const injectToUpdate = {
          ...injectsMap[inject.inject_id],
          inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
          inject_id: inject.inject_id,
          inject_depends_on: undefined,
        };
        onUpdateInject([injectToUpdate]);
      }
    } else if (handleType === 'source') {
      const updates = [];
      const injectToRemove = injects.find(currentInject => currentInject.inject_id === edge.target);
      const injectToUpdate = injects.find(currentInject => currentInject.inject_id === connectionState.toNode?.id);

      const parent = injects.find(currentInject => currentInject.inject_id === connectionState.fromNode?.id);

      if (parent !== undefined
        && injectToUpdate !== undefined
        && injectToRemove !== undefined
        && parent.inject_depends_duration < injectToUpdate.inject_depends_duration) {
        const injectToRemoveEdge = {
          ...injectsMap[injectToRemove.inject_id],
          inject_injector_contract: injectToRemove.inject_injector_contract.injector_contract_id,
          inject_id: injectToRemove.inject_id,
          inject_depends_on: undefined,
        };
        updates.push(injectToRemoveEdge);
        const injectToUpdateEdge = {
          ...injectsMap[injectToUpdate.inject_id],
          inject_injector_contract: injectToUpdate.inject_injector_contract.injector_contract_id,
          inject_id: injectToUpdate.inject_id,
          inject_depends_on: [buildExecutionDependency(edge.source, injectToUpdate.inject_id)],
        };
        updates.push(injectToUpdateEdge);
        onUpdateInject(updates);
      }
    } else {
      const inject = injects.find(currentInject => currentInject.inject_id === edge.target);
      const parent = injects.find(currentInject => currentInject.inject_id === connectionState.toNode?.id);
      if (inject !== undefined && parent !== undefined && parent.inject_depends_duration < inject.inject_depends_duration) {
        const injectToUpdate = {
          ...injectsMap[inject.inject_id],
          inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
          inject_id: inject.inject_id,
          inject_depends_on: [buildExecutionDependency(connectionState.toNode!.id, inject.inject_id)],
        };
        onUpdateInject([injectToUpdate]);
      }
    }
    updateNodes();
  };

  const onScaleChange = (index: number) => {
    setScaleIndex(index);
    localStorage.setItem(SCALE_STORAGE_KEY, String(index));
  };

  const defaultEdgeOptions = {
    type: ConnectionLineType.Bezier,
    style: {
      stroke: alpha(theme.palette.primary.main, 0.6),
      strokeWidth: 1.5,
    },
    markerEnd: {
      type: MarkerType.ArrowClosed,
      width: 18,
      height: 18,
      color: alpha(theme.palette.primary.main, 0.8),
    },
  };

  return (
    <Box
      ref={wrapperRef}
      className="chainedTimeline"
      sx={{
        position: 'relative',
        width: '100%',
        height,
        marginTop: 1,
        borderRadius: 1,
        border: `1px solid ${theme.palette.divider}`,
        overflow: 'hidden',
        backgroundColor: alpha(theme.palette.background.paper, 0.3),
      }}
    >
      {injects.length === 0 ? (
        <TimelineEmptyState canManage={permissions.canManage} onCreate={() => onTimelineClick(0)} />
      ) : (
        <>
          <ReactFlow
            colorMode={theme.palette.mode}
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={timelineNodeTypes}
            nodesDraggable={permissions.canManage}
            nodesConnectable={permissions.canManage}
            nodesFocusable={false}
            elementsSelectable={permissions.canManage}
            onNodeDrag={nodeDrag}
            onNodeDragStop={nodeDragStop}
            onNodeMouseEnter={hideGhost}
            onNodeMouseLeave={showGhost}
            onConnectStart={connectStart}
            onConnectEnd={connectEnd}
            onConnect={connect}
            onEdgeMouseEnter={hideGhost}
            onEdgeMouseLeave={showGhost}
            defaultEdgeOptions={defaultEdgeOptions}
            connectionLineType={ConnectionLineType.SmoothStep}
            onMouseMove={onMouseMove}
            proOptions={{
              account: 'paid-pro',
              hideAttribution: true,
            }}
            translateExtent={[[-60, -80], [Infinity, Infinity]]}
            nodeExtent={[[0, 0], [Infinity, Infinity]]}
            defaultViewport={{
              x: 60,
              y: RULER_HEIGHT + 40,
              zoom: 0.75,
            }}
            minZoom={0.3}
            onClick={onPlaygroundClick}
            onMouseEnter={showGhost}
            onMouseLeave={hideGhost}
            onReconnect={() => { }}
            // @ts-expect-error the xyflow signature is not well defined here
            onReconnectEnd={onReconnectEnd}
            edgesReconnectable={permissions.canManage}
            style={{ background: 'transparent' }}
          >
            <TimelineGrid />
            <TimelineControls
              scaleIndex={scaleIndex}
              onScaleChange={onScaleChange}
              onMouseEnter={hideGhost}
              onMouseLeave={showGhost}
            />
            <div onMouseEnter={hideGhost} onMouseLeave={showGhost}>
              <MiniMap
                position="bottom-right"
                pannable
                ariaLabel={null}
                style={{
                  width: 140,
                  height: 90,
                  background: theme.palette.background.paper,
                  border: `1px solid ${theme.palette.divider}`,
                }}
                maskColor={alpha(theme.palette.background.default, 0.5)}
                nodeColor={theme.palette.primary.main}
              />
            </div>
          </ReactFlow>
          <TimelineRuler minutesPerGap={minutesPerGap} startDate={startDate} />
          <TimelineNowMarker minutesPerGap={minutesPerGap} startDate={startDate} />
          <TimelineGhost visible={ghost.visible && !draggingOngoing && !connectOngoing} x={ghost.x} label={ghost.label} />
        </>
      )}
    </Box>
  );
};

const ChainedTimeline: FunctionComponent<Props> = (props) => {
  return (
    <ReactFlowProvider>
      <ChainedTimelineFlow {...props} />
    </ReactFlowProvider>
  );
};

export default ChainedTimeline;
