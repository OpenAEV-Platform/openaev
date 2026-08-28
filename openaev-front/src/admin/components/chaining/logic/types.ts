import type { EndpointOutput } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import type { FieldLink } from './drawer/FieldOutputLink';
import type { EventFormData } from './events/event-types';
import type { MapperConditionRow } from './logic-flow-helpers';

export interface InjectDocumentInput {
  document_id: string;
  document_attached: boolean;
}

export interface ActionDetailData {
  inject_title: string;
  inject_injector_contract: string;
  inject_injector?: string;
  inject_assets: string[];
  inject_asset_groups: string[];
  inject_teams: string[];
  inject_all_teams: boolean;
  inject_documents: InjectDocumentInput[];
  inject_content: Record<string, unknown>;
  inject_field_links: Record<string, FieldLink>;
  contract_fields: ContractElement[];
}

// Extended node data stored in React state so update forms can be pre-populated
export interface ActionMeta {
  inject_title: string;
  inject_description: string;
  inject_injector_contract?: string;
  inject_injector?: string;
  inject_payload_type?: string;
  inject_payload_collector_type?: string;
  inject_content: Record<string, unknown>;
  inject_attack_patterns_ids: string[];
  inject_kill_chain_phase_ids: string[];
  inject_assets: string[];
  inject_asset_objects: EndpointOutput[];
  inject_asset_groups: string[];
  inject_teams: string[];
  inject_all_teams: boolean;
  inject_documents: InjectDocumentInput[];
  step_condition_ids: string[];
  step_conditions: MapperConditionRow[];
  step_output_types: string[];
  contract_fields: ContractElement[];
}

export interface EventMeta {
  eventId: string;
  formData: EventFormData;
}
