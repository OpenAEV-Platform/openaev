import DOTS from '../../../../constants/Strings';
import type { CredentialContractField } from '../../../../utils/api-types';

const namesWhere = (
  fields: CredentialContractField[] | undefined,
  flag: 'endpoint' | 'reset_on_endpoint_change',
): string[] => (fields ?? []).filter(field => field[flag]).map(field => field.field_name);

/** Fields holding a URL or URI: pointing one of them elsewhere sends the stored secrets there. */
export const endpointFieldsOf = (fields: CredentialContractField[] | undefined): string[] => namesWhere(fields, 'endpoint');

// Same rule as the backend: only a non-blank value different from the stored one is a redirection,
// clearing the field falls back to the official endpoint.
const isRedirected = (current: unknown, stored: unknown): boolean => typeof current === 'string'
  && current.trim() !== ''
  && current !== stored;

/**
 * Names of the fields to empty because an endpoint field of the form no longer matches the stored
 * one: a stored secret must not be replayed against a new endpoint, so the user has to type it
 * again. A field still holding the write-only placeholder is a stored secret nobody touched: a value
 * typed before the endpoint changed is the user's and is left alone.
 */
export const fieldsToResetOnEndpointChange = (
  fields: CredentialContractField[] | undefined,
  values: Record<string, unknown>,
  storedValues: Record<string, unknown> | undefined,
): string[] => {
  if (!endpointFieldsOf(fields).some(name => isRedirected(values[name], storedValues?.[name]))) {
    return [];
  }
  return namesWhere(fields, 'reset_on_endpoint_change').filter(name => values[name] === DOTS);
};
