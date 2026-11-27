import { isFeatureEnabled } from '../utils';

/**
 * The exploration graph preview feature (EXPLORATION_GRAPH, off by default): the Graph page and
 * tabs, "Explore in graph", asset links, the OpenCTI / OpenCRQ pivots and the merged Dashboards
 * entry. Off, every screen is the one shipped before the feature, and the backend answers 404.
 */
const useExplorationGraph = (): boolean => isFeatureEnabled('EXPLORATION_GRAPH');

export default useExplorationGraph;
