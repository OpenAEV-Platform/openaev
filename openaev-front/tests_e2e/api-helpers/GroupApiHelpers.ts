import { type APIRequestContext } from '@playwright/test';

import { tenantApiPath } from '../utils/url';

interface GroupApiOutput {
  group_id: string;
  group_name: string;
  group_users: string[];
}

class GroupApiHelpers {
  readonly groupUri = tenantApiPath('/api/groups');

  constructor(private request: APIRequestContext) {}

  async findGroupByName(name: string): Promise<GroupApiOutput> {
    const response = await this.request.post(`${this.groupUri}/search`, {
      data: {
        textSearch: name,
        page: 0,
        size: 20,
      },
    });
    if (!response.ok()) {
      throw new Error(`Failed to search group ${name}: ${response.status()} - ${await response.text()}`);
    }
    const { content } = (await response.json()) as { content: GroupApiOutput[] };
    const group = content.find(g => g.group_name === name);
    if (!group) {
      throw new Error(`Group ${name} not found in the default tenant`);
    }
    return group;
  }

  /** The endpoint replaces the whole member list, so the current members are kept. */
  async addUserToGroup(groupName: string, userId: string): Promise<void> {
    const group = await this.findGroupByName(groupName);
    if (group.group_users.includes(userId)) {
      return;
    }
    const response = await this.request.put(`${this.groupUri}/${group.group_id}/users`, { data: { group_users: [...group.group_users, userId] } });
    if (!response.ok()) {
      throw new Error(`Failed to add user to group ${groupName}: ${response.status()} - ${await response.text()}`);
    }
  }
}

export default GroupApiHelpers;
