import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import type { Exercise } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const SimulationLogic = ({ readOnly = false }: { readOnly?: boolean }) => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  // Two independent reasons the logic map is read-only: an autonomous run (the AI owns the map,
  // passed in by the router) OR a launched simulation - the map is editable only while SCHEDULED
  // (UI "Draft" / "Scheduled"), see ADR-005. While the exercise is still loading, only the incoming
  // (autonomous) flag applies.
  const launched = !!exercise && exercise.exercise_status !== 'SCHEDULED';
  const effectiveReadOnly = readOnly || launched;
  return (
    <Logic
      workflowId={exercise?.exercise_workflow_id}
      context="simulation"
      exerciseId={exerciseId}
      readOnly={effectiveReadOnly}
    />
  );
};

export default SimulationLogic;
