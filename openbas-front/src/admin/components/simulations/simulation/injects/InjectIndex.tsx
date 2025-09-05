import { type FunctionComponent, Suspense, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchInjectResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { fetchExercise } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise as ExerciseType, type InjectResultOverviewOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import AtomicTestingRoutes from '../../../atomic_testings/atomic_testing/AtomicTestingRoutes';
import { InjectResultOverviewOutputContext } from '../../../atomic_testings/InjectResultOverviewOutputContext';
import { PermissionsContext, type PermissionsContextType } from '../../../common/Context';
import InjectIndexHeader from './InjectIndexHeader';

const InjectIndexComponent: FunctionComponent<{
  exercise: ExerciseType;
  injectResult: InjectResultOverviewOutput;
}> = ({
  exercise,
  injectResult,
}) => {
  const permissionsContext: PermissionsContextType = {
    permissions: useSimulationPermissions(exercise.exercise_id, exercise),
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  };

  const [injectResultOverviewOutput, setInjectResultOverviewOutput] = useState<InjectResultOverviewOutput>(injectResult);

  const updateInjectResultOverviewOutput = (newData: InjectResultOverviewOutput) => {
    setInjectResultOverviewOutput(newData);
  };

  return (
    <InjectResultOverviewOutputContext.Provider value={{
      injectResultOverviewOutput,
      updateInjectResultOverviewOutput,
    }}
    >
      <PermissionsContext.Provider value={permissionsContext}>
        <InjectIndexHeader injectResultOverview={injectResultOverviewOutput} exercise={exercise} />
        <Suspense fallback={<Loader />}>
          <AtomicTestingRoutes injectResultOverview={injectResultOverviewOutput} />
        </Suspense>
      </PermissionsContext.Provider>
    </InjectResultOverviewOutputContext.Provider>
  );
};

const InjectIndex = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };
  const exercise = useHelper((helper: ExercisesHelper) => helper.getExercise(exerciseId));
  useDataLoader(() => {
    dispatch(fetchExercise(exerciseId));
  });
  const [injectResultOutput, setInjectResultOverviewOutput] = useState<InjectResultOverviewOutput>();

  useEffect(() => {
    fetchInjectResultOverviewOutput(injectId).then((result: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverviewOutput(result.data);
    });
  }, [injectId]);

  if (exercise && injectResultOutput) {
    return <InjectIndexComponent exercise={exercise} injectResult={injectResultOutput} />;
  }
  return <Loader />;
};

export default InjectIndex;
