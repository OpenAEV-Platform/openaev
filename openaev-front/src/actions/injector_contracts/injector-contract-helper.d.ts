import { type InjectorContract } from '../../utils/api-types';

export interface InjectorContractHelper {
  getInjectorContract: (injectorContractId: string) => InjectorContract;
  getInjectorContracts: () => InjectorContract[];
  getInjectorContractsWithNoTeams: () => Contract['config']['type'][];
  getInjectorContractsMapByType: () => Record<string, Contract>;
}
