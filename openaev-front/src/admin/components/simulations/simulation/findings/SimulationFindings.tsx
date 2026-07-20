import { useParams } from 'react-router';

import { searchDistinctFindingsForSimulations } from '../../../../../actions/findings/finding-actions';
import type { Exercise, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingList from '../../../findings/FindingList';

const SimulationFindings = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForSimulations(exerciseId, input);
  };

  return (
    <FindingList
      filterLocalStorageKey={`simulation-findings_${exerciseId}`}
      searchDistinctFindings={searchDistinct}
      contextId={exerciseId}
    />
  );
};
export default SimulationFindings;
