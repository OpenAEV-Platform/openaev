import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type {
  InjectorContractSearchPaginationInput, SearchPaginationInput,
  ThreatArsenalActionCreateInput, ThreatArsenalActionUpdateInput,
} from '../../utils/api-types';
import { arrayOfSecurityPlatforms } from '../assets/asset-schema';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

export const searchThreatArsenalActions = (paginationInput: InjectorContractSearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/search`, paginationInput);
};

export const searchNonTabletopThreatArsenalActions = (paginationInput: InjectorContractSearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/search/non-tabletop`, paginationInput);
};

export const addThreatArsenalAction = (data: ThreatArsenalActionCreateInput) => {
  return simplePostCall(THREAT_ARSENAL_URI, data, {}, true, true);
};

export const fetchThreatArsenalAction = (actionId: string) => {
  const uri = `${THREAT_ARSENAL_URI}/${actionId}`;
  return simpleCall(uri);
};

export const updateThreatArsenalAction = (actionId: string, data: ThreatArsenalActionUpdateInput) => {
  const uri = `${THREAT_ARSENAL_URI}/${actionId}`;
  return simplePutCall(uri, data, {}, true, true);
};

export const duplicateThreatArsenalAction = (actionId: string) => {
  const uri = `${THREAT_ARSENAL_URI}/${actionId}/duplicate`;
  return simplePostCall(uri, {});
};

export const exportThreatArsenalAction = (actionId: string) => {
  return simpleCall(`${THREAT_ARSENAL_URI}/${actionId}/export`, {
    params: { include: true },
    headers: { Accept: 'application/zip' },
    responseType: 'blob',
  });
};

export const importThreatArsenalAction = (content: FormData) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/import`, content, { params: { include: true } }, true, true);
};

export const deleteThreatArsenalAction = (actionId: string) => {
  return simpleDelCall(`${THREAT_ARSENAL_URI}/${actionId}`, {}, true, true);
};

export const bulkDeleteThreatArsenalActions = (input: InjectorContractSearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/bulk-delete`, input, {}, true, true);
};

// Distinct authors + counts for the current filters, so the sidebar can keep
// every author visible and grey out the zero-count ones (like the domain facet).
export const fetchThreatArsenalAuthorCounts = (input: SearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/author-counts`, input);
};

// Platform + payload-status counts for the current filters, so the fixed-universe
// sidebar facets show live counts like the domain and author facets.
export const fetchThreatArsenalFacetCounts = (input: SearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/facet-counts`, input);
};

// Security platforms carrying detection remediations for this action (scoped
// endpoint for users without the global security-platform read capability).
export const fetchSecurityPlatformsForActionRemediation = (actionId: string) => (dispatch: Dispatch) => {
  const uri = `${THREAT_ARSENAL_URI}/${actionId}/security-platforms`;
  return getReferential(arrayOfSecurityPlatforms, uri)(dispatch);
};

export const exportThreatArsenalCsvMapper = (searchPaginationInput: SearchPaginationInput | undefined) => {
  const uri = `${THREAT_ARSENAL_URI}/export/csv`;
  return simplePostCall(uri, searchPaginationInput).then((response) => {
    return {
      data: response.data,
      filename: response.headers['content-disposition'].split('filename=')[1],
    };
  });
};
