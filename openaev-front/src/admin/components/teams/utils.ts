import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';

import { type Team } from '../../../utils/api-types';
import { colors } from '../../../utils/Charts';

// eslint-disable-next-line import/prefer-default-export
export const getTeamsColors: (teams: Team[]) => Record<string, string> = (teams: Team[]) => {
  const theme = useTheme();

  const mapIndexed = R.addIndex(R.map);
  return R.pipe(
    mapIndexed((a: Team, index: number) => [
      a.team_id,
      colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
    ]),
    R.fromPairs,
  )(teams);
};
