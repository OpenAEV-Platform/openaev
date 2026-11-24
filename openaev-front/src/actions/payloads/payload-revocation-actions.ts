import { simpleDelCall, simplePostCall } from '../../utils/Action';
import { type PayloadRevocationInput } from '../../utils/api-types';

const revocationUri = (payloadId: string) => `/api/payloads/${payloadId}/revocation`;

export const revokePayload = (payloadId: string, input: PayloadRevocationInput) => simplePostCall(revocationUri(payloadId), input);

export const liftPayloadRevocation = (payloadId: string) => simpleDelCall(revocationUri(payloadId));
