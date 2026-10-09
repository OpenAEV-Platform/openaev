import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type {
  InjectorContractSearchPaginationInput, SearchPaginationInput,
  ThreatArsenalActionCreateInput, ThreatArsenalActionUpdateInput,
  ThreatArsenalApprovalImpactOutput, ThreatArsenalApproveInput, ThreatArsenalRejectInput,
} from '../../utils/api-types';
import { notifyErrorHandler } from '../../utils/error/errorHandlerUtil';
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

/**
 * The approval impact carried by a failed update, if the server refused it because saving would
 * send the approved payload back to pending while it is used (409 with the usage). The API
 * interceptor rejects with the response body spread on `{ status }`, not with an AxiosError.
 */
export const approvalImpactOf = (error: unknown): ThreatArsenalApprovalImpactOutput | null => {
  const rejected = error as ({ status?: number } & Partial<ThreatArsenalApprovalImpactOutput>) | null | undefined;
  if (rejected?.status === 409 && rejected.usage && typeof rejected.message === 'string') {
    return {
      message: rejected.message,
      usage: rejected.usage,
    };
  }
  return null;
};

export type ApprovalImpactCheckedUpdate
  = | {
    saved: true;
    data: unknown;
  }
  | {
    saved: false;
    approvalImpact: ThreatArsenalApprovalImpactOutput;
  };

/**
 * Updates an action, but lets the server refuse (nothing saved) an edit that would send the
 * approved payload back to pending while it is used: resolves with the approval impact instead,
 * so the caller can ask for confirmation, then save with {@link updateThreatArsenalAction}. That
 * refusal is not notified; any other error is notified and rethrown as usual.
 */
export const updateThreatArsenalActionCheckingApprovalImpact = (
  actionId: string,
  data: ThreatArsenalActionUpdateInput,
): Promise<ApprovalImpactCheckedUpdate> => {
  const uri = `${THREAT_ARSENAL_URI}/${actionId}`;
  return simplePutCall(uri, data, { params: { check_approval_impact: true } }, false, true)
    .then(response => ({
      saved: true as const,
      data: response.data,
    }))
    .catch((error) => {
      const approvalImpact = approvalImpactOf(error);
      if (approvalImpact) {
        return {
          saved: false as const,
          approvalImpact,
        };
      }
      notifyErrorHandler(error);
      throw error;
    });
};

// Payload approval: approve / reject need "Approve content"; errors (not pending, content changed
// since shown, missing reason) are surfaced by the default error handling.
// notifyError false: the caller handles errors itself (e.g. a 403 when Approve content was removed).
export const approveThreatArsenalAction = (actionId: string, data: ThreatArsenalApproveInput, notifyError = true) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/${actionId}/approve`, data, undefined, notifyError);
};

export const rejectThreatArsenalAction = (actionId: string, data: ThreatArsenalRejectInput, notifyError = true) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/${actionId}/reject`, data, undefined, notifyError);
};

export const fetchThreatArsenalActionApprovals = (actionId: string) => {
  return simpleCall(`${THREAT_ARSENAL_URI}/${actionId}/approvals`);
};

export const fetchThreatArsenalActionUsage = (actionId: string) => {
  return simpleCall(`${THREAT_ARSENAL_URI}/${actionId}/usage`);
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
