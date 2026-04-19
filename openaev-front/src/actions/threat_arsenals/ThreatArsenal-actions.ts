import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type {
  SearchPaginationInput,
  ThreatArsenalActionCreateInput, ThreatArsenalActionUpdateInput,
} from '../../utils/api-types';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

export const searchThreatArsenalActions = (paginationInput: SearchPaginationInput) => {
  return simplePostCall(`${THREAT_ARSENAL_URI}/search`, paginationInput);
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
