import { type Dispatch } from 'redux';

import { getReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type AtomicTestingInput, type InjectBulkProcessingInput, type InjectRecurrenceInput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { arrayOfSecurityPlatforms } from '../assets/asset-schema';
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

export const bulkDeleteAtomicTestings = (input: InjectBulkProcessingInput) => {
  return simpleDelCall(ATOMIC_TESTING_URI, { data: input });
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

export const updateAtomicTestingRecurrence = (injectId: string, data: InjectRecurrenceInput) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/recurrence`;
  return simplePutCall(uri, data);
};

// -- EXPECTATIONS DRIFT --

export const fetchAtomicTestingExpectationsDrift = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/expectations-drift`;
  return simpleCall(uri);
};

export const realignAtomicTestingExpectations = (injectId: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/expectations-drift/realign`;
  return simplePostCall(uri);
};

// Dismissal is stored in database (not local storage) so it is shared between
// users. The generic success toast is disabled: the caller notifies with a
// dismissal-specific message.
export const dismissAtomicTestingExpectationsDrift = (injectId: string, dismissed: boolean) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/expectations-drift/dismiss`;
  return simplePutCall(uri, { dismissed }, undefined, true, false);
};

export const fetchTargetResult = (injectId: string, targetId: string, targetType: string) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/target_results/${targetId}/types/${targetType}`;
  return simpleCall(uri);
};

export const fetchTargetResultAssetWithAgents = (injectId: string, targetId: string, expectationType: string) => (dispatch: Dispatch) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/target_results/${targetId}/asset_with_agents?expectationType=${expectationType}`;
  return getReferential(schema.arrayOfInjectexpectations, uri)(dispatch);
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

// -- SECURITY PLATFORMS --
// Security platforms carrying detection remediations for this inject (scoped
// endpoint for users without the global security-platform read capability).
export const fetchSecurityPlatformsForAtomicTesting = (injectId: string) => (dispatch: Dispatch) => {
  const uri = `${ATOMIC_TESTING_URI}/${injectId}/security-platforms`;
  return getReferential(arrayOfSecurityPlatforms, uri)(dispatch);
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
