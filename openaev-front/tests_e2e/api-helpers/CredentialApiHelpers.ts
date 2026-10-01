import { type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'crypto';

import { type CredentialInput, type CredentialOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

export type CredentialType = CredentialInput['credential_type'];

/** A created credential along with the secret values sent at creation, to assert they never leak. */
export interface CreatedCredential extends CredentialOutput {
  credential_id: string;
  credential_name: string;
  secrets: string[];
}

class CredentialApiHelpers {
  readonly credentialUri = tenantApiPath('/api/credentials');

  constructor(private request: APIRequestContext) {}

  /**
   * Creates a credential of the given type with fake (but well-formed) secret values.
   * The returned `secrets` are the raw values sent to the backend: they must never be
   * displayed or returned to the frontend afterwards.
   */
  async createCredential(name: string, type: CredentialType): Promise<CreatedCredential> {
    const suffix = `${Date.now()}-${randomBytes(8).toString('hex')}`;
    let input: CredentialInput;
    let secrets: string[];
    switch (type) {
      case 'CLOUD_AWS': {
        const awsSecretAccessKey = `e2eSecretAccessKey${suffix}`;
        input = {
          credential_name: name,
          credential_type: 'CLOUD_AWS',
          credential_auth_method: 'AWS_ACCESS_KEY',
          aws_default_region: 'us-east-1',
          aws_access_key_id: `AKIAE2E${suffix}`,
          aws_secret_access_key: awsSecretAccessKey,
        };
        secrets = [awsSecretAccessKey];
        break;
      }
      case 'IDENTITY': {
        const password = `e2ePassword${suffix}`;
        input = {
          credential_name: name,
          credential_type: 'IDENTITY',
          credential_auth_method: 'USERNAME_PASSWORD',
          credential_username: `e2e-user-${suffix}`,
          credential_password: password,
        };
        secrets = [password];
        break;
      }
      default:
        throw new Error(`Credential type ${type} is not supported by the E2E helpers`);
    }

    const response = await this.request.post(this.credentialUri, {
      multipart: {
        input: {
          name: 'input.json',
          mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify(input)),
        },
      },
    });
    if (!response.ok()) {
      throw new Error(
        `Failed to create credential (POST ${this.credentialUri}): `
        + `${response.status()} ${response.statusText()} - ${await response.text()}`,
      );
    }
    const credential: CredentialOutput = await response.json();
    return {
      ...credential,
      credential_id: credential.credential_id!,
      credential_name: credential.credential_name!,
      secrets,
    };
  }

  async findCredentials(credentialIds: string[]): Promise<CredentialOutput[]> {
    const response = await this.request.post(`${this.credentialUri}/find`, { data: credentialIds });
    return response.json();
  }

  async deleteCredential(id: string) {
    await this.request.delete(`${this.credentialUri}/${id}`);
  }
}

export default CredentialApiHelpers;
