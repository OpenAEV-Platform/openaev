import { type FunctionComponent, useMemo } from 'react';
import { useParams } from 'react-router';

import { fetchExercise } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise as ExerciseType, type SimulationDetails } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../../common/Context';
import injectContextForExercise from '../ExerciseContext';
import SimulationShell from '../SimulationShell';
import SimulationInjectCreation from './SimulationInjectCreation';

// Standalone route wrapper for the simulation inject-creation flow. It must be a
// top-level route (not nested under the simulation Index) because the sibling
// `injects/:injectId/*` route would otherwise capture `injects/create` with
// injectId="create" and mount the inject detail view (which loads forever). This
// wrapper reprovides the same contexts the simulation Index gives the create
// flow and renders inside the shared SimulationShell (breadcrumbs + header +

// tabs), so the experience matches the scenario inject-creation flow: the user
// keeps the full simulation context on screen while picking a contract.
const SimulationInjectCreationRoute: FunctionComponent = () => {
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) as SimulationDetails }));
  useDataLoader(() => {
    dispatch(fetchExercise(exerciseId));
  });

  const injectContext = injectContextForExercise(exercise);
  const permissions = useSimulationPermissions(exerciseId, exercise);
  const permissionsContext: PermissionsContextType = useMemo(() => ({
    permissions,
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  }), [permissions]);
  const documentContext: DocumentContextType = useMemo(() => ({
    onInitDocument: () => ({
      document_tags: [],
      document_scenarios: [],
      document_exercises: exercise
        ? [{
            id: exercise.exercise_id,
            label: exercise.exercise_name,
          }]
        : [],
    }),
  }), [exercise?.exercise_id, exercise?.exercise_name]);

  if (!exercise) {
    return <Loader />;
  }

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <InjectContext.Provider value={injectContext}>
          <SimulationShell exercise={exercise}>
            <SimulationInjectCreation />
          </SimulationShell>
        </InjectContext.Provider>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

export default SimulationInjectCreationRoute;
