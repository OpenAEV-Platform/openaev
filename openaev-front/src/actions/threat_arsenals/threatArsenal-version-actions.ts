import { simpleCall, simplePostCall } from '../../utils/Action';
import { type PayloadVersionChangeInput, type ThreatArsenalActionUpdateInput } from '../../utils/api-types';

const versionUri = (actionId: string) => `/api/threat_arsenals/${actionId}`;

export const fetchThreatArsenalVersions = (actionId: string) => simpleCall(`${versionUri(actionId)}/versions`);

export const fetchThreatArsenalUsages = (actionId: string) => simpleCall(`${versionUri(actionId)}/usages`);

export const changeThreatArsenalVersion = (actionId: string, input: PayloadVersionChangeInput) => simplePostCall(`${versionUri(actionId)}/change-version`, input);

export const fetchThreatArsenalHistory = (actionId: string) => simpleCall(`${versionUri(actionId)}/history`);

/** What a version changes, field by field, compared with another version of the same payload; without one, everything is added. */
export const fetchThreatArsenalChanges = (actionId: string, comparedToActionId?: string) => simpleCall(
  `${versionUri(actionId)}/changes`,
  comparedToActionId ? { params: { compared_to: comparedToActionId } } : undefined,
  false,
);

/** Payload drafts: what the editor's content changes in the version, field by field, without saving. */
export const fetchThreatArsenalContentChanges = (actionId: string, input: ThreatArsenalActionUpdateInput) => simplePostCall(
  `${versionUri(actionId)}/changes`,
  input,
  undefined,
  false,
);
