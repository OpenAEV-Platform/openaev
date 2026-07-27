import { type SecretProvider, type SecretsProviderOutput } from '../../utils/api-types';

export interface SecretsProviderHelper {
  getSecretsProvider: (secretsProviderId: string) => SecretsProviderOutput;
  getSecretsProvidersIncludingPending: () => SecretProvider[];
}
