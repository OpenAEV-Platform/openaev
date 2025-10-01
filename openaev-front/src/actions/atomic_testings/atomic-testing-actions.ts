import { type Dispatch } from 'redux';

import { getReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type AtomicTestingInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';

const ATOMIC_TESTING_URI = '/api/atomic-testings';
const EXPECTATION_TRACE_URI = '/api/inject-expectations-traces';

export const searchAtomicTestings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${ATOMIC_TESTING_URI}/search`;
  return simplePostCall(uri, data);
};

export const fetchInjectResultOverviewOutput = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simpleCall(uri);
};

export const fetchAtomicTestingPayload = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/payload`;
  return simpleCall(uri);
};

export const deleteAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simpleDelCall(uri);
};

export const updateAtomicTesting = (injectId: string, data: AtomicTestingInput) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simplePutCall(uri, data);
};

export const duplicateAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/duplicate`;
  return simplePostCall(uri, null);
};

export const launchAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/launch`;
  return simplePostCall(uri);
};

export const relaunchAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/relaunch`;
  return simplePostCall(uri);
};

export const fetchTargetResultMerged = (injectId: string, targetId: string, targetType: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/target_results/${targetId}/types/${targetType}/merged`;
  return simpleCall(uri);
};

export const createAtomicTesting = (data: AtomicTestingInput) => {
  return simplePostCall(ATOMIC_TESTING_URI, data);
};

// -- TEAMS --

export const searchAtomicTestingTeams = (paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${ATOMIC_TESTING_URI}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall(uri, paginationInput);
};

// -- EXPECTATION TRACES --
export const fetchExpectationTraces = (injectExpectationId: string, sourceId: string) => {
  const uri = `${EXPECTATION_TRACE_URI}?injectExpectationId=${injectExpectationId}&sourceId=${sourceId}`;
  return simpleCall(uri);
};

// -- COLLECTORS --
export const fetchCollectorsForAtomicTesting = (injectId: string) => (dispatch: Dispatch) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/collectors`;
  return getReferential(schema.arrayOfCollectors, uri)(dispatch);
};

// -- ALERT LINKS COUNT --
export const getAlertLinksCount = (injectExpectationId: string, sourceId: string | undefined, expectationResultSourceType: string | undefined) => {
  const uri = `${EXPECTATION_TRACE_URI}/count?injectExpectationId=${injectExpectationId}&sourceId=${sourceId}&expectationResultSourceType=${expectationResultSourceType}`;
  return simpleCall(uri);
};

export const importAtomicTesting = (file: File) => {
  const uri = `${ATOMIC_TESTING_URI}/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import atomic testing');
    throw error;
  });
};
