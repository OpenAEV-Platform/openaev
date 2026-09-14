import { Kayaking } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { fetchExercises } from '../../actions/Exercise';
import { type ExercisesHelper } from '../../actions/exercises/exercise-helper';
import { useHelper } from '../../store';
import { type ExerciseSimple } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';
import EntityMultiSelectFieldController, { type EntityMultiSelectFieldControllerProps } from './EntityMultiSelectFieldController';

type Props = Omit<EntityMultiSelectFieldControllerProps, 'options' | 'icon'>;

const SimulationFieldController: FunctionComponent<Props> = (props) => {
  const dispatch = useAppDispatch();

  const exercises = useHelper((helper: ExercisesHelper) => helper.getExercises());
  useDataLoader(() => {
    dispatch(fetchExercises());
  });

  const options: Option[] = (exercises ?? []).map((exercise: ExerciseSimple) => ({
    id: exercise.exercise_id,
    label: exercise.exercise_name,
  }));

  return (
    <EntityMultiSelectFieldController
      {...props}
      options={options}
      icon={<Kayaking />}
    />
  );
};

export default SimulationFieldController;
