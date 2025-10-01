import { type Edge, type Node } from '@xyflow/react';

import d3Hierarchy from './d3-hierarchy';
import dagre from './dagre';

// the layout direction (T = top, R = right, B = bottom, L = left, TB = top to bottom, ...)
export type Direction = 'TB' | 'LR' | 'RL' | 'BT';

export type LayoutAlgorithmOptions = {
  direction: Direction;
  spacing: [number, number];
};

export type LayoutAlgorithm = (
  nodes: Node[],
  edges: Edge[],
  options: LayoutAlgorithmOptions
) => Promise<{
  nodes: Node[];
  edges: Edge[];
}>;

export default {
  dagre,
  'd3-hierarchy': d3Hierarchy,
};
