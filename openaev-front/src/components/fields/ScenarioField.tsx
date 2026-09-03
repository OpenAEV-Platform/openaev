import { Autocomplete as MuiAutocomplete, Chip, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { AxiosResponse } from 'axios';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchScenarioAsOption } from '../../actions/scenarios/scenario-actions';
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
  values?: Option[];
  onValuesChange?: (value: Option[]) => void;
}

/**
 * Standalone scenario selector, driven by its own `value` / `onChange` props.
 * To bind a scenario selection to a react-hook-form, use ScenarioFieldController.
 */
const ScenarioField: FunctionComponent<Props> = ({
  label,
  value,
  onChange,
  className = '',
  required = false,
  error = false,
  defaultOptions = [],
  multiple = false,
  onValuesChange,
  values = [],
}) => {
  const { options, searchOptions } = useSearchOptions();
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const [multipleOptions, setMultipleOptions] = useState<Option[]>([]);
  const [loading, setLoading] = useState(false);
  const searchOptionsConfig = {
    filterKey: SCENARIOS,
    defaultValues: defaultOptions,
  };

  useEffect(() => {
    if (multiple) {
      setLoading(true);
      searchScenarioAsOption()
        .then((response: AxiosResponse<Option[]>) => setMultipleOptions(response.data))
        .finally(() => setLoading(false));
    } else {
      searchOptions(searchOptionsConfig, '');
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
