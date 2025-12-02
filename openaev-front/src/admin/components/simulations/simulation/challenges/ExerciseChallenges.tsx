import { useParams } from 'react-router';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import { getExerciseChallengesSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ContextualChallenges from '../../../common/challenges/ContextualChallenges';
import { ChallengeContext, type ChallengeContextType } from '../../../common/Context';

const ExerciseChallenges = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const challenges = useSelectorHelper(state => getExerciseChallengesSelector(exerciseId, state));
  useDataLoader(() => {
    dispatch(fetchExerciseChallenges(exerciseId));
  });
  const context: ChallengeContextType = { previewChallengeUrl: () => `/admin/simulations/${exerciseId}/challenges` };
  return (
    <ChallengeContext.Provider value={context}>
      <ContextualChallenges challenges={challenges} linkToInjects={`/admin/simulations/${exerciseId}/injects`} />
    </ChallengeContext.Provider>
  );
};

export default ExerciseChallenges;
