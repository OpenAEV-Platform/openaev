import { useParams } from 'react-router';

import { searchDistinctFindingsForScenarios } from '../../../../../actions/findings/finding-actions';
import type { Scenario, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingList from '../../../findings/FindingList';

const ScenarioFindings = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForScenarios(scenarioId, input);
  };

  return (
    <FindingList
      filterLocalStorageKey="scenario-findings"
      searchDistinctFindings={searchDistinct}
      contextId={scenarioId}
    />
  );
};
export default ScenarioFindings;
