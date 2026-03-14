import { type InjectorContract, type InjectorContractDomainCountOutput } from '../../utils/api-types';

export interface InjectorContractHelper {
  getInjectorContract: (injectorContractId: string) => InjectorContract;
  getInjectorContracts: () => InjectorContract[];
  getInjectorContractsMap: () => Record<string, InjectorContract>;
  getInjectorContractsWithNoTeams: () => Contract['config']['type'][];
  getInjectorContractsMapByType: () => Record<string, Contract>;
  getInjectorContractsDomainCounts: () => InjectorContractDomainCountOutput[];
}
