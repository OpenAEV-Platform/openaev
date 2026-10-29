import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
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
      <Tabs
        value={String(tab)}
        onValueChange={value => setTab(Number(value) as ScenarioConfigurationTab)}
        panels="external"
        style={{ marginBottom: 16 }}
      >
        <TabsList>
          <TabsTrigger value={String(ScenarioConfigurationTab.TEAMS)}>{t('Teams')}</TabsTrigger>
          <TabsTrigger value={String(ScenarioConfigurationTab.VARIABLES)}>{t('Variables')}</TabsTrigger>
          <TabsTrigger value={String(ScenarioConfigurationTab.MEDIA_PRESSURE)}>{t('Media pressure')}</TabsTrigger>
        </TabsList>
      </Tabs>
      {tab === ScenarioConfigurationTab.TEAMS && <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />}
      {tab === ScenarioConfigurationTab.VARIABLES && <ScenarioVariables />}
      {tab === ScenarioConfigurationTab.MEDIA_PRESSURE && <ScenarioArticles />}
    </Box>
  );
};

export default ScenarioConfiguration;
