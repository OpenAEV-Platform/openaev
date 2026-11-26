import { type Theme } from '@mui/material';
import * as R from 'ramda';

import { type Organization, type Team } from '../../../../../utils/api-types';
import { colors } from '../../../../../utils/Charts';

export const computeTeamsColors = (teams: Team[], theme: Theme) => R.pipe(
  (teamsList: Team[]) => teamsList.map((a: Team, index: number) => [
    a.team_id,
    colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
  ]),
  Object.fromEntries,
)(teams);

export const computeOrganizationsColors = (organizations: Organization[], theme: Theme) => R.pipe(
  (organizationsList: Organization[]) => organizationsList.map((o: Organization, index: number) => [
    o.organization_id,
    colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
  ]),
  Object.fromEntries,
)(organizations);
