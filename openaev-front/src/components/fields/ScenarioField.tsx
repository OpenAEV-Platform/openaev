import { Kayaking } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Chip, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { AxiosResponse } from 'axios';
import {
  type CSSProperties,
  type FunctionComponent,
  type HTMLAttributes,
  type KeyboardEventHandler,
  useEffect, useState,
} from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { fetchScenarios, searchScenarioAsOption } from '../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../actions/scenarios/scenario-helper';
import { useHelper } from '../../store';
import type { Scenario } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import type { GroupOption, Option } from '../../utils/Option';
import { SCENARIOS } from '../common/queryable/filter/constants';
import useSearchOptions from '../common/queryable/filter/useSearchOptions';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  className?: string;
  value?: string | undefined;
  onChange?: (value: string | undefined) => void;
  required?: boolean;
  error?: boolean;
  defaultOptions?: GroupOption[];
  multiple?: boolean;
  useForm?: boolean;
  placeholder?: string;
  name?: string;
  disabled?: boolean;
  style?: CSSProperties;
  onKeyDown?: KeyboardEventHandler;
  values?: Option[];
  onValuesChange?: (value: Option[]) => void;
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

const ScenarioField: FunctionComponent<Props> = ({
  label,
  value,
  onChange,
  className = '',
  required = false,
  error = false,
  defaultOptions = [],
  multiple = false,
  useForm = false,
  placeholder = '',
  name,
  disabled = false,
  style,
  onKeyDown,
  onValuesChange,
  values = [],
}) => {
  const { options, searchOptions } = useSearchOptions();
  const { classes } = useStyles();
  const theme = useTheme();
  const formContext = useFormContext();
  const [open, setOpen] = useState(false);
  const [multipleOptions, setMultipleOptions] = useState<Option[]>([]);
  const [loading, setLoading] = useState(false);
  const dispatch = useAppDispatch();
  const searchOptionsConfig = {
    filterKey: SCENARIOS,
    defaultValues: defaultOptions,
  };

  useEffect(() => {
    if (multiple && !useForm) {
      setLoading(true);
      searchScenarioAsOption()
        .then((response: AxiosResponse<Option[]>) => setMultipleOptions(response.data))
        .finally(() => setLoading(false));
    } else if (!multiple && !useForm) {
      searchOptions(searchOptionsConfig, '');
    }
  }, []);

  const scenarios = useHelper((helper: ScenariosHelper) => helper.getScenarios());
  useDataLoader(() => {
    if (multiple && useForm) {
      dispatch(fetchScenarios());
    }
  });

  const scenarioOptions: Option[] = (scenarios ?? []).map((scenario: Scenario) => ({
    id: scenario.scenario_id,
    label: scenario.scenario_name,
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
            options={scenarioOptions}
            value={scenarioOptions.filter(option => ((fieldValue as string[]) ?? []).includes(option.id))}
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

  if (multiple && !useForm) {
    return (
      <MuiAutocomplete
        multiple
        open={open}
        onOpen={() => setOpen(true)}
        onClose={(_, reason) => {
          if (reason === 'selectOption') return;
          setOpen(false);
        }}
        options={multipleOptions}
        loading={loading}
        value={values}
        onChange={(_, newValue) => onValuesChange?.(newValue)}
        getOptionLabel={option => option.label}
        isOptionEqualToValue={(option, val) => option.id === val.id}
        renderTags={(tagValue, getTagProps) =>
          tagValue.map((option, index) => (
            <Chip
              label={option.label}
              {...getTagProps({ index })}
              key={option.id}
              size="small"
            />
          ))}
        renderInput={params => (
          <TextField
            {...params}
            label={label}
            variant="outlined"
            size="small"
          />
        )}
        style={{ marginTop: theme.spacing(2) }}
      />
    );
  }

  return (
    <AutocompleteField
      label={label}
      className={className}
      value={value}
      onChange={value => onChange?.(value)}
      required={required}
      error={error}
      options={options}
      onInputChange={(search: string) => searchOptions(searchOptionsConfig, search)}
    />
  );
};

export default ScenarioField;
