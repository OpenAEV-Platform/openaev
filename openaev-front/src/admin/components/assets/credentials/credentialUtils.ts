import DOTS from '../../../../constants/Strings';
import type { CredentialFullOutput, CredentialInput } from '../../../../utils/api-types';

const convertCredentialFullOutputToCredentialInput = (credential: CredentialFullOutput): CredentialInput => {
  return {
    credential_name: credential.credential_name ?? '',
    credential_description: credential.credential_description ?? '',
    credential_type: credential.credential_type,
    credential_auth_method: credential.credential_auth_method,
    credential_tags: credential.credential_tags_ids ?? [],
    credential_username: credential.credential_username ?? '',
    credential_password: credential.credential_username ? DOTS : '',
    credential_hash_algorithm: credential.credential_hash_algorithm ?? undefined,
    credential_hash: credential.credential_hash_algorithm ? DOTS : '',
  };
};

export default convertCredentialFullOutputToCredentialInput;
