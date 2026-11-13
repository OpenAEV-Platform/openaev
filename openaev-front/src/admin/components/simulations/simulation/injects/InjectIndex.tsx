import { type FunctionComponent, Suspense, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchInjectHeroOutput, fetchInjectResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { fetchExercise } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import Loader from '../../../../../components/Loader';
import NotFound from '../../../../../components/NotFound';
import { useHelper } from '../../../../../store';
import { type Exercise as ExerciseType, type InjectHeroOutput, type InjectResultOverviewOutput } from '../../../../../utils/api-types';
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
  injectHeroResult: InjectHeroOutput;
}> = ({
  exercise,
  injectResult,
  injectHeroResult,
}) => {
  const permissionsContext: PermissionsContextType = {
    permissions: useSimulationPermissions(exercise.exercise_id, exercise),
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  };

  const [injectResultOverviewOutput, setInjectResultOverviewOutput] = useState<InjectResultOverviewOutput>(injectResult);
  const [injectHeroOutput, setInjectHeroOutput] = useState<InjectHeroOutput>(injectHeroResult);

  // Refreshed alongside the overview on every update so the hero (which also carries
  // launched_by, only present on InjectHeroOutput) never lags behind it.
  const updateInjectResultOverviewOutput = (newData: InjectResultOverviewOutput) => {
    setInjectResultOverviewOutput(newData);
    fetchInjectHeroOutput(newData.inject_id).then((result: { data: InjectHeroOutput }) => {
      setInjectHeroOutput(result.data);
    });
  };

  return (
    <InjectResultOverviewOutputContext.Provider value={{
      injectResultOverviewOutput,
      updateInjectResultOverviewOutput,
    }}
    >
      <PermissionsContext.Provider value={permissionsContext}>
        <InjectIndexHeader injectResultOverview={injectResultOverviewOutput} injectHero={injectHeroOutput} exercise={exercise} />
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
  const [injectHeroOutput, setInjectHeroOutput] = useState<InjectHeroOutput>();
  const [loadingInject, setLoadingInject] = useState(true);

  useEffect(() => {
    setLoadingInject(true);
    Promise.all([
      fetchInjectResultOverviewOutput(injectId),
      fetchInjectHeroOutput(injectId),
    ])
      .then(([overviewResult, heroResult]: [{ data: InjectResultOverviewOutput }, { data: InjectHeroOutput }]) => {
        setInjectResultOverviewOutput(overviewResult.data);
        setInjectHeroOutput(heroResult.data);
      })
      // 404 (deleted inject - e.g. its injector contract or phishing landing page was removed)
      // or 403 masked as 404: swallow the rejection, the render below decides what to show.
      .catch(() => {
        setInjectResultOverviewOutput(undefined);
        setInjectHeroOutput(undefined);
      })
      .finally(() => setLoadingInject(false));
  }, [injectId]);

  if (loadingInject) {
    return <Loader />;
  }

  // The fetch completed without a result: the inject does not exist (anymore) or is not
  // accessible. Render a proper not-found instead of the previous infinite loader (#4844).
  if (!injectResultOutput || !injectHeroOutput) {
    return <NotFound />;
  }

  if (exercise) {
    return <InjectIndexComponent exercise={exercise} injectResult={injectResultOutput} injectHeroResult={injectHeroOutput} />;
  }
  return <Loader />;
};

export default InjectIndex;
