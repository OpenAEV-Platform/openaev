import { simpleCall } from '../../utils/Action';
import type { Inject, InjectResultPayloadExecutionOutput } from '../../utils/api-types';
import { INJECT_URI } from '../injects/inject-action';

// eslint-disable-next-line import/prefer-default-export
export const fetchInjectExecutionResult = (injectId: Inject['inject_id'], targetId: string = '', targetType: string = '') => {
  const params = {
    targetId,
    targetType,
  };
  const uri = `${INJECT_URI}/${injectId}/execution-result`;
  return simpleCall<InjectResultPayloadExecutionOutput>(uri, { params });
};
