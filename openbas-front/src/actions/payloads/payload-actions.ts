import type { Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import { type Payload, type PayloadCreateInput, type PayloadUpdateInput, type SearchPaginationInput } from '../../utils/api-types';
import { arrayOfDocuments, payload } from '../Schema';

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

export const updatePayload = (payloadId: Payload['payload_id'], data: PayloadUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}`;
  return putReferential(payload, uri, data)(dispatch);
};

export const addPayload = (data: PayloadCreateInput) => (dispatch: Dispatch) => {
  const uri = '/api/payloads';
  return postReferential(payload, uri, data)(dispatch);
};

export const duplicatePayload = (payloadId: Payload['payload_id']) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}/duplicate`;
  return postReferential(payload, uri, {})(dispatch);
};

export const deletePayload = (payloadId: Payload['payload_id']) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}`;
  return delReferential(uri, 'payloads', payloadId)(dispatch);
};

// -- DOCUMENTS --
export const fetchDocumentsPayload = (payloadId: string) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}/documents`;
  return getReferential(arrayOfDocuments, uri)(dispatch);
};

// -- EXPORT --
export const exportPayload = (id: string) => {
  return simpleCall(`${PAYLOAD_URI}/${id}/export`, {
    params: { include: true },
    headers: { Accept: 'application/zip' },
    responseType: 'blob',
  });
};

// -- IMPORT --
export const importPayload = (content: FormData) => (dispatch: Dispatch) => {
  return postReferential(null, `${PAYLOAD_URI}/import`, content, { params: { include: true } })(dispatch);
};
