import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';

const CREDENTIAL_URI = '/api/credentials';

export const searchCredentials = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${CREDENTIAL_URI}/search`, searchPaginationInput);
};

export const fetchCredential = (credentialId: string) => {
  return simpleCall(`${CREDENTIAL_URI}/${credentialId}`);
};

export const fetchCredentialContracts = () => {
  return simpleCall(`${CREDENTIAL_URI}/contracts`);
};

// Both write endpoints are multipart: an `input` JSON part plus one part per uploaded file
// (currently only the GCP service account key). The FormData is built by CredentialForm.
export const createCredential = (formData: FormData) => {
  return simplePostCall(CREDENTIAL_URI, formData, undefined, true, true);
};

export const updateCredential = (credentialId: string, formData: FormData) => {
  return simplePutCall(`${CREDENTIAL_URI}/${credentialId}`, formData, undefined, true, true);
};

export const deleteCredential = (credentialId: string) => {
  return simpleDelCall(`${CREDENTIAL_URI}/${credentialId}`);
};

// Bulk processing input shape mirrors the backend CredentialBulkProcessingInput DTO.
export interface CredentialBulkProcessingInput {
  search_pagination_input?: SearchPaginationInput;
  credential_ids_to_process?: string[];
  credential_ids_to_ignore?: string[];
}

export const bulkDeleteCredentials = (input: CredentialBulkProcessingInput) => {
  return simpleDelCall(CREDENTIAL_URI, { data: input });
};
