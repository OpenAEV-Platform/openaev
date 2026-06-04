import type { ContractElement } from '../../../../utils/api-types-custom';
import type { FieldLink } from './InjectDataFieldItem';

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
