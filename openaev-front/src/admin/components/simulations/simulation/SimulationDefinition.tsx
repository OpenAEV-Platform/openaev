import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import type { ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';
import ExerciseArticles from './articles/ExerciseArticles';
import ExerciseChallenges from './challenges/ExerciseChallenges';
import SimulationTeams from './teams/SimulationTeams';
import SimulationVariables from './variables/SimulationVariables';

const SimulationDefinition = () => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  // Fetching data
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <SimulationTeams exerciseTeamsUsers={exercise.exercise_teams_users ?? []} />
      <SimulationVariables />
      <div style={{ gridColumn: '1 / span 2' }}>
        <ExerciseArticles />
      </div>
      <div style={{ gridColumn: '1 / span 2' }}>
        <Typography variant="h4" style={{ float: 'left' }}>
          {t('Used challenges (in injects)')}
        </Typography>
        <ExerciseChallenges />
      </div>
    </div>
  );
};

export default SimulationDefinition;
