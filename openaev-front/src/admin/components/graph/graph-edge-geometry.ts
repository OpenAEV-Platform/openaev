export interface Point {
  x: number;
  y: number;
}

export interface Rect {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

/** Node geometry shared by the node and the floating edges: the circle on top, the label under it. */
export const NODE_CIRCLE_SIZE = 44;
export const NODE_WIDTH = 140;
export const LABEL_MARGIN = 4;
export const NODE_LABEL_OFFSET = NODE_CIRCLE_SIZE + LABEL_MARGIN;

/** Gap kept between an edge end and the node circle it reaches. */
export const EDGE_GAP = 4;

/**
 * Where an edge drawn from `center` toward `toward` meets the node: on the circle of `radius`
 * plus a small gap, whatever the direction. The arrowhead lands on the circle it points to; an edge
 * that passes under the label is hidden by the label's own opaque background (edges are drawn under
 * the nodes), so it never needs to stop short of the circle.
 */
export const edgeEndpoint = (center: Point, toward: Point, radius: number): Point => {
  const dx = toward.x - center.x;
  const dy = toward.y - center.y;
  const length = Math.hypot(dx, dy);
  if (length === 0) {
    return center;
  }
  const t = radius + EDGE_GAP;
  return {
    x: center.x + (dx / length) * t,
    y: center.y + (dy / length) * t,
  };
};
