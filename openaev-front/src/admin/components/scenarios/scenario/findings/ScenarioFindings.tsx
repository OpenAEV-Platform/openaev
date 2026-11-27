import { useParams } from 'react-router';

import { searchFindingsForScenarios } from '../../../../../actions/findings/finding-actions';
import type { Scenario, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingList from '../../../findings/FindingList';

const ScenarioFindings = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const searchInContext = (input: SearchPaginationInput) => {
    return searchFindingsForScenarios(scenarioId, input);
  };

  return (
    <FindingList
      filterLocalStorageKey="scenario-findings"
      searchFindings={searchInContext}
      contextId={scenarioId}
    />
  );
};
export default ScenarioFindings;
