import { Kayaking } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Chip, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { AxiosResponse } from 'axios';
import { type CSSProperties, type HTMLAttributes, type KeyboardEventHandler, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchScenarios, searchScenarioAsOption } from '../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../actions/scenarios/scenario-helper';
import { useHelper } from '../store';
import { type Scenario } from '../utils/api-types';
import { useAppDispatch } from '../utils/hooks';
import useDataLoader from '../utils/hooks/useDataLoader';
import Autocomplete from './Autocomplete';

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

export interface ScenarioOption {
  id: string;
  label: string;
}

interface ScenarioFieldProps {
  name?: string;
  label: string;
  placeholder?: string;
  style?: CSSProperties;
  onKeyDown?: KeyboardEventHandler;
  multiple?: boolean;
  value?: ScenarioOption[];
  onChange?: (selected: ScenarioOption[]) => void;
}

const ScenarioField = ({ name, onKeyDown, style, label = 'Scenarios', placeholder, multiple = false, value, onChange }: ScenarioFieldProps) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const [options, setOptions] = useState<ScenarioOption[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const scenarios = useHelper((helper: ScenariosHelper) => helper.getScenarios());
  useDataLoader(() => {
    if (!multiple) {
      dispatch(fetchScenarios());
    }
  });

  const scenarioOptions: ScenarioOption[] = (scenarios ?? []).map((scenario: Scenario) => ({
    id: scenario.scenario_id,
    label: scenario.scenario_name,
  }));

  useEffect(() => {
    if (multiple) {
      setLoading(true);
      searchScenarioAsOption()
        .then((response: AxiosResponse<ScenarioOption[]>) => setOptions(response.data))
        .finally(() => setLoading(false));
    }
  }, []);

  if (multiple) {
    return (
      <MuiAutocomplete
        multiple
        open={open}
        onOpen={() => setOpen(true)}
        onClose={(_, reason) => {
          if (reason === 'selectOption') return;
          setOpen(false);
        }}
        options={options}
        loading={loading}
        value={value}
        onChange={(_, newValue) => onChange?.(newValue)}
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
    <Autocomplete
      variant="standard"
      size="small"
      name={name}
      fullWidth
      multiple
      label={label}
      placeholder={placeholder}
      options={scenarioOptions}
      style={style}
      onKeyDown={onKeyDown}
      renderOption={(renderProps: HTMLAttributes<HTMLLIElement>, option: ScenarioOption) => (
        <Box component="li" {...renderProps} key={option.id}>
          <div className={classes.icon}>
            <Kayaking />
          </div>
          <div className={classes.text}>{option.label}</div>
        </Box>
      )}
      classes={{ clearIndicator: classes.autoCompleteIndicator }}
    />
  );
};

export default ScenarioField;
