import { useTheme } from '@mui/material/styles';
import {
  BaseEdge,
  type Edge,
  type EdgeProps,
  getSmoothStepPath,
} from '@xyflow/react';

interface InformationalEdgeData {
  outputLabel?: string;

  [key: string]: unknown;
}

type InformationalEdgeType = Edge<InformationalEdgeData, 'informational'>;

/**
 * Dotted orange edge used to visualize the data flow between a provider
 * action and a selected event. It does NOT represent a real execution link and is
 * never persisted, it only helps the user understand which action outputs can feed
 * the event's conditions. Unlike {@link DeletableEdge}, it exposes no delete control.
 */
const InformationalEdge = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
}: EdgeProps<InformationalEdgeType>) => {
  const theme = useTheme();
  const [edgePath] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  const color = theme.palette.warning.main;

  return (
    <BaseEdge
      id={id}
      path={edgePath}
      markerEnd={markerEnd}
      style={{
        stroke: color,
        strokeWidth: 2,
        strokeDasharray: '6 4',
      }}
    />
  );
};

export default InformationalEdge;
