import { type ContractElement } from '../../utils/api-types-custom';

export interface Contract {
  config: ContractConfig;
  context: Record<string, string>;
  contract_attack_patterns_external_ids: string[];
  contract_id: string;
  fields: ContractElement[];
  label: Record<string, string>;
  manual: boolean;
  variables: ContractVariable[];
}

export interface ContractConfig {
  color_dark?: string;
  color_light?: string;
  expose?: boolean;
  label?: Record<string, string>;
  type?: string;
}

export interface ContractVariable {
  cardinality: '1' | 'n';
  children?: ContractVariable[];
  key: string;
  label: string;
  type: 'String' | 'Object';
}
