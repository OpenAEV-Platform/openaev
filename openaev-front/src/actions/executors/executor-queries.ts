import { queryOptions } from '@tanstack/react-query';

import { type ExecutorOutput } from '../../utils/api-types';
import { liveMeta } from '../../utils/query/live';
import { STREAM_TYPES } from '../../utils/stream/streamTypes';
import { listExecutors } from './executor-action';

export const executorKeys = {
  all: ['executors'] as const,
  list: () => [...executorKeys.all, 'list'] as const,
};

export const executorQueries = {
  // The current executors only (no upcoming ones). Silent: the query cache notifies a failure once.
  list: () => queryOptions({
    queryKey: executorKeys.list(),
    queryFn: () => listExecutors(false, false).then((result: { data: ExecutorOutput[] }) => result.data),
    meta: liveMeta<ExecutorOutput[]>({
      refs: executors => executors.map(executor => ({
        type: STREAM_TYPES.EXECUTORS,
        id: executor.executor_id,
      })),
      collections: [STREAM_TYPES.EXECUTORS],
    }),
  }),
};
