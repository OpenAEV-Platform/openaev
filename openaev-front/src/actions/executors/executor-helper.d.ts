import { type Executor } from '../../utils/api-types';

export interface ExecutorHelper {
  getExecutors: () => Executor[];
  getExecutorsMap: () => Record<string, Executor>;
}
