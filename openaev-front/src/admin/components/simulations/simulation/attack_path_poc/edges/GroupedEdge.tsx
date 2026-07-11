import { alpha, useTheme } from '@mui/material/styles';
import { BaseEdge, EdgeLabelRenderer, type EdgeProps, getBezierPath } from '@xyflow/react';

import attackPathStatusColor from '../attack-path-poc-colors';
import { type AttackPathFlowEdge } from '../attack-path-poc-flow-helpers';

// One edge stands for however many executions ran the same source -> target hop. It is coloured by
// status (the target endpoint's prevention status for execution edges, red for finding edges); a
// grouped edge shows a "+N" count badge, and finding edges carry the finding type as a label.
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
  const label = data?.label;
  let color = theme.palette.grey[500];
  if (selected) {
    color = theme.palette.primary.main;
  } else if (data?.status) {
    color = attackPathStatusColor(theme, data.status);
  }
  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: color,
          strokeWidth: selected ? 2.5 : 1.5,
          opacity: selected ? 1 : 0.75,
        }}
      />
      {(count > 1 || label) && (
        <EdgeLabelRenderer>
          <div
            className="nodrag nopan"
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              background: theme.palette.background.paper,
              border: `1px solid ${alpha(color, 0.6)}`,
              borderRadius: 10,
              padding: '0 6px',
              fontSize: 11,
              color: theme.palette.text.secondary,
              whiteSpace: 'nowrap',
            }}
          >
            {label ?? `+${count}`}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default GroupedEdge;
