import { type Collector, type Executor } from '../../utils/api-types';

export interface CollectorHelper {
  getCollector: (collectorId: string) => Collector;
  getExistingCollectors: () => Executor [];
  getCollectorsIncludingPending: () => Executor [];
  getCollectorsMap: () => Record<string, Collector>;
}
