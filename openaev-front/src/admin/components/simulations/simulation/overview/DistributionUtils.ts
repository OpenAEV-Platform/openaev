import { type Theme } from '@mui/material';
import * as R from 'ramda';

import { type Organization, type Team } from '../../../../../utils/api-types';
import { colors } from '../../../../../utils/Charts';

const mapIndexed = R.addIndex(R.map);
export const computeTeamsColors = (teams: Team[], theme: Theme) => R.pipe(
  mapIndexed((a: Team, index: number) => [
    a.team_id,
    colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
  ]),
  R.fromPairs,
)(teams);

export const computeOrganizationsColors = (organizations: Organization[], theme: Theme) => R.pipe(
  mapIndexed((o: Organization, index: number) => [
    o.organization_id,
    colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
  ]),
  R.fromPairs,
)(organizations);
