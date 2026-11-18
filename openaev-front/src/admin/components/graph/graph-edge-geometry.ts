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
 * Where an edge drawn from `center` toward `toward` leaves the node: on the circle of `radius`
 * plus a gap, or past the node's label when the edge would otherwise cross it (an edge going down
 * from a node runs through the label drawn under its circle).
 */
export const edgeEndpoint = (center: Point, toward: Point, radius: number, label: Rect | null): Point => {
  const dx = toward.x - center.x;
  const dy = toward.y - center.y;
  const length = Math.hypot(dx, dy);
  if (length === 0) {
    return center;
  }
  const ux = dx / length;
  const uy = dy / length;
  let t = radius + EDGE_GAP;
  if (label && uy > 0) {
    const enterY = (label.top - center.y) / uy;
    let exitX = Number.POSITIVE_INFINITY;
    if (ux > 0) {
      exitX = (label.right - center.x) / ux;
    } else if (ux < 0) {
      exitX = (label.left - center.x) / ux;
    }
    const exitY = (label.bottom - center.y) / uy;
    const exit = Math.min(exitX, exitY);
    if (enterY <= exit) {
      t = Math.max(t, exit + EDGE_GAP);
    }
  }
  return {
    x: center.x + ux * t,
    y: center.y + uy * t,
  };
};
