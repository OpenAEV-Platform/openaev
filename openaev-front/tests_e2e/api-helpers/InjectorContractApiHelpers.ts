import { randomUUID } from 'node:crypto';

import { type APIRequestContext } from '@playwright/test';

import { type InjectorContractAddInput, type InjectorOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';
import { type CredentialType } from './CredentialApiHelpers';

// Built-in injector always registered on the platform: custom contracts attached to it
// never get executed, which keeps these UI suites independent from external injectors.
const MANUAL_INJECTOR_TYPE = 'openaev_manual';

export const CREDENTIAL_REFERENCE_FIELD_LABEL = 'Credential';

export interface CredentialReferenceFieldOptions {
  /** Compatibility constraint declared by the field; omitted means every credential is accepted. */
  credentialReferenceType?: CredentialType;
  multiple?: boolean;
  mandatory?: boolean;
}

export interface CreatedInjectorContract {
  contractId: string;
  injectorId: string;
}

class InjectorContractApiHelpers {
  readonly injectorUri = tenantApiPath('/api/injectors');
  readonly injectorContractUri = tenantApiPath('/api/injector_contracts');

  constructor(private request: APIRequestContext) {}

  async getManualInjectorId(): Promise<string> {
    const response = await this.request.get(this.injectorUri);
    const injectors: InjectorOutput[] = await response.json();
    const manualInjector = injectors.find(injector => injector.injector_type === MANUAL_INJECTOR_TYPE);
    if (!manualInjector) {
      throw new Error(`No "${MANUAL_INJECTOR_TYPE}" injector found on the platform`);
    }
    return manualInjector.injector_id;
  }

  /**
   * Creates a custom contract on the manual injector. When `credentialField` is provided, the
   * contract declares a `credential-reference` field, as the cloud injectors (e.g. Stratus) do.
   */
  async createContract(label: string, credentialField?: CredentialReferenceFieldOptions): Promise<CreatedInjectorContract> {
    const injectorId = await this.getManualInjectorId();
    const fields = credentialField
      ? [{
          key: 'credential_reference',
          label: CREDENTIAL_REFERENCE_FIELD_LABEL,
          type: 'credential-reference',
          mandatory: credentialField.mandatory ?? true,
          mandatoryGroups: null,
          linkedFields: [],
          linkedValues: [],
          cardinality: '1',
          defaultValue: [],
          multiple: credentialField.multiple ?? false,
          ...(credentialField.credentialReferenceType && { credential_reference_type: credentialField.credentialReferenceType }),
        }]
      : [];
    const contractId = randomUUID();
    const input: InjectorContractAddInput = {
      contract_id: contractId,
      injector_id: injectorId,
      contract_labels: { en: label },
      // Like the contracts registered by injectors, the content carries its own id: the inject
      // form reads the contract to save from it.
      contract_content: JSON.stringify({
        contract_id: contractId,
        label: { en: label },
        manual: false,
        fields,
        variables: [],
      }),
      contract_domains: [],
    };

    const response = await this.request.post(this.injectorContractUri, { data: input });
    if (!response.ok()) {
      throw new Error(
        `Failed to create injector contract (POST ${this.injectorContractUri}): `
        + `${response.status()} ${response.statusText()} - ${await response.text()}`,
      );
    }
    return {
      contractId,
      injectorId,
    };
  }

  async deleteContract(id: string) {
    await this.request.delete(`${this.injectorContractUri}/${id}`);
  }
}

export default InjectorContractApiHelpers;
