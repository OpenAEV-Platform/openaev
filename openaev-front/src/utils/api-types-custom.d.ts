// FILE TO REFERENCE ALL CUSTOM TYPES DERIVATIVE FROM API-TYPES

import type { ContractVariable } from '../actions/contract/contract';
import type { ExpectationInput } from '../admin/components/common/injects/expectations/Expectation';
import type * as ApiTypes from './api-types';

type PayloadCreateInputOmit = 'payload_type' | 'payload_source' | 'payload_status' | 'payload_created_at' | 'payload_id' | 'payload_updated_at' | 'payload_output_parsers';
type PayloadCreateInputMore = {
  remediations?: Record<string, DetectionRemediationInput>;
  payload_output_parsers?: (
    Omit<ApiTypes.OutputParser, 'output_parser_created_at' | 'output_parser_updated_at' | 'output_parser_id' | 'output_parser_contract_output_elements'>
    & {
      output_parser_contract_output_elements: (Omit<ApiTypes.ContractOutputElement, 'contract_output_element_created_at' | 'contract_output_element_updated_at' | 'contract_output_element_id' | 'contract_output_element_regex_groups'>
        & { contract_output_element_regex_groups: Omit<ApiTypes.RegexGroup, 'regex_group_created_at' | 'regex_group_updated_at' | 'regex_group_id'>[] })[];
    }
  )[];
};
export type PayloadCreateInput = Omit<ApiTypes.BasePayload, PayloadCreateInputOmit> & PayloadCreateInputMore
  & (
    | Omit<ApiTypes.Command, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'Command' }
    | Omit<ApiTypes.Executable, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'Executable' }
    | Omit<ApiTypes.FileDrop, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'FileDrop' }
    | Omit<ApiTypes.DnsResolution, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'DnsResolution' }
    );

export type ContractType = 'text' | 'number' | 'checkbox' | 'textarea' | 'tags' | 'select' | 'choice' | 'article' | 'challenge' | 'dependency-select' | 'attachment' | 'team' | 'expectation' | 'asset' | 'asset-group' | 'payload' | 'targeted-asset' | 'password';

export interface ChoiceItem {
  label: string;
  value: string;
  information: string;
}

export interface ContractElement {
  key: string;
  mandatory: boolean;
  type: ContractType;
  label: string;
  readOnly: boolean;
  mandatoryGroups?: string[];
  mandatoryConditionFields?: string[];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mandatoryConditionValues?: { [key: string]: any };
  visibleConditionFields?: string[];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  visibleConditionValues?: { [key: string]: any };
  linkedFields?: {
    key: string;
    type: string;
  }[];
  cardinality: '1' | 'n';
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  defaultValue: any;
  richText?: boolean;
  tupleFilePrefix?: string;
  predefinedExpectations?: ExpectationInput[];
  dependencyField?: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  choices?: Record<string, any> | ChoiceItem[];
  contractAttachment?: {
    key: string;
    label: string;
  }[];
}

export type EnhancedContractElement = ContractElement & {
  originalKey: string;
  isInjectContentType: boolean;
  isVisible: boolean;
  isInMandatoryGroup: boolean;
  mandatoryGroupContractElementLabels: string;
  writeOnly?: boolean;
  settings?: {
    rows?: number;
    required?: boolean;
  };
};

export type InjectorContractConverted = Omit<InjectorContract, 'convertedContent'> & {
  convertedContent: {
    fields: ContractElement[];
    contract_id: string;
    config: {
      type: string;
      color_dark: string;
      color_light: string;
      expose: boolean;
      label: Record<string, string>;
    };
    label: Record<string, string>;
    variables?: ContractVariable[];
  };
};
