import { useTheme } from '@mui/material/styles';

import { type Team } from '../../../utils/api-types';
import { colors } from '../../../utils/Charts';

export const getTeamsColors: (teams: Team[]) => Record<string, string> = (teams: Team[]) => {
  const theme = useTheme();

  return Object.fromEntries(teams.map((a: Team, index: number) => [
    a.team_id,
    colors(theme.palette.mode === 'dark' ? 400 : 600)[index],
  ]));
};
