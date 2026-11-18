import { type Page } from '@playwright/test';

import { type CredentialOutput } from '../../../src/utils/api-types';

type CredentialStatus = NonNullable<CredentialOutput['credential_status']>;

/**
 * The stored status of a credential is only written by the connectivity check against the real
 * cloud provider, which E2E environments cannot reach with valid secrets. This mock lets the real
 * credential responses through and only forces the stored status of the given credentials.
 */
class CredentialApiMock {
  constructor(private page: Page) {}

  async mockStoredStatuses(statusByCredentialId: Record<string, CredentialStatus>) {
    const patch = (credential: CredentialOutput): CredentialOutput => {
      const status = credential.credential_id ? statusByCredentialId[credential.credential_id] : undefined;
      return status
        ? {
            ...credential,
            credential_status: status,
          }
        : credential;
    };

    await this.page.route(/\/credentials\/(search|find)$/, async (route) => {
      const response = await route.fetch();
      const json = await response.json();
      const body = Array.isArray(json)
        ? json.map(patch)
        : {
            ...json,
            content: (json.content ?? []).map(patch),
          };
      await route.fulfill({
        response,
        json: body,
      });
    });
  }
}

export default CredentialApiMock;
