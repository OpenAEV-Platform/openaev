import type { Dispatch } from 'redux';

import { ImportFileError } from '../../components/common/import/import-report-types';
import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type ImportReport,
  type MarkingDefinitionInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { type Error as ApiError, notifyErrorHandler } from '../../utils/error/errorHandlerUtil';
import * as schema from '../Schema';

const MARKING_DEFINITIONS_URI = '/api/marking_definitions';

export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/search`, searchPaginationInput);
};

// The tenant's marking definitions narrowed server-side to the current user's own clearance
// (cumulative per type, same rule enforced when a marking is actually assigned) - for populating
// an assignment picker with only the options a submission would actually be allowed to include,
// and the marking filter's value pickers (a marking above the clearance could only match rows the
// user cannot see).
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

// Exports what the list currently shows: the search input of the list, without its paging.
export const exportMarkingDefinitions = (searchPaginationInput: SearchPaginationInput): Promise<{
  data: string;
  filename: string;
}> => {
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/export/csv`, searchPaginationInput).then((response) => {
    return {
      data: response.data,
      filename: response.headers['content-disposition']?.split('filename=')[1] ?? 'MarkingDefinitions.csv',
    };
  });
};

// A 400 is a file-level failure (empty file, missing column, too big...): the generic error
// notification is turned off for it, the message is shown in the report dialog as an
// ImportFileError. Any other failure keeps the generic notification.
export const importMarkingDefinitions = (file: File): Promise<ImportReport> => {
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(`${MARKING_DEFINITIONS_URI}/import/csv`, formData, undefined, false)
    .then(response => response.data)
    .catch((error: ApiError) => {
      if (error.status === 400 && error.message) {
        throw new ImportFileError(error.message);
      }
      notifyErrorHandler(error);
      throw error;
    });
};
