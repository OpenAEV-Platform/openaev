import { useParams } from 'react-router';

import { searchFindingsForSimulations } from '../../../../../actions/findings/finding-actions';
import type { Exercise, SearchPaginationInput } from '../../../../../utils/api-types';
import FindingList from '../../../findings/FindingList';

const SimulationFindings = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const searchInContext = (input: SearchPaginationInput) => {
    return searchFindingsForSimulations(exerciseId, input);
  };

  return (
    <FindingList
      filterLocalStorageKey={`simulation-findings_${exerciseId}`}
      searchFindings={searchInContext}
      contextId={exerciseId}
    />
  );
};
export default SimulationFindings;
