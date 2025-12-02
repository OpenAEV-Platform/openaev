import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { getExerciseTeamsSelector } from '../../../../../actions/selectors';
import { useFormatter } from '../../../../../components/i18n';
import { useSelectorHelper } from '../../../../../store';
import { type Exercise, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForExercise from './teamContextForExercise';

interface Props { exerciseTeamsUsers: Exercise['exercise_teams_users'] }

const SimulationTeams: FunctionComponent<Props> = ({ exerciseTeamsUsers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const theme = useTheme();

  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const teamsStore = useSelectorHelper(state => getExerciseTeamsSelector(exerciseId, state));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
  });

  return (
    <TeamContext.Provider value={teamContextForExercise(exerciseId, exerciseTeamsUsers)}>
      <div style={{
        display: 'grid',
        gap: `0 ${theme.spacing(3)}`,
        gridTemplateRows: 'min-content 1fr',
      }}
      >
        <Typography variant="h4">
          {t('Teams')}
          {permissions.canManage
            && (
              <UpdateTeams
                addedTeamIds={(teamsStore as Team[]).map((team: Team) => team.team_id)}
              />
            )}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <ContextualTeams teams={teamsStore as Team[]} />
        </Paper>
      </div>
    </TeamContext.Provider>
  );
};

export default SimulationTeams;
