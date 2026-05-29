import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useState } from 'react';
import { useParams } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import { type Exercise } from '../../../../../utils/api-types';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForExercise from './teamContextForExercise';

interface Props { exerciseTeamsUsers: Exercise['exercise_teams_users'] }

const SimulationTeams: FunctionComponent<Props> = ({ exerciseTeamsUsers }) => {
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);
  const theme = useTheme();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const [teamsReloadCount, setTeamsReloadCount] = useState(0);

  return (
    <TeamContext.Provider value={teamContextForExercise(exerciseId, exerciseTeamsUsers)}>
      <div style={{ display: 'grid', gap: `0 ${theme.spacing(3)}`, gridTemplateRows: 'min-content 1fr' }}>
        <Typography variant="h4">
          {t('Teams')}
          {permissions.canManage && (
            <UpdateTeams onTeamsUpdated={() => setTeamsReloadCount(c => c + 1)} />
          )}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <ContextualTeams reloadContentCount={teamsReloadCount} />
        </Paper>
      </div>
    </TeamContext.Provider>
  );
};

export default SimulationTeams;
