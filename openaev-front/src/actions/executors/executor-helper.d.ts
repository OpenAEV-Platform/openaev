import { type Executor } from '../../utils/api-types';

export interface ExecutorHelper {
  getExecutor: (executorId: string) => Executor;
  getExecutors: () => Executor[];
  getExecutorsMap: () => Record<string, Executor>;
}
