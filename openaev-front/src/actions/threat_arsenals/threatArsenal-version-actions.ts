import { simpleCall, simplePostCall } from '../../utils/Action';
import { type PayloadVersionChangeInput } from '../../utils/api-types';

const versionUri = (actionId: string) => `/api/threat_arsenals/${actionId}`;

export const fetchThreatArsenalVersions = (actionId: string) => simpleCall(`${versionUri(actionId)}/versions`);

export const fetchThreatArsenalUsages = (actionId: string) => simpleCall(`${versionUri(actionId)}/usages`);

export const changeThreatArsenalVersion = (actionId: string, input: PayloadVersionChangeInput) => simplePostCall(`${versionUri(actionId)}/change-version`, input);
