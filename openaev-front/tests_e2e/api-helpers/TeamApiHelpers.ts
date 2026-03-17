import type { APIRequestContext } from '@playwright/test';

class TeamApiHelpers {
  readonly teamUri = '/api/teams';
  constructor(private request: APIRequestContext) {}

  async createTeam(name?: string) {
    const response = await this.request.post(this.teamUri, {
      data: {
        team_tags: [],
        team_name: name || `Team E2E ${Date.now()}`,
      },
    });
    return response.json();
  }

  async addPlayersToTeam(teamId: string, playerIds: string[]) {
    await this.request.put(`${this.teamUri}/${teamId}/players`, { data: { team_users: playerIds } });
  }

  async deleteTeam(id: string) {
    await this.request.delete(`${this.teamUri}/${id}`);
  }

  async createPlayer(email?: string) {
    const response = await this.request.post('/api/players', {
      data: {
        user_tags: [],
        user_email: email || `test${Date.now()}@test.io`,
        user_firstname: 'Emma',
      },
    });
    return response.json();
  }

  async deletePlayer(id: string) {
    await this.request.delete(`/api/players/${id}`);
  }
}

export default TeamApiHelpers;
