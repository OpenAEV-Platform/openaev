import { type Edge, type Node, useNodesInitialized, useReactFlow, useStore } from '@xyflow/react';
import { useEffect } from 'react';

import { type InjectExpectationsStore } from '../../admin/components/common/injects/expectations/Expectation';
import layoutAlgorithms, { type LayoutAlgorithmOptions } from './algorithms';
import { getSourceHandlePosition, getTargetHandlePosition } from './utils';

export type LayoutOptions = { algorithm: keyof typeof layoutAlgorithms } & LayoutAlgorithmOptions;

function useAutoLayout(options: LayoutOptions, targetResults: InjectExpectationsStore[]) {
  const { getNodes, getEdges, setNodes, setEdges } = useReactFlow();
  const elements = useStore(
    state => ({
      nodeMap: state.nodeLookup,
      edgeMap: state.edges.reduce(
        (acc, edge) => acc.set(edge.id, edge),
        new Map(),
      ),
    }),
    // The compare elements function will only update `elements` if something has
    // changed that should trigger a layout. This includes changes to a node's
    // dimensions, the number of nodes, or changes to edge sources/targets.
    // eslint-disable-next-line @typescript-eslint/no-use-before-define
    compareElements,
  );
  const nodesInitialized = useNodesInitialized();
  useEffect(() => {
    // Only run the layout if there are nodes and they have been initialized with
    // their dimensions
    if (!nodesInitialized || elements.nodeMap.size === 0) {
      return;
    }
    // The callback passed to `useEffect` cannot be `async` itself, so instead we
    // create an async function here and call it immediately afterwards.
    const runLayout = async () => {
      const layoutAlgorithm = layoutAlgorithms[options.algorithm];
      const nodes = getNodes();
      const edges = getEdges();
      const { nodes: nextNodes, edges: nextEdges } = await layoutAlgorithm(
        nodes,
        edges,
        options,
      );
      // Mutating the nodes and edges directly here is fine because we expect our
      // layouting algorithms to return a new array of nodes/edges.
      for (const node of nextNodes) {
        node.style = {
          ...node.style,
          opacity: 1,
        };
        node.sourcePosition = getSourceHandlePosition(options.direction);
        node.targetPosition = getTargetHandlePosition(options.direction);
      }
      for (const edge of edges) {
        edge.style = {
          ...edge.style,
          opacity: 1,
        };
      }
      setNodes(nextNodes);
      setEdges(nextEdges);
    };
    runLayout();
  }, [nodesInitialized, elements, setNodes, setEdges, targetResults]);
}

export default useAutoLayout;

type Elements = {
  nodeMap: Map<string, Node>;
  edgeMap: Map<string, Edge>;
};

function compareElements(xs: Elements, ys: Elements) {
  // eslint-disable-next-line @typescript-eslint/no-use-before-define
  return compareNodes(xs.nodeMap, ys.nodeMap);
}

function compareNodes(xs: Map<string, Node>, ys: Map<string, Node>) {
  // the number of nodes changed, so we already know that the nodes are not equal
  if (xs.size !== ys.size) return false;

  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-expect-error
  for (const [id, x] of xs.entries()) {
    const y = ys.get(id);

    // the node doesn't exist in the next state so it just got added
    if (!y) return false;
    // We don't want to force a layout change while a user might be resizing a
    // node, so we only compare the dimensions if the node is not currently
    // being resized.
    //
    // We early return here instead of using a `continue` because there's no
    // scenario where we'd want nodes to start moving around *while* a user is
    // trying to resize a node or move it around.
    if (x.resizing || x.dragging) return true;
    if (x.width !== y.width || x.height !== y.height) return false;
    if (x.data.label !== y.data.label) return false;
  }

  return true;
}
