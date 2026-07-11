import { useTheme } from '@mui/material/styles';
import {
  Background,
  Controls,
  type Edge,
  MarkerType,
  MiniMap,
  type Node,
  ReactFlow,
  useEdgesState,
  useNodesState,
} from '@xyflow/react';
import { useEffect } from 'react';

import { AP_FLOW_NODE_TYPE, type AttackPathFlowEdge, type AttackPathFlowNode, type AttackPathFlowNodeData } from './attack-path-poc-flow-helpers';
import edgeTypes from './edges';
import nodeTypes from './nodes';

const proOptions = {
  account: 'paid-pro',
  hideAttribution: true,
};

interface AttackPathPocFlowProps {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
  onEndpointClick?: (nodeId: string, ref?: string, label?: string) => void;
}

// Thin ReactFlow wrapper: it renders the nodes/edges the helper produced and reports endpoint
// clicks up to the page, which loads that endpoint's detail on demand (T12).
const AttackPathPocFlow = ({ nodes, edges, onEndpointClick }: AttackPathPocFlowProps) => {
  const theme = useTheme();
  const [flowNodes, setFlowNodes, onNodesChange] = useNodesState<Node>(nodes);
  const [flowEdges, setFlowEdges, onEdgesChange] = useEdgesState<Edge>(edges);

  // The page rebuilds nodes/edges when the graph or the expansion changes; mirror that here.
  useEffect(() => {
    setFlowNodes(nodes);
  }, [nodes, setFlowNodes]);
  useEffect(() => {
    setFlowEdges(edges);
  }, [edges, setFlowEdges]);

  return (
    <ReactFlow
      nodes={flowNodes}
      edges={flowEdges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={(_, node) => {
        if (node.type === AP_FLOW_NODE_TYPE.asset) {
          const data = node.data as AttackPathFlowNodeData;
          onEndpointClick?.(node.id, data.ref, data.label);
        }
      }}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      proOptions={proOptions}
      fitView
      minZoom={0.05}
      style={{ background: 'transparent' }}
      defaultEdgeOptions={{
        type: 'apGrouped',
        markerEnd: { type: MarkerType.ArrowClosed },
      }}
    >
      <Background color={theme.palette.divider} gap={24} />
      <Controls />
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

export default AttackPathPocFlow;
