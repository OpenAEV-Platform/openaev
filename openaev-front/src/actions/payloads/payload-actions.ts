import type { Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import {
  type Collector,
  type Document,
  type JsonApiDocumentResourceObject,
  type Payload,
  type PayloadCreateInput,
  type PayloadUpdateInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { arrayOfCollectors, payload } from '../schemas';

export const PAYLOAD_URI = '/api/payloads';

export const searchPayloads = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/payloads/search';
  return simplePostCall<Page<Payload>>(uri, data);
};

export const fetchPayload = (payloadId: string) => {
  const uri = `/api/payloads/${payloadId}`;
  return simpleCall<Payload>(uri);
};

export const updatePayload = (payloadId: Payload['payload_id'], data: PayloadUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}`;
  return putReferential<Payload>(payload, uri, data)(dispatch);
};

export const addPayload = (data: PayloadCreateInput) => (dispatch: Dispatch) => {
  const uri = '/api/payloads';
  return postReferential<Payload>(payload, uri, data)(dispatch);
};

export const duplicatePayload = (payloadId: Payload['payload_id']) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}/duplicate`;
  return postReferential<Payload>(payload, uri, {})(dispatch);
};

export const deletePayload = (payloadId: Payload['payload_id']) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}`;
  return delReferential(uri, 'payloads', payloadId)(dispatch);
};

// -- DOCUMENTS --
export const fetchDocumentsPayload = async (payloadId: string) => {
  const uri = `${PAYLOAD_URI}/${payloadId}/documents`;
  const result = await simpleCall<Document[]>(uri);
  return result.data;
};

// -- COLLECTORS --
export const fetchCollectorsForPayload = (payloadId: string) => (dispatch: Dispatch) => {
  const uri = `/api/payloads/${payloadId}/collectors`;
  return getReferential<Collector[]>(arrayOfCollectors, uri)(dispatch);
};

// -- EXPORT --
export const exportPayload = (id: string) => {
  return simpleCall<Blob>(`${PAYLOAD_URI}/${id}/export`, {
    params: { include: true },
    headers: { Accept: 'application/zip' },
    responseType: 'blob',
  });
};

// -- IMPORT --
export const importPayload = (content: FormData) => {
  return simplePostCall<JsonApiDocumentResourceObject>(`${PAYLOAD_URI}/import`, content, { params: { include: true } });
};
