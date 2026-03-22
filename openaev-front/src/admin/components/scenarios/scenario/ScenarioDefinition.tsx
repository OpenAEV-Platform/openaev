import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../store';
import { type Scenario } from '../../../../utils/api-types';
import ScenarioArticles from './articles/ScenarioArticles';
import ScenarioChallenges from './challenges/ScenarioChallenges';
import ScenarioTeams from './teams/ScenarioTeams';
import ScenarioVariables from './variables/ScenarioVariables';

const ScenarioDefinition = () => {
  // Standard hooks
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  // Fetching data
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(6)} ${theme.spacing(3)}`,
      gridTemplateColumns: '1fr 1fr',
    }}
    >
      <div style={{ marginBottom: theme.spacing(3) }}>
        <ScenarioTeams scenarioTeamsUsers={scenario.scenario_teams_users} />
      </div>
      <div style={{ marginBottom: theme.spacing(3) }}>
        <ScenarioVariables />
      </div>
      <div style={{ gridColumn: '1 / span 2' }}>
        <ScenarioArticles />
      </div>
      <div style={{ gridColumn: '1 / span 2' }}>
        <ScenarioChallenges />
      </div>
    </div>
  );
};

export default ScenarioDefinition;
