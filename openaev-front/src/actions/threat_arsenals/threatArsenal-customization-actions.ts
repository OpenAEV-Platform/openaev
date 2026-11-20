import { simpleCall, simplePostCall } from '../../utils/Action';
import { type PayloadCustomizationUpdateInput } from '../../utils/api-types';

const actionUri = (actionId: string) => `/api/threat_arsenals/${actionId}`;

export const customizeThreatArsenalAction = (actionId: string) => simplePostCall(`${actionUri(actionId)}/customize`);

export const fetchThreatArsenalCustomization = (actionId: string) => simpleCall(`${actionUri(actionId)}/customization`);

export const updateThreatArsenalCustomization = (actionId: string, input: PayloadCustomizationUpdateInput) => simplePostCall(`${actionUri(actionId)}/customization/update`, input);
