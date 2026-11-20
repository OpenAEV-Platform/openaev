import { NODE_CIRCLE_SIZE, NODE_WIDTH, type Point } from './graph-edge-geometry';

/** The free-hand lasso of the graph (OpenCTI's LassoSelection): a closed polygon drawn by the pointer. */

/** Even-odd ray casting: true when the point is inside the polygon (its edge counts as outside). */
export const pointInPolygon = (point: Point, polygon: Point[]): boolean => {
  if (polygon.length < 3) {
    return false;
  }
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i, i += 1) {
    const a = polygon[i];
    const b = polygon[j];
    const crosses = (a.y > point.y) !== (b.y > point.y)
      && point.x < ((b.x - a.x) * (point.y - a.y)) / (b.y - a.y) + a.x;
    if (crosses) {
      inside = !inside;
    }
  }
  return inside;
};

/** A lasso shorter than this (in screen pixels, along its path) is a click, never a selection. */
export const LASSO_MIN_LENGTH = 12;

export const pathLength = (points: Point[]): number => points.reduce(
  (sum, p, i) => (i === 0 ? 0 : sum + Math.hypot(p.x - points[i - 1].x, p.y - points[i - 1].y)),
  0,
);

/**
 * The ids of the nodes the lasso surrounds: a node is in when the centre of its circle is (positions
 * are the top-left corner of the node box, in flow coordinates, as React Flow draws them). Group nodes
 * are left to the caller to drop.
 */
export const nodesInLasso = (polygon: Point[], positions: Map<string, Point>): string[] => {
  if (polygon.length < 3) {
    return [];
  }
  const ids: string[] = [];
  positions.forEach((position, id) => {
    const centre = {
      x: position.x + NODE_WIDTH / 2,
      y: position.y + NODE_CIRCLE_SIZE / 2,
    };
    if (pointInPolygon(centre, polygon)) {
      ids.push(id);
    }
  });
  return ids;
};
