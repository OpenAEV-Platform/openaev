import DOTS from '../../../../constants/Strings';
import type { CredentialFullOutput, CredentialInput } from '../../../../utils/api-types';

/**
 * Values used to prefill the credential form.
 *
 * <p>Wider than `CredentialInput` on purpose: file-backed contract fields (the GCP service account
 * key) never belong to the JSON payload — they travel as their own multipart part — yet the form
 * still needs a value for them to render the write-only placeholder.
 */
export type CredentialFormInitialValues = CredentialInput & Record<string, unknown>;

const convertCredentialFullOutputToCredentialInput = (credential: CredentialFullOutput): CredentialFormInitialValues => {
  return {
    credential_name: credential.credential_name ?? '',
    credential_description: credential.credential_description ?? '',
    credential_type: credential.credential_type,
    credential_auth_method: credential.credential_auth_method,
    credential_tags: credential.credential_tags_ids ?? [],
    // IDENTITY
    credential_username: credential.credential_username ?? '',
    credential_password: credential.credential_username ? DOTS : '',
    credential_hash_algorithm: credential.credential_hash_algorithm ?? undefined,
    credential_hash: credential.credential_hash_algorithm ? DOTS : '',
    // AWS
    aws_default_region: credential.credential_aws_default_region,
    aws_access_key_id: credential.credential_aws_access_key_id,
    aws_secret_access_key: credential.credential_aws_access_key_id ? DOTS : '',
    aws_session_token: credential.credential_aws_session_token_present ? DOTS : '',
    aws_role_arn: credential.credential_aws_role_arn,
    aws_source_identity_type: credential.credential_aws_source_identity_type,
    aws_source_profile_access_key_id: credential.credential_aws_source_profile_access_key_id,
    aws_source_profile_secret_access_key: credential.credential_aws_source_profile_access_key_id ? DOTS : '',
    // AZURE
    azure_environment: credential.credential_azure_environment,
    azure_client_id: credential.credential_azure_client_id,
    azure_client_secret: credential.credential_azure_environment ? DOTS : '',
    azure_tenant_id: credential.credential_azure_tenant_id,
    azure_subscription_id: credential.credential_azure_subscription_id,
    // GCP: the key file never travels back, the backend only tells us whether one is stored so the
    // upload field can show a placeholder and an untouched form sends no file part at all.
    gcp_scope: credential.credential_gcp_scope,
    gcp_project_id: credential.credential_gcp_project_id,
    gcp_private_key_json: credential.credential_gcp_private_key_defined ? DOTS : '',
    // GCP OAuth2: the client id is a public identifier and comes back as is, while the client
    // secret and the refresh token never travel back — only a boolean tells us one is stored, so
    // the field shows a placeholder and an untouched form leaves the stored value alone.
    gcp_oauth_client_id: credential.credential_gcp_oauth_client_id,
    gcp_oauth_client_secret: credential.credential_gcp_oauth_client_secret_defined ? DOTS : '',
    gcp_oauth_refresh_token: credential.credential_gcp_oauth_refresh_token_defined ? DOTS : '',
  };
};

export default convertCredentialFullOutputToCredentialInput;
