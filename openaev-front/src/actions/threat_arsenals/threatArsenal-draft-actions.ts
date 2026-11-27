import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type PayloadDraftInput, type ThreatArsenalActionCreateInput, type ThreatArsenalActionUpdateInput } from '../../utils/api-types';

// Payload drafts: versions not published yet, never usable. Any action of the payload reaches them.
const draftsUri = (actionId: string) => `/api/threat_arsenals/${actionId}/drafts`;

export const fetchThreatArsenalDrafts = (actionId: string) => simpleCall(draftsUri(actionId));

export const createThreatArsenalDraft = (actionId: string, input: PayloadDraftInput) => simplePostCall(draftsUri(actionId), input);

export const saveThreatArsenalDraft = (actionId: string, draftId: string, input: PayloadDraftInput) => simplePutCall(`${draftsUri(actionId)}/${draftId}`, input);

export const discardThreatArsenalDraft = (actionId: string, draftId: string) => simpleDelCall(`${draftsUri(actionId)}/${draftId}`);

export const publishThreatArsenalDraft = (actionId: string, draftId: string, input: ThreatArsenalActionUpdateInput) => simplePostCall(`${draftsUri(actionId)}/${draftId}/publish`, input, undefined, true, true);

// Drafts of new actions: no action and no version until published.
const newActionDraftsUri = '/api/threat_arsenals/new_action_drafts';

// Shared: everyone who can read the Threat Arsenal lists them (the Active list shows them).
export const fetchNewActionDrafts = () => simpleCall(newActionDraftsUri);

export const createNewActionDraft = (input: PayloadDraftInput) => simplePostCall(newActionDraftsUri, input);

export const saveNewActionDraft = (draftId: string, input: PayloadDraftInput) => simplePutCall(`${newActionDraftsUri}/${draftId}`, input);

export const discardNewActionDraft = (draftId: string) => simpleDelCall(`${newActionDraftsUri}/${draftId}`);

export const publishNewActionDraft = (draftId: string, input: ThreatArsenalActionCreateInput) => simplePostCall(`${newActionDraftsUri}/${draftId}/publish`, input, undefined, true, true);
