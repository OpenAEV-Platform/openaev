import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../../../actions/Application';
import { fetchSimulationObserverChallenges } from '../../../../../actions/challenge-action';
import { fetchSimulationPlayerDocuments } from '../../../../../actions/Document';
import { fetchExercise } from '../../../../../actions/Exercise';
import { getExerciseSelector, getSimulationChallengesReaderSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store';
import { type Exercise as ExerciseType } from '../../../../../utils/api-types';
import { useQueryParameter } from '../../../../../utils/Environment';
import { useAppDispatch } from '../../../../../utils/hooks';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import ChallengesPreview from '../../../common/challenges/ChallengesPreview';
import { PreviewChallengeContext } from '../../../common/Context';

const SimulationChallengesPreview = () => {
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const fullExercise = useSelectorHelper(state => getExerciseSelector(exerciseId, state));
  const challengesReader = useSelectorHelper(state => getSimulationChallengesReaderSelector(exerciseId, state));
  const { exercise_information: exercise, exercise_challenges: challenges } = challengesReader ?? {};
  const permissions = useSimulationPermissions(exerciseId, fullExercise);
  const [userId, challengeId] = useQueryParameter(['user', 'challenge']);

  useEffect(() => {
    dispatch(fetchMe());
    if (exerciseId) {
      dispatch(fetchExercise(exerciseId));
      dispatch(fetchSimulationObserverChallenges(exerciseId, userId));
      dispatch(fetchSimulationPlayerDocuments(exerciseId, userId));
    }
  }, [dispatch, exerciseId, userId]);

  return (
    <PreviewChallengeContext.Provider value={{
      linkToPlayerMode: `/challenges/${exerciseId}?challenge=${challengeId}&user=${userId}`,
      linkToAdministrationMode: `/admin/simulations/${exerciseId}/definition`,
      scenarioOrExercise: exercise,
    }}
    >
      <ChallengesPreview challenges={challenges} permissions={permissions} />
    </PreviewChallengeContext.Provider>
  );
};

export default SimulationChallengesPreview;
