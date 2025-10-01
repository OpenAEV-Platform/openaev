import { BaseEdge, type EdgeProps, getBezierPath } from '@xyflow/react';

const DraggableEdge = ({
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style = {},
  id,
  markerEnd,
}: EdgeProps) => {
  if (sourceY > targetY - 10 && sourceY < targetY + 10 && sourceX > targetX) {
    const radiusX = (sourceX - targetX) * 0.55;
    const radiusY = 35;
    const curvePath = `M ${sourceX} ${sourceY - 10} A ${radiusX} ${radiusY} 0 1 0 ${targetX} ${targetY - 10}`;

    return (
      <BaseEdge
        path={curvePath}
        markerEnd={markerEnd}
        style={style}
      />
    );
  }

  const [edgePath] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  return (
    <BaseEdge
      id={id}
      path={edgePath}
      markerEnd={markerEnd}
      style={style}
    />
  );
};

export default DraggableEdge;
