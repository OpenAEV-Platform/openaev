import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Scenario } from '../../../../utils/api-types';
import ScenarioArticles from './articles/ScenarioArticles';
import ScenarioChallenges from './challenges/ScenarioChallenges';
import ScenarioTeams from './teams/ScenarioTeams';
import ScenarioVariables from './variables/ScenarioVariables';

const ScenarioDefinition = () => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  // Fetching data
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />
      <ScenarioVariables />
      <div style={{ gridColumn: '1 / span 2' }}>
        <ScenarioArticles />
      </div>
      <div style={{ gridColumn: '1 / span 2' }}>
        <Typography variant="h4" style={{ float: 'left' }}>
          {t('Used challenges (in injects)')}
        </Typography>
        <ScenarioChallenges />
      </div>
    </div>
  );
};

export default ScenarioDefinition;
