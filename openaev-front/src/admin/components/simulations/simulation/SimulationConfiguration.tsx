import { Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';
import ExerciseArticles from './articles/ExerciseArticles';
import ExerciseChallenges from './challenges/ExerciseChallenges';
import SimulationTeams from './teams/SimulationTeams';
import SimulationVariables from './variables/SimulationVariables';

// The simulation authoring context (teams, variables, media pressure, challenges)
// surfaced from the hero "Configuration" action, one section per tab, so the
// Injects tab stays focused on the inject list alone (mirrors the scenario).
const SimulationConfiguration: FunctionComponent = () => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  const [tab, setTab] = useState(0);

  return (
    <Box sx={{ paddingTop: 1 }}>
      <Box sx={{
        borderBottom: 1,
        borderColor: 'divider',
        marginBottom: 2,
      }}
      >
        <Tabs value={tab} onChange={(_: SyntheticEvent, value: number) => setTab(value)} variant="scrollable" scrollButtons="auto">
          <Tab label={t('Teams')} />
          <Tab label={t('Variables')} />
          <Tab label={t('Media pressure')} />
          <Tab label={t('Challenges')} />
        </Tabs>
      </Box>
      {tab === 0 && <SimulationTeams exerciseTeamsUsers={exercise.exercise_teams_users ?? []} />}
      {tab === 1 && <SimulationVariables />}
      {tab === 2 && <ExerciseArticles />}
      {tab === 3 && <ExerciseChallenges />}
    </Box>
  );
};

export default SimulationConfiguration;
