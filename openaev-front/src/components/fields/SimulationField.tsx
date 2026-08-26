import { Kayaking } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
import {
  type CSSProperties,
  type FunctionComponent,
  type HTMLAttributes,
  type KeyboardEventHandler,
  useEffect,
} from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { fetchExercises } from '../../actions/Exercise';
import type { ExercisesHelper } from '../../actions/exercises/exercise-helper';
import { useHelper } from '../../store';
import type { ExerciseSimple } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import type { Option } from '../../utils/Option';
import { SIMULATIONS } from '../common/queryable/filter/constants';
import useSearchOptions, { type SearchOptionsConfig } from '../common/queryable/filter/useSearchOptions';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  className?: string;
  value?: string | undefined;
  onChange?: (value: string | undefined) => void;
  required?: boolean;
  error?: boolean;
  searchOptionsConfig?: SearchOptionsConfig;
  multiple?: boolean;
  /**
   * Binds the field to the surrounding react-hook-form context: the form value is
   * a list of simulation ids. Requires `name` and a `FormProvider` ancestor.
   */
  useForm?: boolean;
  placeholder?: string;
  name?: string;
  disabled?: boolean;
  style?: CSSProperties;
  onKeyDown?: KeyboardEventHandler;
}

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

const SimulationField: FunctionComponent<Props> = ({
  label,
  value,
  onChange,
  className = '',
  required = false,
  error = false,
  searchOptionsConfig,
  multiple = false,
  useForm = false,
  placeholder = '',
  name,
  disabled = false,
  style,
  onKeyDown,
}) => {
  const { options, searchOptions } = useSearchOptions();
  const { classes } = useStyles();
  const formContext = useFormContext();
  const dispatch = useAppDispatch();

  const finalSearchOptionsConfig = {
    filterKey: searchOptionsConfig?.filterKey ?? SIMULATIONS,
    contextId: searchOptionsConfig?.contextId,
    defaultValues: searchOptionsConfig?.defaultValues,
  };

  useEffect(() => {
    if (!multiple) {
      searchOptions(finalSearchOptionsConfig, '');
    }
  }, []);

  const exercises = useHelper((helper: ExercisesHelper) => helper.getExercises());
  useDataLoader(() => {
    if (multiple && useForm) {
      dispatch(fetchExercises());
    }
  });

  const exerciseOptions: Option[] = (exercises ?? []).map((exercise: ExerciseSimple) => ({
    id: exercise.exercise_id,
    label: exercise.exercise_name,
  }));

  if (multiple && useForm) {
    return (
      <Controller
        name={name ?? ''}
        control={formContext.control}
        render={({ field: { value: fieldValue, onChange: fieldOnChange }, fieldState: { error: fieldError } }) => (
          <MuiAutocomplete
            multiple
            fullWidth
            size="small"
            selectOnFocus
            autoHighlight
            clearOnBlur={false}
            clearOnEscape={false}
            disableClearable
            disabled={disabled}
            onKeyDown={onKeyDown}
            slotProps={{ paper: { elevation: 2 } }}
            options={exerciseOptions}
            value={exerciseOptions.filter(option => ((fieldValue as string[]) ?? []).includes(option.id))}
            onChange={(_, newValue) => fieldOnChange(newValue.map(option => option.id))}
            getOptionLabel={option => option.label}
            isOptionEqualToValue={(option, val) => option.id === val.id}
            renderOption={(renderProps: HTMLAttributes<HTMLLIElement>, option: Option) => (
              <Box component="li" {...renderProps} key={option.id}>
                <div className={classes.icon}>
                  <Kayaking />
                </div>
                <div className={classes.text}>{option.label}</div>
              </Box>
            )}
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={label}
                placeholder={placeholder}
                fullWidth
                style={style}
                error={!!fieldError}
                helperText={fieldError?.message}
              />
            )}
            classes={{ clearIndicator: classes.autoCompleteIndicator }}
          />
        )}
      />
    );
  }

  return (
    <AutocompleteField
      label={label}
      className={className}
      value={value}
      onChange={newValue => onChange?.(newValue)}
      required={required}
      error={error}
      options={options}
      onInputChange={(search: string) => searchOptions(finalSearchOptionsConfig, search)}
    />
  );
};

export default SimulationField;
