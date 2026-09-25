import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';
import SimulationConfigurationTab from '../SimulationConfigurationTab';
import ExerciseArticles from './articles/ExerciseArticles';
import SimulationTeams from './teams/SimulationTeams';
import SimulationVariables from './variables/SimulationVariables';

// The simulation authoring context (teams, variables, media pressure) surfaced
// from the hero "Configuration" action, one section per tab, so the Injects
// tab stays focused on the inject list alone (mirrors the scenario).
// Challenges are authored inside injects, so they are not configured here -
// the hero exposes a "Preview challenges page" action instead.
const SimulationConfiguration: FunctionComponent<{ initialTab?: SimulationConfigurationTab }> = ({ initialTab = SimulationConfigurationTab.TEAMS }) => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  const [tab, setTab] = useState<SimulationConfigurationTab>(initialTab);

  return (
    <Box sx={{ paddingTop: 1 }}>
      <Tabs
        value={String(tab)}
        onValueChange={value => setTab(Number(value) as SimulationConfigurationTab)}
        panels="external"
        style={{ marginBottom: 16 }}
      >
        <TabsList>
          <TabsTrigger value={String(SimulationConfigurationTab.TEAMS)}>{t('Teams')}</TabsTrigger>
          <TabsTrigger value={String(SimulationConfigurationTab.VARIABLES)}>{t('Variables')}</TabsTrigger>
          <TabsTrigger value={String(SimulationConfigurationTab.MEDIA_PRESSURE)}>{t('Media pressure')}</TabsTrigger>
        </TabsList>
      </Tabs>
      {tab === SimulationConfigurationTab.TEAMS && <SimulationTeams exerciseTeamsUsers={exercise.exercise_teams_users ?? []} />}
      {tab === SimulationConfigurationTab.VARIABLES && <SimulationVariables />}
      {tab === SimulationConfigurationTab.MEDIA_PRESSURE && <ExerciseArticles />}
    </Box>
  );
};

export default SimulationConfiguration;
