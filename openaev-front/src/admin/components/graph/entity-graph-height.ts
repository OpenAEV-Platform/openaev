// The Graph tab canvas never goes under this height, and keeps this gap above the window's bottom.
export const EMBEDDED_GRAPH_MIN_HEIGHT = 420;
export const EMBEDDED_GRAPH_BOTTOM_GAP = 24;

/**
 * Height of the Graph tab canvas: the room left between its own top and the bottom of the window,
 * so the canvas toolbar and the legend stay inside the viewport and the page never scrolls (#9448,
 * UX round 1 B1). The top is measured, never guessed from the header heights above it.
 */
export const embeddedGraphHeight = (top: number, viewportHeight: number): number => {
  return Math.max(EMBEDDED_GRAPH_MIN_HEIGHT, Math.floor(viewportHeight - top - EMBEDDED_GRAPH_BOTTOM_GAP));
};
