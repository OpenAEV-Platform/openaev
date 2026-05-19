import type { Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import {
  type Payload,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema';

export const PAYLOAD_URI = '/api/payloads';

export const searchPayloads = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/payloads/search';
  return simplePostCall(uri, data);
};

export const fetchPayload = (payloadId: string) => {
  const uri = `/api/payloads/${payloadId}`;
  return simpleCall(uri);
};

export const deletePayload = (payloadId: Payload['payload_id']) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}`;
  return delReferential(uri, 'payloads', payloadId)(dispatch);
};

// -- DOCUMENTS --
export const fetchDocumentsPayload = async (payloadId: string) => {
  const uri = `${PAYLOAD_URI}/${payloadId}/documents`;
  const result = await simpleCall(uri);
  return result.data;
};

// -- COLLECTORS --
export const fetchCollectorsForPayload = (payloadId: string) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}/collectors`;
  return getReferential(schema.arrayOfCollectors, uri)(dispatch);
};
