import { type Executor } from '../../utils/api-types';

export interface ExecutorHelper {
  getExecutor: (executorId: string) => Executor;
  getExistingExecutors: () => Executor [];
  getExecutorsIncludingPending: () => Executor [];
  getExecutorsMap: () => Record<string, Executor>;
}
