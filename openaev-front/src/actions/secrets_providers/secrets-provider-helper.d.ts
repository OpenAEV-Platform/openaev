import { type SecretsProvider, type SecretsProviderOutput } from '../../utils/api-types';

export interface SecretsProviderHelper {
  getSecretsProvider: (secretsProviderId: string) => SecretsProvider;
  getSecretsProvidersIncludingPending: () => SecretsProviderOutput[];
}
