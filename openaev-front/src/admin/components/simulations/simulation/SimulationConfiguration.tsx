import { Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';
import ExerciseArticles from './articles/ExerciseArticles';
import SimulationTeams from './teams/SimulationTeams';
import SimulationVariables from './variables/SimulationVariables';

// Tab indices are surfaced as a named enum (rather than magic numbers) so deep-link callers
// (e.g. the "manage custom variables" link in AvailableVariablesDialog) can target a specific
// tab without duplicating/guessing its position, and stay correct if tabs are ever reordered.
export enum SimulationConfigurationTab {
  TEAMS = 0,
  VARIABLES = 1,
  MEDIA_PRESSURE = 2,
}

export const SIMULATION_CONFIGURATION_QUERY_PARAM = 'config';
export const SIMULATION_CONFIGURATION_VARIABLES_QUERY_VALUE = 'variables';
export const buildSimulationVariablesConfigurationUrl = (exerciseId: string, returnPath: string = `/admin/simulations/${exerciseId}/injects`) => (
  `${returnPath}${returnPath.includes('?') ? '&' : '?'}${SIMULATION_CONFIGURATION_QUERY_PARAM}=${SIMULATION_CONFIGURATION_VARIABLES_QUERY_VALUE}`
);

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
        </Tabs>
      </Box>
      {tab === SimulationConfigurationTab.TEAMS && <SimulationTeams exerciseTeamsUsers={exercise.exercise_teams_users ?? []} />}
      {tab === SimulationConfigurationTab.VARIABLES && <SimulationVariables />}
      {tab === SimulationConfigurationTab.MEDIA_PRESSURE && <ExerciseArticles />}
    </Box>
  );
};

export default SimulationConfiguration;
