import { alpha, useTheme } from '@mui/material/styles';
import { BaseEdge, EdgeLabelRenderer, type EdgeProps, getBezierPath } from '@xyflow/react';

import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor, { attackPathStatusLabel } from '../attack-path-colors';
import { type AttackPathFlowEdge } from '../attack-path-flow-helpers';

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
  data,
  selected,
}: EdgeProps<AttackPathFlowEdge>) => {
  const theme = useTheme();
  const { t } = useFormatter();
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
  const dimmed = data?.dimmed ?? false;
  // Solid edges, coloured by status (green prevented / orange detected / red neither) and neutral
  // blue by default; a mixed aggregation resolves to orange upstream (see aggregateStatus).
  let color = attackPathStatusColor(theme, data?.status);
  if (selected) {
    color = theme.palette.primary.main;
  }
  let opacity = 0.85;
  if (selected) {
    opacity = 1;
  } else if (dimmed) {
    opacity = 0.1;
  }
  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: color,
          strokeWidth: selected ? 2.5 : 1.5,
          opacity,
        }}
      />
      {!dimmed && (count > 1 || label) && (
        <EdgeLabelRenderer>
          <div
            className="nodrag nopan"
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              background: theme.palette.background.paper,
              border: `1px solid ${alpha(color, 0.6)}`,
              borderRadius: theme.shape.borderRadius,
              padding: `${theme.spacing(0.5)} ${theme.spacing(0.75)}`,
              fontSize: theme.typography.caption.fontSize,
              color: theme.palette.text.secondary,
              whiteSpace: 'nowrap',
            }}
            title={`${label ?? `+${count}`} — ${t(attackPathStatusLabel(data?.status))}`}
          >
            {label ?? `+${count}`}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default GroupedEdge;
