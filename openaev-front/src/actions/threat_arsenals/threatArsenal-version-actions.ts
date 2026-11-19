import { simpleCall } from '../../utils/Action';

const versionUri = (actionId: string) => `/api/threat_arsenals/${actionId}`;

export const fetchThreatArsenalVersions = (actionId: string) => simpleCall(`${versionUri(actionId)}/versions`);

export const fetchThreatArsenalUsages = (actionId: string) => simpleCall(`${versionUri(actionId)}/usages`);
