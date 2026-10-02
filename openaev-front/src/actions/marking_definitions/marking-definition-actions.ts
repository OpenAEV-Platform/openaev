import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simplePostCall } from '../../utils/Action';
import {
  type MarkingDefinitionInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema';

const MARKING_DEFINITIONS_URI = '/api/marking_definitions';

export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/search`, searchPaginationInput);
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
