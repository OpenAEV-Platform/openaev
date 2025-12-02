import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { getReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type AtomicTestingInput, type Collector, type InjectExpectation, type InjectExpectationTrace, type InjectResultOutput, type InjectResultOverviewOutput, type SearchPaginationInput, type StatusPayloadOutput, type TeamOutput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { arrayOfCollectors } from '../schemas';

const ATOMIC_TESTING_URI = '/api/atomic-testings';
const EXPECTATION_TRACE_URI = '/api/inject-expectations-traces';

export const searchAtomicTestings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${ATOMIC_TESTING_URI}/search`;
  return simplePostCall<Page<InjectResultOutput>>(uri, data);
};

export const fetchInjectResultOverviewOutput = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simpleCall<InjectResultOverviewOutput>(uri);
};

export const fetchAtomicTestingPayload = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/payload`;
  return simpleCall<StatusPayloadOutput>(uri);
};

export const deleteAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simpleDelCall(uri);
};

export const updateAtomicTesting = (injectId: string, data: AtomicTestingInput) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}`;
  return simplePutCall<InjectResultOverviewOutput>(uri, data);
};

export const duplicateAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/duplicate`;
  return simplePostCall<InjectResultOverviewOutput>(uri, null);
};

export const launchAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/launch`;
  return simplePostCall<InjectResultOverviewOutput>(uri);
};

export const relaunchAtomicTesting = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/relaunch`;
  return simplePostCall<InjectResultOverviewOutput>(uri);
};

export const fetchTargetResult = (injectId: string, targetId: string, targetType: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/target_results/${targetId}/types/${targetType}`;
  return simpleCall<InjectExpectation[]>(uri);
};

export const createAtomicTesting = (data: AtomicTestingInput) => {
  return simplePostCall<InjectResultOverviewOutput>(ATOMIC_TESTING_URI, data);
};

// -- TEAMS --

export const searchAtomicTestingTeams = (paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${ATOMIC_TESTING_URI}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall<Page<TeamOutput>>(uri, paginationInput);
};

// -- EXPECTATION TRACES --
export const fetchExpectationTraces = (injectExpectationId: string, sourceId: string) => {
  const uri = `${EXPECTATION_TRACE_URI}?injectExpectationId=${injectExpectationId}&sourceId=${sourceId}`;
  return simpleCall<InjectExpectationTrace[]>(uri);
};

// -- COLLECTORS --
export const fetchCollectorsForAtomicTesting = (injectId: string) => (dispatch: Dispatch) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/collectors`;
  return getReferential<Collector[]>(arrayOfCollectors, uri)(dispatch);
};

// -- ALERT LINKS COUNT --
export const getAlertLinksCount = (injectExpectationId: string, sourceId: string | undefined, expectationResultSourceType: string | undefined) => {
  const uri = `${EXPECTATION_TRACE_URI}/count?injectExpectationId=${injectExpectationId}&sourceId=${sourceId}&expectationResultSourceType=${expectationResultSourceType}`;
  return simpleCall<number>(uri);
};

export const importAtomicTesting = (file: File) => {
  const uri = `${ATOMIC_TESTING_URI}/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall<void>(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import atomic testing');
    throw error;
  });
};
