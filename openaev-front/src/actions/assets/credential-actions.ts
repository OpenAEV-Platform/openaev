import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type CredentialInput, type SearchPaginationInput } from '../../utils/api-types';

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

export const createCredential = (input: CredentialInput) => {
  return simplePostCall(CREDENTIAL_URI, input, undefined, true, true);
};

export const updateCredential = (credentialId: string, input: CredentialInput) => {
  return simplePutCall(`${CREDENTIAL_URI}/${credentialId}`, input, undefined, true, true);
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
