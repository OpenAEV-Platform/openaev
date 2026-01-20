import { type Executor } from '../../utils/api-types';

export interface ExecutorHelper {
  getExecutor: (executorId: string) => Executor;
  getExistingExecutor: () => Executor [];
  getExecutorsIncludingPending: () => Executor [];
  getExecutorsMap: () => Record<string, Executor>;
}
