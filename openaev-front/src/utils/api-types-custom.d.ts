// FILE TO REFERENCE ALL CUSTOM TYPES DERIVATIVE FROM API-TYPES

import type { ReactNode } from 'react';

import type { ContractVariable } from '../actions/contract/contract';
import type { ExpectationInput } from '../admin/components/common/injects/expectations/Expectation';
import type * as ApiTypes from './api-types';

type ThreatArsenalActionCreateInputOmit
  = 'action_type'
    | 'action_source'
    | 'action_status'
    | 'action_output_parsers'
    | 'command_content'
    | 'command_executor'
    | 'dns_resolution_hostname'
    | 'executable_file'
    | 'file_drop_file';

type ThreatArsenalActionCreateInputMore = {
  remediations?: Record<string, DetectionRemediationInput>;
  action_domains: string[];
  action_output_parsers?: (
      Omit<ApiTypes.OutputParser, 'output_parser_created_at' | 'output_parser_updated_at' | 'output_parser_id' | 'output_parser_contract_output_elements'>
      & {
        output_parser_contract_output_elements: (Omit<ApiTypes.ContractOutputElement, 'contract_output_element_created_at' | 'contract_output_element_updated_at' | 'contract_output_element_id' | 'contract_output_element_regex_groups'>
          & { contract_output_element_regex_groups: Omit<ApiTypes.RegexGroup, 'regex_group_created_at' | 'regex_group_updated_at' | 'regex_group_id'>[] })[];
      }
  )[];
};

export type ThreatArsenalActionCreateCustomInput = Omit<ApiTypes.ThreatArsenalActionCreateInput, ThreatArsenalActionCreateInputOmit> & ThreatArsenalActionCreateInputMore
  & (
    | {
      action_type: 'Command';
      command_executor: string;
      command_content: string;
    }
    | {
      action_type: 'Executable';
      executable_file: string;
    }
    | {
      action_type: 'FileDrop';
      file_drop_file: string;
    }
    | {
      action_type: 'DnsResolution';
      dns_resolution_hostname: string;
    }
    );

// Versioned attack-path graph delta (issue 6647): the incremental payload of
// GET /api/attack-path/simulations/{id}/graph/delta?since=<version>. Field set is a strict subset of
// the snapshot's (same DTOs, same masking), plus the version cursor and the resync signal.
// Nullability mirrors the backend record `AttackPathDeltaDTO`: the cursor, the resync flag and every
// entity list are always present (empty lists, never null), so only `counters` is optional — its
// absence is the explicit "unchanged, keep the ones you have" signal.
// TODO(#6647): drop this hand-written mirror once the delta endpoint ships in the swagger and
// `AttackPathDeltaDTO` is emitted into api-types.d.ts, then import it from there.
export interface AttackPathDeltaDTO {
  /** @format int64 */
  sinceVersion: number;
  /** @format int64 */
  newVersion: number;
  /** The cursor cannot be answered (run reset / too far behind): re-read the full snapshot. */
  resyncRequired: boolean;
  staticAttackPathFindings: ApiTypes.AttackPathNodeDTO[];
  attackPathExecutions: ApiTypes.AttackPathNodeDTO[];
  attackPathNodes: ApiTypes.AttackPathNodeDTO[];
  attackPathEdges: ApiTypes.AttackPathEdges[];
  /** Absent means "unchanged, keep the ones you have". */
  counters?: ApiTypes.AttackPathCounters;
}

// A graph snapshot as the read endpoints actually return it (issue 6647): the generated
// `AttackPathDTO` plus the graph version the snapshot reflects, which seeds the delta poll's cursor so
// the first tick does not have to replay the whole graph. Present on both the collapsed and the full
// response; 0 when the simulation has no attack-path version yet.
// TODO(#6647): drop this mirror once api-types.d.ts is regenerated with `graphVersion`.
export interface AttackPathSnapshotDTO extends ApiTypes.AttackPathDTO {
  /** @format int64 */
  graphVersion?: number;
}

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
    | 'ai-target'
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
  availableExpectations?: ExpectationInput[];
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

export type ThreatArsenalContentConverted = ThreatArsenalActionWithContentOutput & {
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

// ToolBar custom types derived from generated API types.
export type ToolBarSelectOption = {
  label: string;
  value: string;
};

export type ToolBarActionValue = ToolBarSelectOption | string;

export type ToolBarTask = {
  type: string;
  icon: () => ReactNode;
  onClick: () => void;
  title?: string;
};

export type ToolBarActionInput = {
  type?: string;
  field?: string;
  values?: ToolBarActionValue[];
  fieldType?: string;
  inputValue?: string;
  options?: Record<string, boolean>;
};

export type ToolBarBulkUpdateActionInput = Omit<ApiTypes.InjectBulkUpdateOperation, 'field' | 'operation' | 'values'> & {
  field: NonNullable<ApiTypes.InjectBulkUpdateOperation['field']>;
  type: Uppercase<NonNullable<ApiTypes.InjectBulkUpdateOperation['operation']>>;
  values: Array<{ value: string }>;
};

export type ToolBarTeamInput = Pick<ApiTypes.Team, 'team_name' | 'team_id'>;

export type ToolBarEndpointInput = Pick<ApiTypes.Endpoint, 'asset_name' | 'asset_id'>;

export type ToolBarAssetGroupInput = Pick<ApiTypes.AssetGroup, 'asset_group_name' | 'asset_group_id'>;
