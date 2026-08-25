import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { Kayaking } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import type { AxiosResponse } from 'axios';
import {
  type CSSProperties,
  type FunctionComponent,
  type KeyboardEventHandler,
  useEffect, useState,
} from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchScenarios, searchScenarioAsOption } from '../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../actions/scenarios/scenario-helper';
import { useHelper } from '../../store';
import type { Scenario } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import type { GroupOption, Option } from '../../utils/Option';
import Autocomplete from '../Autocomplete';
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
  style,
  onKeyDown,
  onValuesChange,
  values = [],
}) => {
  const { options, searchOptions } = useSearchOptions();
  const { classes } = useStyles();
  const theme = useTheme();
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
        renderOption={(option: Option) => (
          <>
            <div className={classes.icon}>
              <Kayaking />
            </div>
            <div className={classes.text}>{option.label}</div>
          </>
        )}
      />
    );
  }

  if (multiple && !useForm) {
    return (
      <div style={{ marginTop: theme.spacing(2) }}>
        <Combobox<Option>
          multiple
          open={open}
          onOpenChange={(next, meta) => {
            // MUI reported `selectOption` and the site swallowed it so the panel
            // stayed open across picks; `meta.cause` states the same thing.
            if (!next && meta.cause === 'select') {
              return;
            }
            setOpen(next);
          }}
          options={multipleOptions}
          loading={loading}
          value={values}
          onValueChange={newValue => onValuesChange?.(newValue as Option[])}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, val) => option.id === val.id}
        >
          <ComboboxLabel>{label}</ComboboxLabel>
          <ComboboxField>
            <ComboboxChips />
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxClear />
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
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
