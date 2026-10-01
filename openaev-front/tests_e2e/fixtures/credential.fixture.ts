import { test as base } from '@playwright/test';

import { type InjectResultOverviewOutput } from '../../src/utils/api-types';
import AtomicTestingApiHelpers from '../api-helpers/AtomicTestingApiHelpers';
import CredentialApiHelpers, { type CreatedCredential, type CredentialType } from '../api-helpers/CredentialApiHelpers';
import InjectorContractApiHelpers, { type CredentialReferenceFieldOptions } from '../api-helpers/InjectorContractApiHelpers';

interface AtomicTestingOptions {
  /** Credential reference field declared by the contract; omitted means the contract has no such field. */
  credentialField?: CredentialReferenceFieldOptions;
  /** Credentials already associated with the inject. */
  secretReferenceIds?: string[];
}

type CredentialFixtures = {
  createCredential: (name: string, type: CredentialType) => Promise<CreatedCredential>;
  createAtomicTesting: (title: string, options?: AtomicTestingOptions) => Promise<InjectResultOverviewOutput>;
};

const credentialFixture = base.extend<CredentialFixtures>({

  createCredential: async ({ request }, use) => {
    const apiHelpers = new CredentialApiHelpers(request);
    const credentialsCreated: CreatedCredential[] = [];
    const createCredential = async (name: string, type: CredentialType) => {
      const credential = await apiHelpers.createCredential(name, type);
      credentialsCreated.push(credential);
      return credential;
    };

    await use(createCredential);

    await Promise.all(credentialsCreated.map(credential => apiHelpers.deleteCredential(credential.credential_id)));
  },

  // Depends on createCredential so that Playwright tears the injects down before the
  // credentials they reference.
  createAtomicTesting: async ({ request, createCredential: _createCredential }, use) => {
    const contractApiHelpers = new InjectorContractApiHelpers(request);
    const atomicTestingApiHelpers = new AtomicTestingApiHelpers(request);
    const contractIds: string[] = [];
    const atomicTestingIds: string[] = [];

    const createAtomicTesting = async (title: string, options: AtomicTestingOptions = {}) => {
      const { contractId, injectorId } = await contractApiHelpers.createContract(`E2E contract ${title}`, options.credentialField);
      contractIds.push(contractId);
      const atomicTesting = await atomicTestingApiHelpers.createAtomicTesting({
        inject_title: title,
        inject_injector_contract: contractId,
        inject_injector: injectorId,
        inject_content: {},
        inject_secret_references: options.secretReferenceIds ?? [],
      });
      atomicTestingIds.push(atomicTesting.inject_id);
      return atomicTesting;
    };

    await use(createAtomicTesting);

    await Promise.all(atomicTestingIds.map(id => atomicTestingApiHelpers.deleteAtomicTesting(id)));
    await Promise.all(contractIds.map(id => contractApiHelpers.deleteContract(id)));
  },
});

export default credentialFixture;
