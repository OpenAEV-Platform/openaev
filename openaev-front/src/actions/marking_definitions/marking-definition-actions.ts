import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type MarkingDefinitionInput,
  type MarkingDefinitionOutput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema';

const MARKING_DEFINITIONS_URI = '/api/marking_definitions';

export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/search`, searchPaginationInput);
};

// Every definition of the tenant, in one generous page: the set is small (nine seeded TLP/PAP levels
// per tenant), there is nothing to paginate through.
export const listMarkingDefinitions = ({ signal }: { signal?: AbortSignal }): Promise<MarkingDefinitionOutput[]> =>
  simplePostCall(`${MARKING_DEFINITIONS_URI}/search`, {
    page: 0,
    size: 200,
  }, { signal }, false).then(response => response.data?.content ?? []);

// The tenant's marking definitions narrowed server-side to the current user's own clearance
// (cumulative per type, same rule enforced when a marking is actually assigned) - for populating
// an assignment picker with only the options a submission would actually be allowed to include,
// and the marking filter's value pickers (a marking above the clearance could only match rows the
// user cannot see).
export const getAssignableMarkingDefinitions = ({ signal }: { signal?: AbortSignal }): Promise<MarkingDefinitionOutput[]> =>
  simpleCall(`${MARKING_DEFINITIONS_URI}/assignable`, { signal }, false).then(response => response.data ?? []);

// Whether any entity currently carries this marking definition - checked before a delete, which
// silently removes the definition from every entity that holds it.
export const fetchMarkingDefinitionAssigned = (markingDefinitionId: string) => {
  return simpleCall(`${MARKING_DEFINITIONS_URI}/${markingDefinitionId}/assigned`);
};

export const createMarkingDefinition = (input: MarkingDefinitionInput) => (dispatch: Dispatch) => {
  return postReferential(schema.markingDefinition, MARKING_DEFINITIONS_URI, input)(dispatch);
};

export const updateMarkingDefinition = (
  markingDefinitionId: string,
  input: MarkingDefinitionInput,
) => (dispatch: Dispatch) => {
  return putReferential(schema.markingDefinition, `${MARKING_DEFINITIONS_URI}/${markingDefinitionId}`, input)(dispatch);
};

export const deleteMarkingDefinition = (markingDefinitionId: string) => (dispatch: Dispatch) => {
  return delReferential(`${MARKING_DEFINITIONS_URI}/${markingDefinitionId}`, 'marking_definitions', markingDefinitionId)(dispatch);
};
