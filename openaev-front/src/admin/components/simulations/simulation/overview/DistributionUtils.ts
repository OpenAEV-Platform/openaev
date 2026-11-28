import { type Theme } from '@mui/material';

import { type Organization, type Team } from '../../../../../utils/api-types';
import { colors } from '../../../../../utils/Charts';

export const computeTeamsColors = (teams: Team[], theme: Theme) => Object.fromEntries(teams.map((a: Team, index: number) => [
  a.team_id,
  colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
]));

export const computeOrganizationsColors = (organizations: Organization[], theme: Theme) => Object.fromEntries(organizations.map((o: Organization, index: number) => [
  o.organization_id,
  colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
]));
