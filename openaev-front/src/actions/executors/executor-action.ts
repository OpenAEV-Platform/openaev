import type { Dispatch } from 'redux';

import { delReferential, getReferential, simpleCall } from '../../utils/Action';
import * as schema from '../Schema';

const EXECUTOR_URI = '/api/executors';

export const fetchExecutors = (isNextIncluded = false) => (dispatch: Dispatch) => {
  const uri = `${EXECUTOR_URI}?include_next=${isNextIncluded}`;
  return getReferential(schema.arrayOfExecutors, uri)(dispatch);
};

export const fetchExecutor = (executorId: string) => (dispatch: Dispatch) => {
  const uri = `${EXECUTOR_URI}/${executorId}`;
  return getReferential(schema.executor, uri)(dispatch);
};

export const fetchExecutorRelatedIds = (executorId: string) => {
  return simpleCall(`${EXECUTOR_URI}/${executorId}/related-ids`);
};

export const deleteExecutor = (executorId: string) => (dispatch: Dispatch) => {
  return delReferential(`${EXECUTOR_URI}/${executorId}`, 'executors', executorId)(dispatch);
};
