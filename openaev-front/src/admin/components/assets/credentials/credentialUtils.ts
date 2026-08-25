import DOTS from '../../../../constants/Strings';
import type { CredentialFullOutput, CredentialInput } from '../../../../utils/api-types';

const convertCredentialFullOutputToCredentialInput = (credential: CredentialFullOutput): CredentialInput => {
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
    aws_role_arn: credential.credential_aws_role_arn,
    aws_source_identity_type: credential.credential_aws_source_identity_type,
    aws_source_profile_access_key_id: credential.credential_aws_source_profile_access_key_id,
    aws_source_profile_secret_access_key: credential.credential_aws_source_profile_access_key_id ? DOTS : '',
  };
};

export default convertCredentialFullOutputToCredentialInput;
