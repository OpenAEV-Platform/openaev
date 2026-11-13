import DOTS from '../../../../constants/Strings';
import type { CredentialInput } from '../../../../utils/api-types';

interface EndpointGuard {
  /** Fields holding a URL or URI: pointing one of them elsewhere sends the stored secrets there. */
  endpointFields: string[];
  /** Write-only fields the backend requires to be sent again once an endpoint field changed. */
  fieldsToReset: string[];
}

// Mirrors the backend handlers: a stored secret must not be replayed against a new endpoint, so
// the user has to type it again. Azure managed identity has nothing to reset.
const ENDPOINT_GUARDS: Partial<Record<CredentialInput['credential_auth_method'], EndpointGuard>> = {
  AWS_ACCESS_KEY: {
    endpointFields: ['aws_endpoint_url'],
    fieldsToReset: ['aws_secret_access_key', 'aws_session_token'],
  },
  AWS_ASSUME_ROLE: {
    endpointFields: ['aws_endpoint_url'],
    fieldsToReset: ['aws_external_id', 'aws_source_profile_secret_access_key'],
  },
  AZURE_SERVICE_PRINCIPAL: {
    endpointFields: ['azure_authority_host_uri'],
    fieldsToReset: ['azure_client_secret'],
  },
  GCP_SERVICE_ACCOUNT: {
    endpointFields: ['gcp_custom_resource_manager_uri', 'gcp_custom_projects_uri'],
    fieldsToReset: ['gcp_private_key_json'],
  },
  GCP_OAUTH2: {
    endpointFields: ['gcp_custom_token_uri', 'gcp_custom_projects_uri', 'gcp_custom_iam_uri'],
    fieldsToReset: ['gcp_oauth_client_secret', 'gcp_oauth_refresh_token', 'gcp_custom_oauth_access_token'],
  },
};

export const endpointFieldsOf = (
  authMethod: CredentialInput['credential_auth_method'] | undefined,
): string[] => (authMethod ? ENDPOINT_GUARDS[authMethod]?.endpointFields ?? [] : []);

// Same rule as the backend: only a non-blank value different from the stored one is a redirection,
// clearing the field falls back to the official endpoint.
const isRedirected = (current: unknown, stored: unknown): boolean => typeof current === 'string'
  && current.trim() !== ''
  && current !== stored;

/**
 * Names of the fields to empty because an endpoint field of the form no longer matches the stored
 * one. A field still holding the write-only placeholder is a stored secret nobody touched: a value
 * typed before the endpoint changed is the user's and is left alone.
 */
export const fieldsToResetOnEndpointChange = (
  authMethod: CredentialInput['credential_auth_method'] | undefined,
  values: Record<string, unknown>,
  storedValues: Record<string, unknown> | undefined,
): string[] => {
  const guard = authMethod ? ENDPOINT_GUARDS[authMethod] : undefined;
  if (!guard || !guard.endpointFields.some(name => isRedirected(values[name], storedValues?.[name]))) {
    return [];
  }
  return guard.fieldsToReset.filter(name => values[name] === DOTS);
};
