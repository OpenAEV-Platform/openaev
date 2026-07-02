import type { EndpointOutput } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import type { FieldLink } from './drawer/InjectDataFieldItem';
import type { EventFormData } from './events/event-types';
import type { MapperConditionRow } from './logic-flow-helpers';

export interface LogicAction {
  id: string;
  label: string;
  injectorContract?: string;
}

export interface LogicEvent {
  id: string;
  label: string;
  conditions?: string[];
}

export interface ActionDetailData {
  inject_title: string;
  inject_injector_contract: string;
  inject_injector?: string;
  inject_assets: string[];
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
  inject_attack_patterns_ids: string[];
  inject_kill_chain_phase_ids: string[];
  inject_assets: string[];
  inject_asset_objects: EndpointOutput[];
  step_condition_ids: string[];
  step_conditions: MapperConditionRow[];
  contract_fields: ContractElement[];
}

export interface EventMeta {
  eventId: string;
  formData: EventFormData;
}
