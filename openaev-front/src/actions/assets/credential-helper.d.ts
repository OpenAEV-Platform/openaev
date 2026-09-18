import { type CredentialOutput } from '../../utils/api-types';

export interface CredentialHelper {
  getCredential: (credentialId: string) => CredentialOutput;
  getCredentials: () => CredentialOutput[];
  getCredentialsMap: () => Record<string, CredentialOutput>;
}
