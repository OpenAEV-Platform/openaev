import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type PayloadDraftInput, type ThreatArsenalActionUpdateInput } from '../../utils/api-types';

// Payload drafts: versions not published yet, never usable. Any action of the payload reaches them.
const draftsUri = (actionId: string) => `/api/threat_arsenals/${actionId}/drafts`;

export const fetchThreatArsenalDrafts = (actionId: string) => simpleCall(draftsUri(actionId));

export const createThreatArsenalDraft = (actionId: string, input: PayloadDraftInput) => simplePostCall(draftsUri(actionId), input);

export const saveThreatArsenalDraft = (actionId: string, draftId: string, input: PayloadDraftInput) => simplePutCall(`${draftsUri(actionId)}/${draftId}`, input);

export const discardThreatArsenalDraft = (actionId: string, draftId: string) => simpleDelCall(`${draftsUri(actionId)}/${draftId}`);

export const publishThreatArsenalDraft = (actionId: string, draftId: string, input: ThreatArsenalActionUpdateInput) => simplePostCall(`${draftsUri(actionId)}/${draftId}/publish`, input, undefined, true, true);
