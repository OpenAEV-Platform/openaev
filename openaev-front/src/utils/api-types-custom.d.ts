// FILE TO REFERENCE ALL CUSTOM TYPES DERIVATIVE FROM API-TYPES

import type { ContractVariable } from '../actions/contract/contract';
import type { ExpectationInput } from '../admin/components/common/injects/expectations/Expectation';
import type * as ApiTypes from './api-types';

type PayloadCreateInputOmit
  = 'payload_type'
    | 'payload_source'
    | 'payload_status'
    | 'payload_created_at'
    | 'payload_id'
    | 'payload_updated_at'
    | 'payload_output_parsers';
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

export type ContractType
  = 'text'
    | 'number'
    | 'checkbox'
    | 'textarea'
    | 'tags'
    | 'select'
    | 'choice'
    | 'article'
    | 'challenge'
    | 'dependency-select'
    | 'attachment'
    | 'team'
    | 'expectation'
    | 'asset'
    | 'asset-group'
    | 'payload'
    | 'targeted-asset' | 'password';

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
  dataSource?: DataSource | null;
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

export type WidgetInput = Omit<ApiTypes.WidgetInput, 'widget_config'> & {
  widget_config:
    | ApiTypes.DateHistogramWidget & {
      mode: 'temporal';
      widget_configuration_type: 'temporal-histogram';
    }
    | ApiTypes.FlatConfiguration & { widget_configuration_type: 'flat' }
    | ApiTypes.ListConfiguration & { widget_configuration_type: 'list' }
    | ApiTypes.StructuralHistogramWidget & {
      mode: 'structural';
      widget_configuration_type: 'structural-histogram';
    }
    | ApiTypes.AverageConfiguration & { widget_configuration_type: 'average' };
};

export type WidgetInputWithoutLayout = Omit<WidgetInput, 'widget_layout'>;

// -- Data source binding (chaining input mapping) --

export interface DataSource {
  input_type: string;
  input_field: string | null;
}

export interface OutputFieldDescriptor {
  key: string;
  type: string; // ContractOutputTechnicalType: text, number, boolean, object
  required: boolean;
}

export interface OutputTypeDescriptor {
  outputType: string;
  technicalType: string;
  findingCompatible: boolean;
  fields: OutputFieldDescriptor[];
}

// -- Workflow / Chaining types --

export type ScenarioType = 'time-based' | 'chaining';

export type StepFieldScope = 'LOCAL' | 'GLOBAL';

export type WorkflowStatus = 'TEMPLATE' | 'STOP' | 'RUN' | 'END';

export type StepStatus = 'TEMPLATE' | 'READY' | 'RUN' | 'END';

export type StepActionClass = 'INJECT_EXECUTION';

export type ConditionType =
  | 'AND' | 'OR' | 'EQ' | 'NEQ'
  | 'IS_NULL' | 'IS_NOT_NULL'
  | 'GT' | 'GTE' | 'LT' | 'LTE'
  | 'IN' | 'NIN'
  | 'AFTER' | 'BEFORE'
  | 'MAPPER' | 'DEPEND_ON';

export interface WorkflowCondition {
  condition_id: string;
  condition_key?: string;
  condition_field?: string;
  condition_value?: string;
  condition_type: ConditionType;
  step_from_id?: string;
  condition_parent_id?: string;
  condition_created_at?: string;
  condition_updated_at?: string;
}

export interface WorkflowStep {
  step_id: string;
  step_action_class: StepActionClass;
  step_data?: string;
  step_output_parser?: string;
  step_limit_execution: number;
  step_field_scope: StepFieldScope;
  step_status: StepStatus;
  step_created_at?: string;
  step_updated_at?: string;
  step_conditions: WorkflowCondition[];
}

export interface ScopeList {
  endpoint_ids: string[];
  manual_entries: string[];
}

export interface WorkflowScope {
  whitelist: ScopeList;
  blacklist: ScopeList;
}

export interface Workflow {
  workflow_id: string;
  workflow_status: WorkflowStatus;
  workflow_version: number;
  workflow_is_edited: boolean;
  workflow_scope?: string;
  workflow_timeout?: number;
  workflow_created_at?: string;
  workflow_updated_at?: string;
  workflow_steps: WorkflowStep[];
}
