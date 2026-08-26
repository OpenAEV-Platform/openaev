import { Kayaking } from '@mui/icons-material';
import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { fetchExercises } from '../../actions/Exercise';
import { type ExercisesHelper } from '../../actions/exercises/exercise-helper';
import { useHelper } from '../../store';
import { type ExerciseSimple } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';
import EntityMultiSelectField from './EntityMultiSelectField';

interface Props {
  name: string;
  label: string;
  placeholder?: string;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

// Multiple simulations selector bound to react-hook-form: the field value is a
// list of simulation ids.
const SimulationFieldController = ({ name, label, placeholder, style, disabled = false, required = false }: Props) => {
  const { control } = useFormContext();
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
    <Controller
      name={name}
      control={control}
      render={({ field: { value, onChange }, fieldState: { error } }) => (
        <EntityMultiSelectField
          label={label}
          options={options}
          fieldValue={value ?? []}
          fieldOnChange={onChange}
          icon={<Kayaking />}
          error={error}
          placeholder={placeholder}
          style={style}
          disabled={disabled}
          required={required}
        />
      )}
    />
  );
};

export default SimulationFieldController;
