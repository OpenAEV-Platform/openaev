import { test as base } from '@playwright/test';

import { type Team, type User } from '../../src/utils/api-types';
import TeamApiHelpers from '../api-helpers/TeamApiHelpers';

type TeamFixtures = {
  createTeam: (teamName: string) => Promise<Team>;
  createTeamWithMultiplePlayers: (teamName: string, playerIds: string[]) => Promise<Team>;
  createPlayer: (email: string) => Promise<User>;
};

const teamFixture = base.extend<TeamFixtures>({

  createTeam: async ({ request }, use) => {
    const apiHelpers = new TeamApiHelpers(request);
    const teamsCreated: Team[] = [];
    const createTeam = async (teamName: string) => {
      const team = await apiHelpers.createTeam(teamName);
      teamsCreated.push(team);
      return team;
    };
    await use(createTeam);

    await Promise.all(teamsCreated.map(team => apiHelpers.deleteTeam(team.team_id)));
  },

  createTeamWithMultiplePlayers: async ({ request }, use) => {
    const apiHelpers = new TeamApiHelpers(request);
    const teamsCreated: Team[] = [];

    const createTeamWithPlayers = async (teamName: string, playersIds: string[]) => {
      const team = await apiHelpers.createTeam(teamName);
      teamsCreated.push(team);
      await apiHelpers.addPlayersToTeam(team.team_id, playersIds);
      return team;
    };
    await use(createTeamWithPlayers);

    await Promise.all([
      ...teamsCreated.map(team => apiHelpers.deleteTeam(team.team_id)),
    ]);
  },

  createPlayer: async ({ request }, use) => {
    const apiHelpers = new TeamApiHelpers(request);
    const players: User[] = [];

    const createPlayer = async (email: string) => {
      const player = await apiHelpers.createPlayer(email);
      players.push(player);
      return player;
    };

    await use(createPlayer);

    await Promise.all(players.map(player => apiHelpers.deletePlayer(player.user_id)));
  },

});

export default teamFixture;
