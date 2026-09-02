import { simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput } from '../../utils/api-types';

const MARKING_DEFINITION_URI = '/api/marking-definitions';

// -- SEARCH --

/**
 * Resolves marking ids carried by a row (`asset_markings`) to their definitions.
 *
 * Only the search call is exposed: managing definitions is done through the API in this PoC, so
 * there is no create/update/delete UI to back the rest of the CRUD surface.
 */
// eslint-disable-next-line import/prefer-default-export
export const searchMarkingDefinitions = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${MARKING_DEFINITION_URI}/search`, searchPaginationInput);
};
