import { BaseEdge, type EdgeProps, getStraightPath, type InternalNode, useInternalNode } from '@xyflow/react';
import { memo } from 'react';

import { edgeEndpoint, NODE_CIRCLE_SIZE, NODE_LABEL_OFFSET, NODE_WIDTH, type Point, type Rect } from './graph-edge-geometry';

const RADIUS = NODE_CIRCLE_SIZE / 2;

const circleCenter = (node: InternalNode): Point => ({
  x: node.internals.positionAbsolute.x + (node.measured.width ?? NODE_WIDTH) / 2,
  y: node.internals.positionAbsolute.y + RADIUS,
});

const labelRect = (node: InternalNode): Rect | null => {
  const height = node.measured.height;
  if (!height) {
    return null;
  }
  const { x, y } = node.internals.positionAbsolute;
  return {
    left: x,
    right: x + (node.measured.width ?? NODE_WIDTH),
    top: y + NODE_LABEL_OFFSET,
    bottom: y + height,
  };
};

/**
 * A straight edge between the two node circles, whatever their relative position: it never starts
 * or ends inside a circle and never runs through a node's label (fixed top/bottom handles drew an
 * edge to a node above through both labels).
 */
const GraphFloatingEdge = ({ id, source, target, markerEnd, style, label, labelStyle, labelBgStyle }: EdgeProps) => {
  const sourceNode = useInternalNode(source);
  const targetNode = useInternalNode(target);
  if (!sourceNode || !targetNode) {
    return null;
  }
  const sc = circleCenter(sourceNode);
  const tc = circleCenter(targetNode);
  const start = edgeEndpoint(sc, tc, RADIUS, labelRect(sourceNode));
  const end = edgeEndpoint(tc, sc, RADIUS, labelRect(targetNode));
  const [path, labelX, labelY] = getStraightPath({
    sourceX: start.x,
    sourceY: start.y,
    targetX: end.x,
    targetY: end.y,
  });
  return (
    <BaseEdge
      id={id}
      path={path}
      markerEnd={markerEnd}
      style={style}
      label={label}
      labelX={labelX}
      labelY={labelY}
      labelStyle={labelStyle}
      labelBgStyle={labelBgStyle}
    />
  );
};

export default memo(GraphFloatingEdge);
