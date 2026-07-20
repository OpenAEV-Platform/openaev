import { Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Scenario } from '../../../../utils/api-types';
import ScenarioArticles from './articles/ScenarioArticles';
import ScenarioChallenges from './challenges/ScenarioChallenges';
import ScenarioTeams from './teams/ScenarioTeams';
import ScenarioVariables from './variables/ScenarioVariables';

// The scenario authoring context (teams, variables, media pressure, challenges)
// surfaced from the hero "Configuration" action, one section per tab, so the
// Injects tab stays focused on the inject list alone.
const ScenarioConfiguration: FunctionComponent = () => {
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
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
      {tab === 0 && <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />}
      {tab === 1 && <ScenarioVariables />}
      {tab === 2 && <ScenarioArticles />}
      {tab === 3 && <ScenarioChallenges />}
    </Box>
  );
};

export default ScenarioConfiguration;
