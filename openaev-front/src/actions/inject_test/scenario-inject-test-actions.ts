import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type InjectBulkProcessingInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

const SCENARIO_URI = `/api/scenarios`;

export const searchInjectTests = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/injects/test/search`;
  return simplePostCall(uri, searchPaginationInput);
};

export const fetchInjectTestStatus = (testId: string) => {
  const uri = `${SCENARIO_URI}/injects/test/${testId}`;
  return simpleCall(uri);
};

export const testInject = (scenarioId: string, injectId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/injects/${injectId}/test`;
  return simpleCall(uri).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const bulkTestInjects = (scenarioId: string, data: InjectBulkProcessingInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/injects/test`;
  return simplePostCall(uri, data, undefined, false).catch((error) => {
    MESSAGING$.notifyError('Can\'t be tested');
    throw error;
  });
};

export const deleteInjectTest = (scenarioId: string, testId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/injects/test/${testId}`;
  return simpleDelCall(uri);
};
