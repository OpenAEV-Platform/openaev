import { alpha, useTheme } from '@mui/material/styles';
import { BaseEdge, EdgeLabelRenderer, type EdgeProps, getBezierPath } from '@xyflow/react';

import { attackPathCausalColor } from '../attack-path-colors';
import { type AttackPathFlowEdge } from '../attack-path-flow-helpers';

// A causal kill-chain edge (issue 6647), drawn from a producing finding node to the execution/injector
// node that consumes it. Two visual variants, driven by data.causalKind:
//   - 'finding' => SOLID line: a produced finding value feeds the consuming execution (a real match).
//   - 'depend'  => DASHED line: pure dependsOn sequencing, with no finding matched.
// This edge is additive; it only appears when kill-chain meta is available, and never alters the
// existing status-coloured graph edges.
const CausalEdge = ({
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
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const causalKind = data?.causalKind;
  const label = data?.label;
  const dimmed = data?.dimmed ?? false;
  // A matched finding chain reads as a real data-flow (solid); a bare dependsOn is speculative
  // sequencing (dashed). Colour with the dedicated causal magenta so it never reads as a
  // prevention/detection verdict (those are green/orange/red on the status edges).
  const isFinding = causalKind === 'finding';
  let color = attackPathCausalColor(theme);
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
          strokeDasharray: isFinding ? undefined : '6 4',
          opacity,
        }}
      />
      {!dimmed && label && (
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
            title={label}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default CausalEdge;
