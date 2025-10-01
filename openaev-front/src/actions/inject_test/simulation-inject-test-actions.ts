import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type InjectBulkProcessingInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

const EXERCISE_URI = `/api/exercises`;

export const searchInjectTests = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `${EXERCISE_URI}/${simulationId}/injects/test/search`;
  return simplePostCall(uri, searchPaginationInput);
};

export const fetchInjectTestStatus = (testId: string) => {
  const uri = `${EXERCISE_URI}/injects/test/${testId}`;
  return simpleCall(uri);
};

export const testInject = (simulationId: string, injectId: string) => {
  const uri = `${EXERCISE_URI}/${simulationId}/injects/${injectId}/test`;
  return simpleCall(uri).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const bulkTestInjects = (simulationId: string, data: InjectBulkProcessingInput) => {
  const uri = `${EXERCISE_URI}/${simulationId}/injects/test`;
  return simplePostCall(uri, data, undefined, false).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const deleteInjectTest = (simulationId: string, testId: string) => {
  const uri = `${EXERCISE_URI}/${simulationId}/injects/test/${testId}`;
  return simpleDelCall(uri);
};
