import { useParams } from 'react-router';

import { searchFindingsForSimulations } from '../../../../../actions/findings/finding-actions';
import type { Exercise, FindingOutput, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingList from '../../../findings/FindingList';

const SimulationFindings = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const additionalFilterNames = [
    'finding_inject_id',
  ];

  const search = (input: SearchPaginationInput) => {
    return searchFindingsForSimulations(exerciseId, input);
  };
  const additionalHeaders = [
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (finding: FindingOutput) => finding.finding_inject?.inject_title,
    },
  ];

  return (
    <FindingList
      filterLocalStorageKey="simulation-findings"
      searchFindings={search}
      additionalHeaders={additionalHeaders}
      additionalFilterNames={additionalFilterNames}
      contextId={exerciseId}
    />
  );
};
export default SimulationFindings;
