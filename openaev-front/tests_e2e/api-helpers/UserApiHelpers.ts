import { type APIRequestContext } from '@playwright/test';

import { tenantApiPath } from '../utils/url';

interface UserApiOutput { user_id: string }

class UserApiHelpers {
  readonly userUri = tenantApiPath('/api/users');

  constructor(private request: APIRequestContext) {}

  /** Creates the user, or attaches it to the tenant when the e-mail already exists. */
  async createOrAttachUser(email: string, password: string, firstname: string): Promise<UserApiOutput> {
    const response = await this.request.post(this.userUri, {
      data: {
        user_email: email,
        user_plain_password: password,
        user_firstname: firstname,
      },
    });
    if (!response.ok()) {
      throw new Error(`Failed to create user ${email}: ${response.status()} - ${await response.text()}`);
    }
    return (await response.json()) as UserApiOutput;
  }
}

export default UserApiHelpers;
