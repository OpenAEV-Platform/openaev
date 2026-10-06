import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type MarkingDefinitionInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema';

const MARKING_DEFINITIONS_URI = '/api/marking_definitions';

export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/search`, searchPaginationInput);
};

// The tenant's marking definitions narrowed server-side to the current user's own clearance
// (cumulative per type, same rule enforced when a marking is actually assigned) - for populating
// an assignment picker with only the options a submission would actually be allowed to include.
// Unlike searchMarkingDefinitions, this returns a plain array, not a paginated {content: [...]}.
export const fetchAssignableMarkingDefinitions = () => {
  return simpleCall(`${MARKING_DEFINITIONS_URI}/assignable`);
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
