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

import { AP_FLOW_NODE_TYPE, type AttackPathFlowEdge, type AttackPathFlowNode, type AttackPathFlowNodeData } from './attack-path-flow-helpers';
import edgeTypes from './edges';
import nodeTypes from './nodes';

const proOptions = {
  account: 'paid-pro',
  hideAttribution: true,
};

interface AttackPathFlowProps {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
  onEndpointClick?: (nodeId: string, ref?: string, label?: string) => void;
}

// Thin ReactFlow wrapper: it renders the nodes/edges the helper produced and reports endpoint
// clicks up to the page, which loads that endpoint's detail on demand (T12).
const AttackPathFlow = ({ nodes, edges, onEndpointClick }: AttackPathFlowProps) => {
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
      // Follow the app theme so the canvas is not the default white; dark theme gives the dark
      // navy background the graph is designed for.
      colorMode={theme.palette.mode}
      // Cull off-screen nodes: a large collapsed graph is hundreds of endpoints, so only paint the
      // ones in the viewport to keep pan/zoom responsive.
      onlyRenderVisibleElements
      style={{ background: 'transparent' }}
      defaultEdgeOptions={{
        type: 'apGrouped',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: theme.palette.grey[500],
        },
      }}
    >
      <Background bgColor={theme.palette.background.default} color={theme.palette.divider} gap={24} />
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

export default AttackPathFlow;
