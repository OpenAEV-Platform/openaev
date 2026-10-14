import { type PaletteMode } from '@mui/material';
import * as R from 'ramda';

import { type Team } from '../../../utils/api-types';
import { colors } from '../../../utils/Charts';

export const getTeamsColors: (teams: Team[], paletteMode: PaletteMode) => Record<string, string> = (teams: Team[], paletteMode: PaletteMode) => {
  const mapIndexed = R.addIndex(R.map);
  return R.pipe(
    mapIndexed((a: Team, index: number) => [
      a.team_id,
      colors(paletteMode === 'dark' ? 400 : 600)[index],
    ]),
    R.fromPairs,
  )(teams);
};
