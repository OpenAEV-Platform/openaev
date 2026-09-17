import { Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Scenario } from '../../../../utils/api-types';
import { ScenarioConfigurationTab } from '../ScenarioConfigurationTab';
import ScenarioArticles from './articles/ScenarioArticles';
import ScenarioTeams from './teams/ScenarioTeams';
import ScenarioVariables from './variables/ScenarioVariables';

const ScenarioConfiguration: FunctionComponent<{ initialTab?: ScenarioConfigurationTab }> = ({ initialTab = ScenarioConfigurationTab.TEAMS }) => {
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const [tab, setTab] = useState<ScenarioConfigurationTab>(initialTab);

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
      {tab === ScenarioConfigurationTab.TEAMS && <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />}
      {tab === ScenarioConfigurationTab.VARIABLES && <ScenarioVariables />}
      {tab === ScenarioConfigurationTab.MEDIA_PRESSURE && <ScenarioArticles />}
    </Box>
  );
};

export default ScenarioConfiguration;
