import { CropFree, UnfoldLess, UnfoldMore } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  type Connection,
  ConnectionLineType,
  type ConnectionState,
  ControlButton,
  Controls,
  type Edge,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Viewport,
  type XYPosition,
} from '@xyflow/react';
import moment from 'moment-timezone';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

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
import ChainingUtils from './common/chaining/ChainingUtils';
import CustomTimelineBackground from './CustomTimelineBackground';
import CustomTimelinePanel from './CustomTimelinePanel';
import { useFormatter } from './i18n';
import nodeTypes from './nodes';
import { type NodeInject } from './nodes/NodeInject';
import NodePhantom from './nodes/NodePhantom';

const useStyles = makeStyles()(() => ({
  container: {
    marginTop: 30,
    paddingRight: 40,
  },
  rotatedIcon: { transform: 'rotate(90deg)' },
  newBox: {
    position: 'relative',
    zIndex: 4,
    pointerEvents: 'none',
    cursor: 'none',
  },
}));

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

const ChainedTimelineFlow: FunctionComponent<Props> = ({
  injects,
  onSelectedInject,
  onTimelineClick,
  onUpdateInject,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);
  const [nodes, setNodes, onNodesChange] = useNodesState<NodeInject>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [draggingOnGoing, setDraggingOnGoing] = useState<boolean>(false);
  const [viewportData, setViewportData] = useState<Viewport>();
  const [minutesPerGapIndex, setMinutesPerGapIndex] = useState<number>(0);
  const [currentUpdatedNode, setCurrentUpdatedNode] = useState<NodeInject | null>(null);
  const [currentMousePosition, setCurrentMousePosition] = useState<XYPosition>({
    x: 0,
    y: 0,
  });
  const [newNodeCursorVisibility, setNewNodeCursorVisibility] = useState<'visible' | 'hidden'>('hidden');
  const [newNodeCursorClickable, setNewNodeCursorClickable] = useState<boolean>(true);
  const [currentMouseTime, setCurrentMouseTime] = useState<string>('');
  const [connectOnGoing, setConnectOnGoing] = useState<boolean>(false);

  const reactFlow = useReactFlow();

  const { contextId } = useContext(InjectTestContext);

  const { injectsMap, teams, assets, assetGroups, scenario, exercise }
    = useHelper((helper: ExercisesHelper & InjectHelper & TeamsHelper & EndpointHelper & AssetGroupsHelper & ScenariosHelper) => ({
      injectsMap: helper.getInjectsMap(),
      teams: helper.getTeamsMap(),
      assets: helper.getEndpointsMap(),
      assetGroups: helper.getAssetGroupMaps(),
      scenario: helper.getScenario(contextId),
      exercise: helper.getExercise(contextId),
    }));

  const { t } = useFormatter();

  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };
  const defaultEdgeOptions = {
    type: ConnectionLineType.Bezier,
    markerEnd: {
      type: MarkerType.ArrowClosed,
      width: 30,
      height: 30,
    },
  };

  const minutesPerGapAllowed = [
    5, // 5 minutes per gap
    20, // 20 so that each vertical lines indicates 1h
    20 * 12, // 20*12 so that each vertical line indicates half a day
    20 * 24, // 20*24 so that each vertical line indicates a full day
  ];
  const gapSize = 125;
  const newNodeSize = 50;
  const nodeHeightClearance = 220;
  const nodeWidthClearance = 350;

  let startDate: string | undefined;

  // If we have a scenario, we find the startdate using the cron info
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
    // Otherwise, we're in a simulation and we use the start_date
    startDate = exercise.exercise_start_date !== null ? exercise.exercise_start_date : undefined;
  }

  /**
   * Convert reactflow coordinates to time
   * @param position the reactflow coordinates
   */
  const convertCoordinatesToTime = (position: XYPosition) => {
    return Math.round(((position.x) / (gapSize / minutesPerGapAllowed[minutesPerGapIndex])) * 60);
  };

  /**
   * Move item from an index to another one
   * @param array the array to update
   * @param to the target index
   * @param from the origin index
   */
  const moveItem = (array: NodeInject[], to: number, from: number) => {
    const item = array[from];
    array.splice(from, 1);
    array.splice(to, 0, item);
    return array;
  };

  /**
   * Calculate a bounding box for an index
   * @param currentNode the node to calculate the bounding box for
   * @param nodesAvailable the nodes
   */
  const calculateBoundingBox = (currentNode: NodeInject, nodesAvailable: NodeInject[]) => {
    if (currentNode.data.inject?.inject_depends_on) {
      const nodesId = currentNode.data.inject?.inject_depends_on.map(value => value.dependency_relationship?.inject_parent_id);
      const dependencies = nodesAvailable.filter(dependencyNode => nodesId.includes(dependencyNode.id));
      const minX = Math.min(currentNode.position.x, ...dependencies.map(value => value.data.boundingBox!.topLeft.x));
      const minY = Math.min(currentNode.position.y, ...dependencies.map(value => value.data.boundingBox!.topLeft.y));
      const maxX = Math.max(currentNode.position.x + nodeWidthClearance, ...dependencies.map(value => value.data.boundingBox!.bottomRight.x));
      const maxY = Math.max(currentNode.position.y + nodeHeightClearance, ...dependencies.map(value => value.data.boundingBox!.bottomRight.y));
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
        x: currentNode.position.x + nodeWidthClearance,
        y: currentNode.position.y + nodeHeightClearance,
      },
    };
  };

  /**
   * Calculate injects position when dragging stopped
   * @param nodeInjects the list of injects
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
      for (let row = 1; row <= (maxY / nodeHeightClearance) + 1; row += 1) {
        if (!arrayOfY.includes(row * nodeHeightClearance)) {
          nodeInjectPosition.y = (row - 1) * nodeHeightClearance;
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
        const results = [];
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
                labelShowBg: false,
                labelStyle: {
                  fill: theme.palette.text?.primary,
                  fontSize: 14,
                },
              });
            }
          }
        }
        return results;
      });

    setEdges(newEdges);
  };

  /**
   * Update all nodes
   */
  const updateNodes = () => {
    if (injects.length > 0) {
      const injectsNodes = injects
        .sort((a, b) => a.inject_depends_duration - b.inject_depends_duration)
        .map((inject: InjectOutputType) => ({
          id: `${inject.inject_id}`,
          type: 'inject',
          data: {
            key: inject.inject_id,
            label: inject.inject_title,
            color: 'green',
            background:
                theme.palette.mode === 'dark'
                  ? '#09101e'
                  : '#e5e5e5',
            isTargeted: injects.find(anyInject => anyInject.inject_id === inject.inject_id) !== undefined,
            isTargeting: inject.inject_depends_on !== undefined,
            inject,
            fixedY: 0,
            startDate,
            onSelectedInject,
            boundingBox: {
              topLeft: {
                x: (inject.inject_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]),
                y: 0,
              },
              bottomRight: {
                x: (inject.inject_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]) + nodeWidthClearance,
                y: nodeHeightClearance,
              },
            },
            targets: inject.inject_assets!.map(asset => assets[asset]?.asset_name)
              .concat(inject.inject_asset_groups!.map(assetGroup => assetGroups[assetGroup]?.asset_group_name))
              .concat(inject.inject_teams!.map(team => teams[team]?.team_name)),
            contextId,
            onCreate,
            onUpdate,
            onDelete,
          },
          position: {
            x: (inject.inject_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]),
            y: 0,
          },
        }));

      if (currentUpdatedNode !== null) {
        injectsNodes.find(inject => inject.id === currentUpdatedNode.id)!.position.x = currentUpdatedNode.position.x;
      }

      setCurrentUpdatedNode(null);
      setDraggingOnGoing(false);
      calculateInjectPosition(injectsNodes);
      setNodes(injectsNodes);
      updateEdges();
    }
  };

  useEffect(() => {
    updateNodes();
  }, [injects, minutesPerGapIndex]);

  /**
   * Actions to hide the new node 'button'
   */
  const hideNewNode = () => {
    if (!connectOnGoing) {
      setNewNodeCursorVisibility('hidden');
      setNewNodeCursorClickable(false);
    }
  };

  /**
   * Actions to show the new node 'button'
   */
  const showNewNode = () => {
    if (!connectOnGoing) {
      setNewNodeCursorVisibility('visible');
      setNewNodeCursorClickable(true);
    }
  };

  /**
   * Take care of updates when the node drag is starting
   * @param _event the mouse event (unused for now)
   * @param node the node to update
   */
  const nodeDragStop = (_event: ReactMouseEvent, node: NodeInject) => {
    const injectFromMap = injectsMap[node.id];
    if (injectFromMap !== undefined) {
      const inject = {
        ...injectFromMap,
        inject_injector_contract: injectFromMap.inject_injector_contract.injector_contract_id,
        inject_id: node.id,
        inject_depends_duration: convertCoordinatesToTime(node.position),
        inject_depends_on: injectFromMap.inject_depends_on !== null
          ? injectFromMap.inject_depends_on
          : null,
      };
      onUpdateInject([inject]);
      setCurrentUpdatedNode(node);
      setDraggingOnGoing(false);
    }
  };

  /**
   * Small function to do some stuff when dragging is starting
   */
  const nodeDragStart = () => {
    const nodesList = nodes.filter(currentNode => currentNode.type !== 'phantom');
    setNodes(nodesList);
  };

  /**
   * Small function to do some stuff when connect is starting
   */
  const connectStart = () => {
    setConnectOnGoing(true);
    hideNewNode();
  };

  /**
   * Small function to do some stuff when connect ends
   */
  const connectEnd = () => {
    setTimeout(() => {
      setConnectOnGoing(false);
      showNewNode();
    }, 100);
  };

  const connect = (connection: Connection) => {
    const inject = injects.find(currentInject => currentInject.inject_id === connection.target);
    const injectParent = injects.find(currentInject => currentInject.inject_id === connection.source);
    if (inject !== undefined && injectParent !== undefined && inject.inject_depends_duration > injectParent.inject_depends_duration) {
      const newDependsOn: InjectDependency = {
        dependency_relationship: {
          inject_children_id: inject.inject_id,
          inject_parent_id: injectParent.inject_id,
        },
        dependency_condition:
          {
            mode: 'and',
            conditions: [
              {
                key: 'Execution',
                operator: 'eq',
                value: true,
              },
            ],
          },
      };

      const injectToUpdate = {
        ...injectsMap[inject.inject_id],
        inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
        inject_id: inject.inject_id,
        inject_depends_on: [newDependsOn],
      };
      onUpdateInject([injectToUpdate]);
    }
  };

  /**
   * Actions to do during node drag, especially keeping it horizontal
   * @param _event the mouse event
   * @param node the node that is being dragged
   */
  const nodeDrag = (_event: ReactMouseEvent, node: NodeInject) => {
    setDraggingOnGoing(true);
    const { position } = node;
    const { data } = node;
    const dependsOn = nodes.find(currentNode => (data.inject?.inject_depends_on !== null
      && data.inject?.inject_depends_on!.find(value => value.dependency_relationship?.inject_parent_id === currentNode.id)));
    const dependsTo = nodes
      .filter(currentNode => (currentNode.data.inject?.inject_depends_on !== undefined
        && currentNode.data.inject?.inject_depends_on !== null
        && currentNode.data.inject?.inject_depends_on.find(value => value.dependency_relationship?.inject_parent_id === node.id) !== undefined))
      .sort((a, b) => a.data.inject!.inject_depends_duration - b.data.inject!.inject_depends_duration)[0];
    const aSecond = gapSize / (minutesPerGapAllowed[minutesPerGapIndex] * 60);
    if (dependsOn?.position && position.x <= dependsOn?.position.x) {
      position.x = dependsOn.position.x + aSecond;
    }

    if (dependsTo?.position && position.x >= dependsTo?.position.x) {
      position.x = dependsTo.position.x - aSecond;
    }

    if (node.data.fixedY !== undefined) {
      position.y = node.data.fixedY;
      if (data.inject) data.inject.inject_depends_duration = convertCoordinatesToTime(node.position);
    }
  };

  /**
   * Actions when clicking the new node 'button'
   * @param event
   */
  const onNewNodeClick = (event: ReactMouseEvent) => {
    if (newNodeCursorClickable) {
      const position = reactFlow.screenToFlowPosition({
        x: event.clientX - (newNodeSize / 2),
        y: event.clientY,
      });

      const totalSeconds = (position.x / gapSize) * minutesPerGapAllowed[minutesPerGapIndex] * 60;
      onTimelineClick(totalSeconds);
    }
  };

  /**
   * Actions to do when the mouse move
   * @param eventMove the mouse event
   */
  const onMouseMove = (eventMove: ReactMouseEvent) => {
    if (!draggingOnGoing) {
      const position = reactFlow.screenToFlowPosition({
        x: eventMove.clientX,
        y: eventMove.clientY,
      }, { snapToGrid: false });
      const sidePosition = reactFlow.screenToFlowPosition({
        x: eventMove.clientX - (newNodeSize / 2),
        y: eventMove.clientY,
      }, { snapToGrid: false });

      const viewPort = reactFlow.getViewport();
      setCurrentMousePosition({
        x: ((position.x * reactFlow.getZoom()) + viewPort.x - (newNodeSize / 2)),
        y: ((position.y * reactFlow.getZoom()) + viewPort.y - (newNodeSize / 2)),
      });

      if (startDate === undefined) {
        const momentOfTime = moment.utc(
          moment.duration(convertCoordinatesToTime(
            {
              x: sidePosition.x > 0 ? sidePosition.x : 0,
              y: sidePosition.y,
            },
          ), 's').asMilliseconds(),
        );

        setCurrentMouseTime(`${momentOfTime.dayOfYear() - 1} d, ${momentOfTime.hour()} h, ${momentOfTime.minute()} m`);
      } else {
        const momentOfTime = moment.utc(startDate)
          .add(-new Date().getTimezoneOffset() / 60, 'h')
          .add(convertCoordinatesToTime({
            x: sidePosition.x > 0 ? sidePosition.x : 0,
            y: sidePosition.y,
          }), 's');

        setCurrentMouseTime(momentOfTime.format('MMMM Do, YYYY - h:mmA'));
      }
    }
  };

  /**
   * Taking care of the panning of the timeline
   * @param _event the mouse event
   * @param viewport the updated viewport
   */
  const panTimeline = (_event: MouseEvent | TouchEvent | null, viewport: Viewport) => {
    setViewportData(viewport);
  };

  /**
   * Updating the time between each gap
   * @param incrementIndex increment or decrement the index to get the current minutesPerGap
   */
  const updateMinutesPerGap = (incrementIndex: number) => {
    const nodesList = nodes.filter(currentNode => currentNode.type !== 'phantom');
    setNodes(nodesList);
    setDraggingOnGoing(true);
    setMinutesPerGapIndex(minutesPerGapIndex + incrementIndex);
    setDraggingOnGoing(false);
  };

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
        const newDependsOn: InjectDependency = {
          dependency_relationship: {
            inject_children_id: injectToUpdate.inject_id,
            inject_parent_id: edge.source,
          },
          dependency_condition:
              {
                mode: 'and',
                conditions: [
                  {
                    key: 'Execution',
                    operator: 'eq',
                    value: true,
                  },
                ],
              },
        };
        const injectToUpdateEdge = {
          ...injectsMap[injectToUpdate.inject_id],
          inject_injector_contract: injectToUpdate.inject_injector_contract.injector_contract_id,
          inject_id: injectToUpdate.inject_id,
          inject_depends_on: [newDependsOn],
        };
        updates.push(injectToUpdateEdge);
        onUpdateInject(updates);
      }
    } else {
      const inject = injects.find(currentInject => currentInject.inject_id === edge.target);
      const parent = injects.find(currentInject => currentInject.inject_id === connectionState.toNode?.id);
      if (inject !== undefined && parent !== undefined && parent.inject_depends_duration < inject.inject_depends_duration) {
        const newDependsOn: InjectDependency = {
          dependency_relationship: {
            inject_children_id: inject.inject_id,
            inject_parent_id: connectionState.toNode?.id,
          },
          dependency_condition:
              {
                mode: 'and',
                conditions: [
                  {
                    key: 'Execution',
                    operator: 'eq',
                    value: true,
                  },
                ],
              },
        };
        const injectToUpdate = {
          ...injectsMap[inject.inject_id],
          inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
          inject_id: inject.inject_id,
          inject_depends_on: [newDependsOn],
        };
        onUpdateInject([injectToUpdate]);
      }
    }
    updateNodes();
  };
  return (
    <>
      {injects.length > 0 ? (
        <div
          className={`${classes.container} chainedTimeline`}
          style={{
            width: '100%',
            height: 'calc(100vh - 400px)',
          }}
        >
          <ReactFlow
            colorMode={theme.palette.mode}
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            nodesDraggable={permissions.canManage}
            nodesConnectable={permissions.canManage}
            nodesFocusable={false}
            elementsSelectable={permissions.canManage}
            onNodeDrag={nodeDrag}
            onNodeDragStop={nodeDragStop}
            onNodeDragStart={nodeDragStart}
            onNodeMouseEnter={hideNewNode}
            onNodeMouseLeave={showNewNode}
            onConnectStart={connectStart}
            onConnectEnd={connectEnd}
            onConnect={connect}
            onEdgeMouseEnter={hideNewNode}
            onEdgeMouseLeave={showNewNode}
            defaultEdgeOptions={defaultEdgeOptions}
            connectionLineType={ConnectionLineType.SmoothStep}
            onMouseMove={onMouseMove}
            onMove={panTimeline}
            proOptions={proOptions}
            translateExtent={[[-60, -50], [Infinity, Infinity]]}
            nodeExtent={[[0, 0], [Infinity, Infinity]]}
            defaultViewport={{
              x: 60,
              y: 50,
              zoom: 0.75,
            }}
            minZoom={0.3}
            onClick={onNewNodeClick}
            onMouseEnter={showNewNode}
            onMouseLeave={hideNewNode}
            onReconnect={() => {}}
            // @ts-expect-error for some reason, the signature here is not well defined
            onReconnectEnd={onReconnectEnd}
            edgesReconnectable={true}
          >
            <div
              id="newBox"
              className={!connectOnGoing ? classes.newBox : ''}
              style={{
                top: currentMousePosition.y,
                left: currentMousePosition.x,
                visibility: newNodeCursorVisibility,
              }}
            >
              <NodePhantom
                time={currentMouseTime}
                newNodeSize={newNodeSize}
              />
            </div>
            <div
              onMouseEnter={hideNewNode}
              onMouseLeave={showNewNode}
            >
              <Controls
                showFitView={false}
                showZoom={false}
                showInteractive={false}
                orientation="horizontal"
              >
                <Tooltip title={t('Fit view')}>
                  <div>
                    <ControlButton
                      onClick={() => reactFlow.fitView({ duration: 500 })}
                    >
                      <CropFree />
                    </ControlButton>
                  </div>
                </Tooltip>
                <Tooltip title={t('Increase time interval')}>
                  <div>
                    <ControlButton
                      disabled={minutesPerGapAllowed.length - 1 === minutesPerGapIndex}
                      onClick={() => updateMinutesPerGap(1)}
                    >
                      <UnfoldLess className={classes.rotatedIcon} />
                    </ControlButton>
                  </div>
                </Tooltip>
                <Tooltip title={t('Reduce time interval')}>
                  <div>
                    <ControlButton
                      disabled={minutesPerGapIndex === 0}
                      onClick={() => updateMinutesPerGap(-1)}
                    >
                      <UnfoldMore className={classes.rotatedIcon} />
                    </ControlButton>
                  </div>
                </Tooltip>
              </Controls>
            </div>
            <CustomTimelineBackground
              gap={gapSize}
              minutesPerGap={minutesPerGapAllowed[minutesPerGapIndex]}
            />
            <CustomTimelinePanel
              gap={gapSize}
              minutesPerGap={minutesPerGapAllowed[minutesPerGapIndex]}
              viewportData={viewportData}
              startDate={startDate}
            />

            <MiniMap
              pannable={true}
              onMouseEnter={hideNewNode}
              onMouseLeave={showNewNode}
              ariaLabel={null}
            />
          </ReactFlow>
        </div>
      ) : null}
    </>
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
