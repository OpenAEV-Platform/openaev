import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type {
  InjectorContractSearchPaginationInput,
  ThreatArsenalActionCreateInput, ThreatArsenalActionUpdateInput,
} from '../../utils/api-types';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

export const searchThreatArsenalActions = (paginationInput: InjectorContractSearchPaginationInput) => {
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
