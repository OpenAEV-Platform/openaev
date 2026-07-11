import { useTheme } from '@mui/material/styles';
import { BaseEdge, EdgeLabelRenderer, type EdgeProps, getBezierPath } from '@xyflow/react';

import { type AttackPathFlowEdge } from '../attack-path-poc-flow-helpers';

// One edge stands for however many executions ran the same source -> target hop; when it groups more
// than one, a "+N" badge shows the count, so the graph never draws one edge per execution.
const GroupedEdge = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  data,
  selected,
}: EdgeProps<AttackPathFlowEdge>) => {
  const theme = useTheme();
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const count = data?.count ?? 1;
  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: selected ? theme.palette.primary.main : theme.palette.divider,
          strokeWidth: selected ? 2 : 1,
        }}
      />
      {count > 1 && (
        <EdgeLabelRenderer>
          <div
            className="nodrag nopan"
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              background: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 10,
              padding: '0 6px',
              fontSize: 11,
              color: theme.palette.text.secondary,
            }}
          >
            {`+${count}`}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default GroupedEdge;
