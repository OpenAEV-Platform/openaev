// FILE TO REFERENCE ALL CUSTOM TYPES DERIVATIVE FROM API-TYPES

import type { ContractVariable } from '../actions/contract/contract';
import type { ExpectationInput } from '../admin/components/common/injects/expectations/Expectation';
import type * as ApiTypes from './api-types';

interface BaseWidgetConfiguration {
  title?: string;
  start?: string;
  end?: string;
  time_range:
    | 'DEFAULT'
    | 'ALL_TIME'
    | 'CUSTOM'
    | 'LAST_DAY'
    | 'LAST_WEEK'
    | 'LAST_MONTH'
    | 'LAST_QUARTER'
    | 'LAST_SEMESTER'
    | 'LAST_YEAR';
}

export type DateHistogramWidget = BaseWidgetConfiguration & {
  display_legend?: boolean;
  widget_configuration_type: 'temporal-histogram';
  stacked?: boolean;
  mode: 'temporal';
  date_attribute: string;
  interval: 'year' | 'month' | 'week' | 'day' | 'hour' | 'quarter';
  series: ApiTypes.DateHistogramSeries[];
};
export type FlatConfiguration = BaseWidgetConfiguration & {
  series: ApiTypes.FlatSeries[];
  widget_configuration_type: 'flat';
  date_attribute: string;
};
export type ListConfiguration = BaseWidgetConfiguration & {
  perspective: ApiTypes.ListPerspective;
  columns: string[];
  sorts?: ApiTypes.EngineSortField[];
  limit?: number;
  widget_configuration_type: 'list';
  date_attribute: string;
};
export type StructuralHistogramWidget = BaseWidgetConfiguration & {
  widget_configuration_type: 'structural-histogram';
  display_legend?: boolean;
  stacked?: boolean;
  mode: 'structural';
  field: string;
  date_attribute: string;
  series: ApiTypes.StructuralHistogramSeries[];
  limit?: number;
};
export type HistogramWidget = ApiTypes.BaseWidgetConfiguration &
  (
    | ApiTypes.BaseWidgetConfigurationWidgetConfigurationTypeMapping<'temporal-histogram', DateHistogramWidget>
    | ApiTypes.BaseWidgetConfigurationWidgetConfigurationTypeMapping<'structural-histogram', StructuralHistogramWidget>
    );
export type WidgetInput = Omit<ApiTypes.WidgetInput, 'widget_config'> & { widget_config: DateHistogramWidget | StructuralHistogramWidget | ListConfiguration | FlatConfiguration };
export type Widget = Omit<ApiTypes.Widget, 'widget_config'> & { widget_config: DateHistogramWidget | StructuralHistogramWidget | ListConfiguration | FlatConfiguration };
type PayloadCreateInputOmit = 'payload_type' | 'payload_source' | 'payload_status' | 'payload_created_at' | 'payload_id' | 'payload_updated_at' | 'payload_output_parsers';
type PayloadCreateInputMore = {
  payload_output_parsers?: (
    Omit<ApiTypes.OutputParser, 'output_parser_created_at' | 'output_parser_updated_at' | 'output_parser_id' | 'output_parser_contract_output_elements'>
    & {
      output_parser_contract_output_elements: (Omit<ApiTypes.ContractOutputElement, 'contract_output_element_created_at' | 'contract_output_element_updated_at' | 'contract_output_element_id' | 'contract_output_element_regex_groups'>
        & { contract_output_element_regex_groups: Omit<ApiTypes.RegexGroup, 'regex_group_created_at' | 'regex_group_updated_at' | 'regex_group_id'>[] })[];
    }
  )[];
};
export type PayloadCreateInput = Omit<ApiTypes.BasePayload, PayloadCreateInputOmit> & PayloadCreateInputMore &
  (
    | Omit<ApiTypes.Command, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'Command' }
    | Omit<ApiTypes.Executable, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'Executable' }
    | Omit<ApiTypes.FileDrop, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'FileDrop' }
    | Omit<ApiTypes.DnsResolution, PayloadCreateInputOmit> & PayloadCreateInputMore & { payload_type: 'DnsResolution' }
    );

export type ContractType = 'text' | 'number' | 'checkbox' | 'textarea' | 'tags' | 'select' | 'choice' | 'article' | 'challenge' | 'dependency-select' | 'attachment' | 'team' | 'expectation' | 'asset' | 'asset-group' | 'payload' | 'targeted-asset';

export interface ContractElement {
  key: string;
  mandatory: boolean;
  type: ContractType;
  label: string;
  readOnly: boolean;
  mandatoryGroups?: string[];
  mandatoryConditionField?: string;
  linkedFields?: {
    key: string;
    type: string;
  }[];
  linkedValues?: string[];
  cardinality: '1' | 'n';
  defaultValue: string | string[];
  richText?: boolean;
  tupleFilePrefix?: string;
  predefinedExpectations?: ExpectationInput[];
  dependencyField?: string;
  choices?: Record<string, string> | {
    label: string;
    value: string;
    information: string;
  }[];
}

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

