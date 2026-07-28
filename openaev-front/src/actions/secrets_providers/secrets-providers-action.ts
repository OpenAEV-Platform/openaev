import type { Dispatch } from 'redux';

import { delReferential, getReferential, simpleCall } from '../../utils/Action';
import * as schema from '../Schema';

const SECRETS_PROVIDERS_URI = '/api/secrets_providers';

export const fetchSecretsProviders = (isNextIncluded = false) => (dispatch: Dispatch) => {
  const uri = `${SECRETS_PROVIDERS_URI}?include_next=${isNextIncluded}`;
  return getReferential(schema.arrayOfSecretsProviders, uri)(dispatch);
};

export const fetchSecretProvider = (secrets_providerId: string) => (dispatch: Dispatch) => {
  const uri = `${SECRETS_PROVIDERS_URI}/${secrets_providerId}`;
  return getReferential(schema.secretsProvider, uri)(dispatch);
};

export const fetchSecretsProviderRelatedIds = (secrets_providerId: string) => {
  return simpleCall(`${SECRETS_PROVIDERS_URI}/${secrets_providerId}/related-ids`);
};

export const deleteSecretsProvider = (secrets_providerId: string) => (dispatch: Dispatch) => {
  return delReferential(`${SECRETS_PROVIDERS_URI}/${secrets_providerId}`, 'secretsproviders', secrets_providerId)(dispatch);
};
