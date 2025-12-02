import { type Dispatch } from 'redux';

import { type Page } from '../components/common/queryable/Page';
import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { type DocumentCreateInput, type DocumentUpdateInput, type SearchPaginationInput } from '../utils/api-types';
import { arrayOfDocuments, document } from './schemas';

export const fetchDocuments = () => (dispatch: Dispatch) => getReferential<Document[]>(arrayOfDocuments, '/api/documents')(dispatch);

export const fetchDocument = (documentId: string) => (dispatch: Dispatch) => getReferential<Document>(document, `/api/documents/${documentId}`)(dispatch);

export const searchDocuments = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/documents/search';
  return simplePostCall<Page<Document>>(uri, data);
};

export const addDocument = (data: DocumentCreateInput) => (dispatch: Dispatch) => {
  const uri = '/api/documents';
  return postReferential<Document>(document, uri, data)(dispatch);
};

export const updateDocument = (documentId: string, data: DocumentUpdateInput) => (dispatch: Dispatch) => putReferential(
  document,
  `/api/documents/${documentId}`,
  data,
)(dispatch);

export const deleteDocument = (documentId: string) => (dispatch: Dispatch) => {
  const uri = `/api/documents/${documentId}`;
  return delReferential(uri, 'documents', documentId)(dispatch);
};

export const fetchSimulationPlayerDocuments = (simulationId: string, userId = null) => (dispatch: Dispatch) => getReferential(
  arrayOfDocuments,
  `/api/player/simulations/${simulationId}/documents${userId ? `?userId=${userId}` : ''}`,
)(dispatch);

export const fetchScenarioPlayerDocuments = (scenarioId: string, userId = null) => (dispatch: Dispatch) => getReferential(
  arrayOfDocuments,
  `/api/player/scenarios/${scenarioId}/documents${userId ? `?userId=${userId}` : ''}`,
)(dispatch);
