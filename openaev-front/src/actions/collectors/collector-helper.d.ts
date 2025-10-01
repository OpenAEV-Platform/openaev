import { type Collector } from '../../utils/api-types';

export interface CollectorHelper {
  getCollectors: () => Collector[];
  getCollectorsMap: () => Record<string, Collector>;
}
