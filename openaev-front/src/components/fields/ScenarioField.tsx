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
