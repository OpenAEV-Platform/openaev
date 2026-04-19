import { Autocomplete, Checkbox, TextField } from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { FilterContext } from './context';
import { type FilterHelpers } from './FilterHelpers';
import { getSelectedOptions } from './FilterUtils';
import useSearchOptions, { type SearchOptionsConfig } from './useSearchOptions';
import wordsToExcludeFromTranslation from './WordsToExcludeFromTranslation';

interface Props {
  filter: Filter;
  helpers: FilterHelpers;
  contextId?: string; // used to give contextual information to the searchOptions function
}

export const BasicTextInput: FunctionComponent<Props> = ({
  filter,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const handleValueChange = (value: string) => {
    helpers.handleUpdateValuesById(filter.id, [value.trim()]);
  };
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      label={t(filter.key)}
      defaultValue={filter.values?.[0] ?? ''}
      autoFocus
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          handleValueChange((event.target as HTMLInputElement).value);
        }
      }}
      onBlur={(event) => {
        handleValueChange((event.target as HTMLInputElement).value);
      }}
    />
  );
};

export const BasicSelectInput: FunctionComponent<Props & { propertySchema: PropertySchemaDTO }> = ({
  filter,
  helpers,
  propertySchema,
  contextId,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const { options, setOptions, searchOptions } = useSearchOptions();
  const { defaultValues } = useContext(FilterContext);
  const selectedOptions = getSelectedOptions(options, filter.values ?? [], t);
  const mergedOptions = [
    ...selectedOptions,
    ...options.filter(option => !selectedOptions.some(selectedOption => selectedOption.id === option.id)),
  ];
  const handleSearchOptions = (search: string) => {
    const searchOptionsConfig: SearchOptionsConfig = {
      filterKey: filter.key,
      contextId: contextId,
      defaultValues: defaultValues?.get(filter.key),
    };
    searchOptions(searchOptionsConfig, search);
  };
  useEffect(() => {
    if (propertySchema.schema_property_values && propertySchema.schema_property_values?.length > 0) {
      setOptions(
        propertySchema.schema_property_values
          .map((value) => {
            const label = wordsToExcludeFromTranslation.includes(value) ? value : t(value.charAt(0).toUpperCase() + value.slice(1).toLowerCase());
            return ({
              id: value,
              label,
            });
          })
          .sort((a, b) => a.label.localeCompare(b.label)),
      );
    } else {
      handleSearchOptions('');
    }
  }, []);

  const onClick = (optionId: string) => {
    const isIncluded = filter.values?.includes(optionId);
    const newValues = isIncluded
      ? (filter.values?.filter(v => v !== optionId) ?? [])
      : [...(filter.values ?? []), optionId];
    helpers.handleUpdateValuesById(filter.id, newValues);
  };

  return (
    <Autocomplete
      selectOnFocus
      openOnFocus
      autoHighlight
      multiple
      noOptionsText={t('No available options')}
      options={mergedOptions}
      value={selectedOptions}
      inputValue={inputValue}
      renderValue={() => null}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      groupBy={(option: GroupOption | Option) => 'group' in option ? option.group : ''}
      getOptionLabel={option => option.label ?? ''}
      onInputChange={(_, search, reason) => {
        if (reason === 'reset') {
          return;
        }
        setInputValue(search);
        handleSearchOptions(search);
      }}
      renderInput={paramsInput => (
        <TextField
          {...paramsInput}
          label={t(propertySchema.schema_property_name)}
          variant="outlined"
          size="small"
        />
      )}
      loading
      renderOption={(props, option) => {
        const checked = filter.values?.includes(option.id);
        return (
          <li
            {...props}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.stopPropagation();
              }
            }}
            key={option.id}
            onClick={() => onClick(option.id)}
            style={{
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              padding: 0,
              margin: 0,
            }}
          >
            <Checkbox checked={checked} />
            <span style={{ padding: '0 4px 0 4px' }}>{option.label}</span>
          </li>
        );
      }}
    />
  );
};

export const BasicFilterDate: FunctionComponent<Props> = ({
  filter,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const handleValueChange = (date: Date) => {
    helpers.handleUpdateValuesById(filter.id, [date.toISOString()]);
  };
  return (
    <DateTimePicker
      label={t(filter.key)}
      onChange={(date) => {
        if (date) {
          handleValueChange(date);
        }
      }}
      slotProps={{
        textField: {
          variant: 'outlined',
          fullWidth: true,
        },
      }}
    />
  );
};

export const FilterChipPopoverInput: FunctionComponent<Props & { propertySchema: PropertySchemaDTO }> = ({
  propertySchema,
  filter,
  helpers,
  contextId,
}) => {
  const choice = () => {
    // Date field
    if (propertySchema.schema_property_type.includes('instant')) {
      return (<BasicFilterDate filter={filter} helpers={helpers} contextId={contextId} />);
    }
    // Emptiness
    if (filter?.operator && ['empty', 'not_empty'].includes(filter.operator)) {
      return null;
    }
    // Select field
    if (propertySchema.schema_property_values || propertySchema.schema_property_has_dynamic_value) {
      return (<BasicSelectInput propertySchema={propertySchema} filter={filter} helpers={helpers} contextId={contextId} />);
    }
    // Simple text field
    return (<BasicTextInput filter={filter} helpers={helpers} contextId={contextId} />);
  };
  return (choice());
};
