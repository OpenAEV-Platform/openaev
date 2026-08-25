import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { MarkingDefinitionInput, MarkingDefinitionOutput, SearchPaginationInput } from '../../utils/api-types';

const MARKING_DEFINITION_URI = '/api/marking-definitions';

// -- CREATE --

export const addMarkingDefinition = (data: MarkingDefinitionInput) => {
  return simplePostCall(MARKING_DEFINITION_URI, data, undefined, true, true);
};

// -- READ --

export const fetchMarkingDefinition = (markingId: MarkingDefinitionOutput['marking_id']) => {
  return simpleCall(`${MARKING_DEFINITION_URI}/${markingId}`);
};

// -- SEARCH --

export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITION_URI}/search`, searchPaginationInput);
};

// -- UPDATE --

export const updateMarkingDefinition = (markingId: MarkingDefinitionOutput['marking_id'], data: MarkingDefinitionInput) => {
  return simplePutCall(`${MARKING_DEFINITION_URI}/${markingId}`, data);
};

// -- DELETE --

export const deleteMarkingDefinition = (markingId: MarkingDefinitionOutput['marking_id']) => {
  return simpleDelCall(`${MARKING_DEFINITION_URI}/${markingId}`);
};
