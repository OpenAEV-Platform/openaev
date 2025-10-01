import { useParams } from 'react-router';

import { searchDistinctFindingsForScenarios, searchFindingsForScenarios } from '../../../../../actions/findings/finding-actions';
import { SIMULATION } from '../../../../../constants/Entities';
import type { RelatedFindingOutput, Scenario, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingContextLink from '../../../findings/FindingContextLink';
import FindingList from '../../../findings/FindingList';

const ScenarioFindings = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const additionalFilterNames = [
    'finding_inject_id',
    'finding_simulation',
  ];

  const search = (input: SearchPaginationInput) => {
    return searchFindingsForScenarios(scenarioId, input);
  };
  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForScenarios(scenarioId, input);
  };

  const additionalHeaders = [
    {
      field: 'finding_simulation',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
  ];

  return (
    <FindingList
      filterLocalStorageKey="scenario-findings"
      searchFindings={search}
      searchDistinctFindings={searchDistinct}
      additionalHeaders={additionalHeaders}
      additionalFilterNames={additionalFilterNames}
      contextId={scenarioId}
    />
  );
};
export default ScenarioFindings;
