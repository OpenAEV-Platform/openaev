import { type Collector } from '../../utils/api-types';

export interface CollectorHelper {
  getCollector: (collectorId: string) => Collector;
  getCollectors: () => Collector[];
  getCollectorsMap: () => Record<string, Collector>;
}
