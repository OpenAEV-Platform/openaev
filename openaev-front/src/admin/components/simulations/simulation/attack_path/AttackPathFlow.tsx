import { useTheme } from '@mui/material/styles';
import {
  Background,
  Controls,
  type Edge,
  MiniMap,
  type Node,
  ReactFlow,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';
import { useEffect } from 'react';

import { AP_FLOW_NODE_TYPE, type AttackPathFlowEdge, type AttackPathFlowNode, type AttackPathFlowNodeData } from './attack-path-flow-helpers';
import edgeTypes from './edges';
import nodeTypes from './nodes';

const proOptions = {
  account: 'paid-pro',
  hideAttribution: true,
};

// A request to center the map on a node; the nonce lets the same node be re-focused on a repeat click.
export interface AttackPathFocusRequest {
  nodeId: string;
  nonce: number;
}

interface AttackPathFlowProps {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
  onEndpointClick?: (nodeId: string, ref?: string, label?: string) => void;
  onClusterClick?: (injectorId: string, kind: 'header' | 'overflow') => void;
  onFindingClusterClick?: (typeFindings: string, injectorId: string, kind: 'header' | 'overflow') => void;
  onFindingSelect?: (nodeId: string) => void;
  focusRequest?: AttackPathFocusRequest | null;
}

// Thin ReactFlow wrapper: it renders the nodes/edges the helper produced and reports node clicks up
// to the page (endpoint drill-down, cluster expand, finding-type focus, finding path highlight). A
// finding-item click in the drawer can also request a cross-focus onto an endpoint node.
const AttackPathFlow = ({ nodes, edges, onEndpointClick, onClusterClick, onFindingClusterClick, onFindingSelect, focusRequest }: AttackPathFlowProps) => {
  const theme = useTheme();
  const reactFlow = useReactFlow();
  const [flowNodes, setFlowNodes, onNodesChange] = useNodesState<Node>(nodes);
  const [flowEdges, setFlowEdges, onEdgesChange] = useEdgesState<Edge>(edges);

  // The page rebuilds nodes/edges when the graph or the expansion changes; mirror that here.
  useEffect(() => {
    setFlowNodes(nodes);
  }, [nodes, setFlowNodes]);
  useEffect(() => {
    setFlowEdges(edges);
  }, [edges, setFlowEdges]);

  // Center and zoom onto the requested node (cross-focus): clicking a finding item brings its
  // endpoint into view. fitView on a single node centers it; the nonce re-fires on a repeat click.
  useEffect(() => {
    if (focusRequest?.nodeId) {
      reactFlow.fitView({
        nodes: [{ id: focusRequest.nodeId }],
        duration: 600,
        maxZoom: 1.5,
      });
    }
  }, [focusRequest, reactFlow]);

  return (
    <ReactFlow
      nodes={flowNodes}
      edges={flowEdges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={(_, node) => {
        const data = node.data as AttackPathFlowNodeData;
        if (node.type === AP_FLOW_NODE_TYPE.asset) {
          onEndpointClick?.(node.id, data.ref, data.label);
        } else if (node.type === AP_FLOW_NODE_TYPE.endpointCluster && data.injectorId) {
          onClusterClick?.(data.injectorId, data.clusterKind === 'overflow' ? 'overflow' : 'header');
        } else if (node.type === AP_FLOW_NODE_TYPE.findingCluster && data.typeFindings && data.injectorId) {
          onFindingClusterClick?.(data.typeFindings, data.injectorId, data.clusterKind === 'overflow' ? 'overflow' : 'header');
        } else if (node.type === AP_FLOW_NODE_TYPE.finding) {
          onFindingSelect?.(node.id);
        }
      }}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      proOptions={proOptions}
      fitView
      minZoom={0.05}
      // Follow the app theme so the canvas is not the default white; dark theme gives the dark
      // navy background the graph is designed for.
      colorMode={theme.palette.mode}
      // Cull off-screen nodes: a large collapsed graph is hundreds of endpoints, so only paint the
      // ones in the viewport to keep pan/zoom responsive.
      onlyRenderVisibleElements
      style={{ background: 'transparent' }}
      defaultEdgeOptions={{ type: 'apGrouped' }}
    >
      <Background bgColor={theme.palette.background.default} color={theme.palette.divider} gap={24} />
      <Controls position="top-left" />
      <MiniMap
        position="bottom-right"
        pannable
        zoomable
        style={{
          background: theme.palette.background.paper,
          border: `1px solid ${theme.palette.divider}`,
        }}
        maskColor={`${theme.palette.background.default}80`}
        nodeColor={theme.palette.primary.main}
      />
    </ReactFlow>
  );
};

export default AttackPathFlow;
