import type { Dispatch } from 'redux';

import { type ImportReport } from '../../components/common/import/import-report-types';
import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type MarkingDefinitionInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema';
import { mockExportMarkingDefinitions, mockImportMarkingDefinitions } from './marking-definition-import-mock';

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

// -- EXPORT / IMPORT --

// The export/import endpoints are not implemented yet (issue #8690): while this is true, both calls
// are answered by marking-definition-import-mock.ts. Set to false and delete the mock once the
// backend is in.
export const USE_MOCK_IMPORT_EXPORT = true;

export const exportMarkingDefinitions = (): Promise<{
  data: string;
  filename: string;
}> => {
  if (USE_MOCK_IMPORT_EXPORT) {
    return mockExportMarkingDefinitions(searchMarkingDefinitions);
  }
  return simpleCall(`${MARKING_DEFINITIONS_URI}/export`).then((response) => {
    return {
      data: response.data,
      filename: response.headers['content-disposition'].split('filename=')[1],
    };
  });
};

export const importMarkingDefinitions = (file: File): Promise<ImportReport> => {
  if (USE_MOCK_IMPORT_EXPORT) {
    return mockImportMarkingDefinitions(file, searchMarkingDefinitions);
  }
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/import`, formData).then(response => response.data);
};

// -- OPTIONS --

export const searchMarkingDefinitionAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${MARKING_DEFINITIONS_URI}/options`, { params });
};

export const searchMarkingDefinitionByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/options`, ids);
};
